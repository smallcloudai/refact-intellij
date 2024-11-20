@file:Suppress("UnstableApiUsage")

package com.smallcloud.refactai.codecompletion

import com.intellij.codeInsight.inline.completion.*
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElementManipulator
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionGrayTextElement
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionSkipTextElement
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSingleSuggestion
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult.Changed
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionSuggestionUpdateManager.UpdateResult.Invalidated
import com.intellij.codeInsight.inline.completion.suggestion.InlineCompletionVariant
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.application
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.streamedInferenceFetch
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance
import com.smallcloud.refactai.modes.EditorTextState
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.modes.completion.CompletionTracker
import com.smallcloud.refactai.modes.completion.prompt.RequestCreator
import com.smallcloud.refactai.modes.completion.structs.Completion
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.utils.getExtension
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.DeltaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlin.io.path.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

private class Default : InlineCompletionSuggestionUpdateManager.Adapter {
    override fun onDocumentChange(
        event: InlineCompletionEvent.DocumentChange,
        variant: InlineCompletionVariant.Snapshot
    ): InlineCompletionSuggestionUpdateManager.UpdateResult {
        if (!isValidTyping(event.typing, variant)) {
            return Invalidated
        }
        val truncated = truncateFirstSymbol(variant.elements) ?: return Invalidated
        return Changed(variant.copy(elements = truncated))
    }

    private fun isValidTyping(typing: TypingEvent, variant: InlineCompletionVariant.Snapshot): Boolean {
        if (typing !is TypingEvent.OneSymbol) {
            return false
        }
        val fragment = typing.typed
        val textToInsert = variant.elements.joinToString("") { it.text }
        return textToInsert.startsWith(fragment)
    }

    private fun truncateFirstSymbol(elements: List<InlineCompletionElement>): List<InlineCompletionElement>? {
        val newFirstElementIndex = elements.indexOfFirst { it.text.isNotEmpty() }
        check(newFirstElementIndex >= 0)
        val firstElement = elements[newFirstElementIndex]
        val manipulator = InlineCompletionElementManipulator.getApplicable(firstElement) ?: return null
        val newFirstElement = manipulator.truncateFirstSymbol(firstElement)
        return listOfNotNull(newFirstElement) + elements.drop(newFirstElementIndex + 1)
    }

    override fun onLookupEvent(
        event: InlineCompletionEvent.InlineLookupEvent,
        variant: InlineCompletionVariant.Snapshot
    ): InlineCompletionSuggestionUpdateManager.UpdateResult {
        return super.onLookupEvent(event, variant)
    }

    override fun onDirectCall(
        event: InlineCompletionEvent.DirectCall,
        variant: InlineCompletionVariant.Snapshot
    ): InlineCompletionSuggestionUpdateManager.UpdateResult = Invalidated
}

private class InsertHandler : DefaultInlineCompletionInsertHandler() {
    override fun afterInsertion(
        environment: InlineCompletionInsertEnvironment,
        elements: List<InlineCompletionElement>
    ) {
        super.afterInsertion(environment, elements)
        application.invokeLater { // Very important to make it in another EDT call
            InlineCompletion.getHandlerOrNull(environment.editor)?.invokeEvent(
                RefactAIContinuousEvent(
                    environment.editor, environment.editor.caretModel.offset
                )
            )
        }
    }
}

private val specialSymbolsRegex = "^[:\\s\\t\\n\\r(){},.\"'\\];]*\$".toRegex()

class RefactAICompletionProvider : DebouncedInlineCompletionProvider() {
    private val logger = Logger.getInstance("inlineCompletion")

    override val id: InlineCompletionProviderID = InlineCompletionProviderID("Refact.ai")
    override val suggestionUpdateManager: InlineCompletionSuggestionUpdateManager = Default()
    override val insertHandler: InlineCompletionInsertHandler = InsertHandler()
    private var lastRequestId: String = ""
    private var temperatureCounter = 0

    override suspend fun getDebounceDelay(request: InlineCompletionRequest): Duration {
        val force = request.event is InlineCompletionEvent.DirectCall
        if (!force) {
            val debounceMs = CompletionTracker.calcDebounceTime(request.editor)
            CompletionTracker.updateLastCompletionRequestTime(request.editor)
            return debounceMs.milliseconds
        } else {
            return 0.milliseconds
        }
    }

    override fun restartOn(event: InlineCompletionEvent): Boolean = false

    private fun getActiveFile(document: Document, project: Project?): String? {
        val projectPath = project?.basePath ?: return null
        val file = FileDocumentManager.getInstance().getFile(document) ?: return null
        return Path(file.path).toUri().toString().replace(Path(projectPath).toUri().toString(), "")
    }

    private class Context(val request: SMCRequest, val editorState: EditorTextState, val force: Boolean = false)


    private fun makeContext(request: InlineCompletionRequest): Context? {
        val fileName = getActiveFile(request.document, request.editor.project) ?: return null
        if (PrivacyService.instance.getPrivacy(FileDocumentManager.getInstance().getFile(request.document))
            == Privacy.DISABLED && !InferenceGlobalContext.isSelfHosted
        ) return null
        if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return null
        val editor = request.editor
        val logicalPos = editor.caretModel.logicalPosition
        var text = editor.document.text
        var offset = -1
        ApplicationManager.getApplication().runReadAction {
            offset = editor.caretModel.offset
        }

        val currentLine = text.substring(
            editor.document.getLineStartOffset(logicalPos.line),
            editor.document.getLineEndOffset(logicalPos.line)
        )
        val rightOfCursor = text.substring(
            offset,
            editor.document.getLineEndOffset(logicalPos.line)
        )

        if (!rightOfCursor.matches(specialSymbolsRegex)) return null

        val isMultiline = currentLine.all { it == ' ' || it == '\t' }
        var pos = 0
        if (isMultiline) {
            val startOffset = editor.document.getLineStartOffset(logicalPos.line)
            val endOffset = editor.document.getLineEndOffset(logicalPos.line)
            text = text.removeRange(startOffset, endOffset)
        } else {
            pos = offset - editor.document.getLineStartOffset(logicalPos.line)
        }
        val force = request.event is InlineCompletionEvent.DirectCall
        val state = runReadAction {
                EditorTextState(
                editor,
                editor.document.modificationStamp,
                editor.caretModel.offset
            )
        }

        if (!state.isValid()) return null
        val stat = UsageStatistic(scope = "completion", extension = getExtension(fileName))
        val baseUrl = getInstance(editor.project!!)?.url!!
        val request = RequestCreator.create(
            fileName, text, logicalPos.line, pos,
            stat,
            baseUrl = baseUrl,
            stream = false, model = InferenceGlobalContext.model,
            multiline = isMultiline, useAst = InferenceGlobalContext.astIsEnabled,
        ) ?: return null
        return Context(request, state, force = force)
    }

    override suspend fun getSuggestionDebounced(request: InlineCompletionRequest): InlineCompletionSingleSuggestion =
        InlineCompletionSingleSuggestion.build(request, channelFlow {
            val context = makeContext(request) ?: return@channelFlow
            InferenceGlobalContext.status = ConnectionStatus.PENDING
            if (context.force) {
                context.request.body.parameters.maxNewTokens = 50
                context.request.body.noCache = true
                temperatureCounter++
            } else {
                temperatureCounter = 0
            }
            if (temperatureCounter > 1) {
                context.request.body.parameters.temperature = 0.6F
            }
            lastRequestId = context.request.id

            streamedInferenceFetch(context.request, dataReceiveEnded = {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            }) { prediction ->
                val choice = prediction.choices.first()
                if (lastRequestId != prediction.requestId) {
                    return@streamedInferenceFetch
                }
                 val completion = Completion(
                    context.request.body.inputs.sources.values.toList().first(),
                    offset = context.editorState.offset,
                    multiline = context.request.body.inputs.multiline,
                    createdTs = prediction.created,
                    isFromCache = prediction.cached,
                    snippetTelemetryId = prediction.snippetTelemetryId
                )
                completion.updateCompletion(choice.delta)
                CoroutineScope(Dispatchers.Default).launch {
                    val elems = if (completion.multiline) {
                        getMultilineElements(completion, context.editorState)
                    } else {
                        getSingleLineElements(completion, context.editorState)
                    }

                    elems.forEach {
                        send(it)
                        delay(2)
                    }
                }
            }
            awaitClose()

        })

    private fun getSingleLineElements(
        completionData: Completion,
        editorState: EditorTextState
    ): List<InlineCompletionElement> {
        val res = mutableListOf<InlineCompletionElement>()

        val currentLine = completionData.originalText.substring(completionData.offset)
            .substringBefore('\n', "")
        val patch = DiffUtils.diff(currentLine.toList(), completionData.completion.toList())
        var previousPosition = 0
        for (delta in patch.getDeltas()) {
            if (delta.type != DeltaType.INSERT) {
                continue
            }
            val blockText = delta.target.lines?.joinToString("") ?: ""

            val previousSymbols = currentLine.substring(previousPosition, delta.source.position)
            if (previousSymbols.isNotEmpty()) {
                res.add(InlineCompletionSkipTextElement(previousSymbols))
            }

            res.add(InlineCompletionGrayTextElement(blockText))
            previousPosition = delta.source.position
        }
        val previousSymbols = currentLine.substring(previousPosition)
        if (previousSymbols.isNotEmpty()) {
            res.add(InlineCompletionSkipTextElement(previousSymbols))
        }
        return res
    }

    private fun getMultilineElements(
        completionData: Completion,
        editorState: EditorTextState
    ): List<InlineCompletionElement> {
        return listOf(InlineCompletionGrayTextElementCustom(completionData.completion, (editorState.offsetByCurrentLine)))
    }

    override fun isEnabled(event: InlineCompletionEvent): Boolean {
        val isRelevantEvent = event is InlineCompletionEvent.DirectCall || event is InlineCompletionEvent.DocumentChange
        var isInCompletionMode = true

        if (isRelevantEvent) {
            val editor = when (event) {
                is InlineCompletionEvent.DirectCall -> event.editor
                is InlineCompletionEvent.DocumentChange -> event.editor
                else -> null
            }

            editor?.let {
                val provider = ModeProvider.getOrCreateModeProvider(it)
                isInCompletionMode = provider.isInCompletionMode()
            }
        }

        return isInCompletionMode && (InferenceGlobalContext.useAutoCompletion || event is InlineCompletionEvent.DirectCall)
    }
}