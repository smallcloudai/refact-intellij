package com.smallcloud.codify.modes.completion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.io.*
import com.smallcloud.codify.modes.EditorTextHelper
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.modes.completion.prompt.FilesCollector
import com.smallcloud.codify.modes.completion.prompt.PromptCooker
import com.smallcloud.codify.modes.completion.prompt.PromptInfo
import com.smallcloud.codify.modes.completion.prompt.RequestCreator
import com.smallcloud.codify.struct.SMCRequest
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class StreamedCompletionMode(
    override var needToRender: Boolean = true
) : Mode, CaretListener {
    private val scope: String = "completion"
    private val app = ApplicationManager.getApplication()
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CompletionScheduler", 1)
    private var processTask: Future<*>? = null
    private var completionLayout: CompletionLayout? = null
    private val logger = Logger.getInstance("StreamedCompletionMode")

    private var autocompleteSymbolBefore: String = ""
    private var hasOneLineCompletionBefore: Boolean = false

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        logger.info("beforeDocumentChangeNonBulk cancelOrClose request")
        cancelOrClose(editor)
    }

    private fun willHaveMoreEvents(event: DocumentEvent, editor: Editor): Boolean {
        if (event.offset == 0 || event.offset >= editor.document.text.length) return false
        val ch = editor.document.text[event.offset]
        val beforeCh = editor.document.text[event.offset - 1]
        if (beforeCh == ':' && ch == '\n') return true
        return false
    }

    private fun autocompletedSymbolsInfo(event: DocumentEvent, editor: Editor): Pair<Boolean, Int> {
        val startAutocompleteStrings = setOf("(", "\"", "{", "[", "'", "\"")
        val endAutocompleteStrings = setOf(")", "\"", "\'", "}", "]", "'''", "\"\"\"")
        val startToStopSymbols = mapOf(
            "(" to setOf(")"), "{" to setOf("}"), "[" to setOf("]"),
            "'" to setOf("'", "'''"), "\"" to setOf("\"", "\"\"\"")
        )
        val newStr = event.newFragment.toString()
        if (startToStopSymbols[autocompleteSymbolBefore]?.contains(newStr) == true && newStr in endAutocompleteStrings) {
            logger.info("Fixing completion request for the autocomplete symbol: $newStr")
            autocompleteSymbolBefore = ""
            return true to -1
        }
        if (newStr.isNotEmpty() && "${newStr.last()}" in startAutocompleteStrings) {
            autocompleteSymbolBefore = "${newStr.last()}"
            logger.info("Skipped completion request: started autocomplete symbol: $autocompleteSymbolBefore")
            return false to 0
        }

        autocompleteSymbolBefore = ""
        return true to 0
    }

    override fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
        val fileName = getActiveFile(editor.document) ?: return
        val state: EditorState
        val debounceMs: Long
        if (!force) {
            if (event == null) return
            if (event.offset + event.newLength > editor.document.text.length) return
            if (event.newLength + event.oldLength <= 0) return
            if (willHaveMoreEvents(event, editor)) return
            val (valid, offset) = autocompletedSymbolsInfo(event, editor)
            if (!valid) return
            state = EditorState(
                editor.document.modificationStamp,
                event.offset + event.newLength + offset,
                editor.document.text
            )

            val completionData = CompletionCache.getCompletion(state.text, state.offset)
            if (completionData != null) {
                hasOneLineCompletionBefore = completionData.isSingleLineComplete
                processTask = scheduler.submit {
                    app.invokeLater { renderCompletion(editor, state, completionData) }
                }
                return
            }

            if (shouldIgnoreChange(event, editor, state.offset)) {
                return
            }

            debounceMs = CompletionTracker.calcDebounceTime(editor)
            CompletionTracker.updateLastCompletionRequestTime(editor)
            logger.debug("Debounce time: $debounceMs")
        } else {
            state = EditorState(
                editor.document.modificationStamp,
                editor.caretModel.offset,
                editor.document.text
            )
            debounceMs = 0
        }

        val editorHelper = EditorTextHelper(editor, state.offset)
        if (!editorHelper.isValid()) return

        var promptInfo: List<PromptInfo> = listOf()
        if (InferenceGlobalContext.useMultipleFilesCompletion) {
            editor.project?.let {
                promptInfo = PromptCooker.cook(
                    editorHelper,
                    FileDocumentManager.getInstance().getFile(editor.document)?.extension,
                    FilesCollector.getInstance(it).collect(),
                    mostImportantFilesMaxCount = if (force) 25 else 10,
                    lessImportantFilesMaxCount = if (force) 10 else 5,
                    maxFileSize = if (force) 2_000_000 else 200_000
                )
            }
        }
        val request = RequestCreator.create(
            fileName, state.text, state.offset, state.offset,
            scope, "Infill", "infill", promptInfo,
            stream = true
        ) ?: return

        processTask = scheduler.schedule({
            process(request, editor, state, editorHelper, force)
        }, debounceMs, TimeUnit.MILLISECONDS)
    }

    private fun renderCompletion(
        editor: Editor,
        state: EditorState,
        completionData: Completion,
    ) {
        val invalidStamp = state.modificationStamp != editor.document.modificationStamp
        val invalidOffset = state.offset != editor.caretModel.offset
        if (invalidStamp || invalidOffset) {
            logger.info("Completion is dropped: invalidStamp || invalidOffset")
            logger.info(
                "state_offset: ${state.offset}," +
                        " state_modificationStamp: ${state.modificationStamp}"
            )
            logger.info(
                "editor_offset: ${editor.caretModel.offset}," +
                        " editor_modificationStamp: ${editor.document.modificationStamp}"
            )
            return
        }
        if (processTask == null) {
            logger.info("Completion is dropped: there is no active processTask is left")
            return
        }
        logger.info(
            "Completion rendering: offset: ${state.offset}," +
                    " modificationStamp: ${state.modificationStamp}"
        )
        logger.info("Completion data: ${completionData.completion}")
        try {
            val completion = CompletionLayout(editor, completionData)
            if (needToRender) completion.render()
            completionLayout = completion
            editor.caretModel.addCaretListener(this)
        } catch (ex: Exception) {
            logger.warn("Exception while rendering completion", ex)
            logger.debug("Exception while rendering completion cancelOrClose request")
            cancelOrClose(editor)
        }
    }

    override fun hide() {
        logger.debug("hide")
        if (!isInActiveState()) return
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                app.invokeAndWait { completionLayout?.hide() }
            }
        }
    }

    override fun show() {
        logger.debug("show")
        if (isInActiveState()) return
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                app.invokeAndWait { completionLayout?.show() }
            }
        }
    }

    private fun process(
        request: SMCRequest,
        editor: Editor,
        state: EditorState,
        editorHelper: EditorTextHelper,
        force: Boolean,
    ) {
        val completionState = CompletionState(editorHelper, force = force)
        if (!force && !completionState.readyForCompletion) return
        if (!force && !completionState.multiline && hasOneLineCompletionBefore) return
        if (force) {
            request.body.maxTokens = 512
        }
        request.body.stopTokens = completionState.stopTokens
        InferenceGlobalContext.status = ConnectionStatus.PENDING
        streamedInferenceFetch(request, onDataReceiveEnded = {
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = null
        }) { prediction ->
            if (prediction.status == null) {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = "Parameters are not correct"
                return@streamedInferenceFetch
            }

            val headMidTail =
                prediction.choices.firstOrNull()?.filesHeadMidTail?.get(request.body.cursorFile)
                    ?: return@streamedInferenceFetch

            val head = minOf(editorHelper.document.text.length, headMidTail.head)
            val firstEosIndex = editorHelper.document.text.substring(head).indexOfFirst { it == '\n' }
            var tail = head + (if (firstEosIndex == -1) 0 else firstEosIndex)
            tail = minOf(editorHelper.document.text.length, tail)
            val completionData = Completion(
                originalText = editorHelper.document.text,
                predictedText = editorHelper.document.text.substring(0, head) +
                        headMidTail.mid +
                        editorHelper.document.text.substring(head),
                completion = headMidTail.mid,
                currentLinesAreEqual = false,
                multiline = completionState.multiline,
                startIndex = head,
                endIndex = tail,
                createdTs = System.currentTimeMillis(),
                isSingleLineComplete = !completionState.multiline
            )
            if (!completionData.isMakeSense()) return@streamedInferenceFetch
            synchronized(this) {
                CompletionCache.addCompletion(completionData)
            }
            app.invokeAndWait {
                completionLayout?.dispose()
                completionLayout = null
                editor.caretModel.removeCaretListener(this)
                renderCompletion(editor, state, completionData)
            }
        }?.also {
            try {
                it.future.get()
            } catch (_: InterruptedException) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                it.request?.abort()
                logger.debug("lastReqJob abort")
            } catch (e: ExecutionException) {
                catchNetExceptions(e.cause)
            } catch (e: Exception) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = e.message
                logger.warn("Exception while completion request processing", e)
            }
        }
    }

    private fun catchNetExceptions(e: Throwable?) {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        InferenceGlobalContext.lastErrorMsg = e?.message
        logger.warn("Exception while completion request processing", e)
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        completionLayout?.apply {
            applyPreview(caret ?: editor.caretModel.currentCaret)
            dispose()
            hasOneLineCompletionBefore = completionData.isSingleLineComplete
        }
        completionLayout = null
        editor.caretModel.removeCaretListener(this)
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        logger.debug("onEscPressed cancelOrClose request")
        cancelOrClose(editor)
    }

    override fun onCaretChange(event: CaretEvent) {}

    override fun caretPositionChanged(event: CaretEvent) {
        logger.debug("caretPositionChanged cancelOrClose request")
        cancelOrClose(event.editor)
    }

    override fun isInActiveState(): Boolean = completionLayout != null && completionLayout!!.rendered && needToRender
    override fun cleanup() {
        cancelOrClose(null)
    }

    private fun shouldIgnoreChange(event: DocumentEvent?, editor: Editor, offset: Int): Boolean {
        if (event == null) return false
        val document = event.document

        if (editor.editorKind != EditorKind.MAIN_EDITOR && !app.isUnitTestMode) {
            return true
        }
        if (!EditorModificationUtil.checkModificationAllowed(editor)
            || document.getRangeGuard(offset, offset) != null
        ) {
            document.fireReadOnlyModificationAttempt()
            return true
        }
        return false
    }

    private fun getActiveFile(document: Document): String? {
        if (!app.isDispatchThread) return null
        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }

    private fun cancelOrClose(editor: Editor?) {
        try {
            processTask?.cancel(true)
            processTask?.get()
        } catch (_: CancellationException) {
        } finally {
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            processTask = null
            hasOneLineCompletionBefore = completionLayout?.completionData?.isFromCache ?: false
            completionLayout?.dispose()
            completionLayout = null
            editor?.caretModel?.removeCaretListener(this)
        }
    }
}