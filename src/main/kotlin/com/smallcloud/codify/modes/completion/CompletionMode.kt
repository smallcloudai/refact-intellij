package com.smallcloud.codify.modes.completion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.alsoIfNull
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Resources
import com.smallcloud.codify.io.*
import com.smallcloud.codify.modes.EditorTextState
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.modes.completion.renderer.AsyncCompletionLayout
import com.smallcloud.codify.modes.completion.prompt.FilesCollector
import com.smallcloud.codify.modes.completion.prompt.PromptCooker
import com.smallcloud.codify.modes.completion.prompt.PromptInfo
import com.smallcloud.codify.modes.completion.prompt.RequestCreator
import com.smallcloud.codify.modes.completion.structs.Completion
import com.smallcloud.codify.modes.completion.structs.DocumentEventExtra
import com.smallcloud.codify.privacy.Privacy
import com.smallcloud.codify.privacy.PrivacyService
import com.smallcloud.codify.statistic.CompletionStatistic
import com.smallcloud.codify.statistic.StatisticService
import com.smallcloud.codify.struct.SMCRequest
import java.io.InterruptedIOException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class CompletionMode(
    override var needToRender: Boolean = true
) : Mode, CaretListener {
    private val scope: String = "completion"
    private val app = ApplicationManager.getApplication()
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CodifyCompletionScheduler", 1)
    private var processTask: Future<*>? = null
    private var completionLayout: AsyncCompletionLayout? = null
    private val logger = Logger.getInstance("StreamedCompletionMode")
    private var lastStatistic: CompletionStatistic? = null
    private var hasOneLineCompletionBefore: Boolean = false
    private var completionInProgress: Boolean = false


    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {
        event.editor.caretModel.removeCaretListener(this)
        event.editor.caretModel.addCaretListener(this)
        cancelOrClose()
    }

    override fun onTextChange(event: DocumentEventExtra) {
        lastStatistic?.let {
            if (completionLayout != null) {
                it.addStatistic("cancel", "document_changed")
                StatisticService.instance.addCompletionStatistic(it)
                lastStatistic = null
            }
        }
        val fileName = getActiveFile(event.editor.document) ?: return
        if (PrivacyService.instance.getPrivacy(FileDocumentManager.getInstance().getFile(event.editor.document))
            == Privacy.DISABLED) return
        if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return
        var maybeState: EditorTextState? = null
        val debounceMs: Long
        val editor = event.editor
        lastStatistic = CompletionStatistic()
        if (!event.force) {
            val docEvent = event.event ?: return
            if (docEvent.offset + docEvent.newLength > editor.document.text.length) return
            if (docEvent.newLength + docEvent.oldLength <= 0) return
            maybeState = EditorTextState(
                editor,
                editor.document.modificationStamp,
                docEvent.offset + docEvent.newLength + event.offsetCorrection
            )
            val completionData = CompletionCache.getCompletion(maybeState.text, maybeState.offset)
            if (completionData != null) {
                processTask = scheduler.submit {
                    renderCompletion(editor, maybeState!!, completionData, animation = false)
                    lastStatistic?.addStatistic("cacheRendered")
                }
                return
            }

            if (shouldIgnoreChange(docEvent, editor, maybeState.offset)) {
                return
            }

            debounceMs = CompletionTracker.calcDebounceTime(editor)
            CompletionTracker.updateLastCompletionRequestTime(editor)
            logger.debug("Debounce time: $debounceMs")
        } else {
            app.invokeAndWait {
                maybeState = EditorTextState(
                    editor,
                    editor.document.modificationStamp,
                    editor.caretModel.offset
                )
            }
            debounceMs = 0
        }

        val state = maybeState ?: return
        if (!state.isValid()) return
        state.getRidOfLeftSpacesInplace()

        var promptInfo: List<PromptInfo> = listOf()
        if (InferenceGlobalContext.useMultipleFilesCompletion) {
            editor.project?.let {
                app.invokeAndWait {
                    promptInfo = PromptCooker.cook(
                        state,
                        FileDocumentManager.getInstance().getFile(editor.document)?.extension,
                        FilesCollector.getInstance(it).collect(),
                        mostImportantFilesMaxCount = if (event.force) 25 else 6,
                        lessImportantFilesMaxCount = if (event.force) 10 else 2,
                        maxFileSize = if (event.force) 2_000_000 else 200_000
                    )
                }
            }
        }

        val request = RequestCreator.create(
            fileName, state.text, state.offset, state.offset,
            scope, "Infill", "infill", promptInfo,
            stream = true, model = InferenceGlobalContext.model ?: Resources.defaultModel
        ) ?: return

        processTask = scheduler.schedule({
            process(request, state, event.force)
        }, debounceMs, TimeUnit.MILLISECONDS)
    }

    private fun renderCompletion(
        editor: Editor,
        state: EditorTextState,
        completionData: Completion,
        animation: Boolean
    ) {
        var modificationStamp: Long = state.modificationStamp
        var offset: Int = state.offset
        app.invokeAndWait {
            modificationStamp = editor.document.modificationStamp
            offset = editor.caretModel.offset
        }
        val invalidStamp = state.modificationStamp != modificationStamp
        val invalidOffset = state.offset != offset
        if (invalidStamp || invalidOffset) {
            logger.info("Completion is dropped: invalidStamp || invalidOffset")
            logger.info(
                "state_offset: ${state.offset}," +
                        " state_modificationStamp: ${state.modificationStamp}"
            )
            logger.info(
                "editor_offset: $offset, editor_modificationStamp: $modificationStamp"
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
            completionLayout?.also {
                it.update(completionData, needToRender, animation)
            }.alsoIfNull {
                completionLayout = AsyncCompletionLayout(editor).also {
                    it.update(completionData, needToRender, animation)
                }
            }
        } catch (ex: Exception) {
            logger.warn("Exception while rendering completion", ex)
            logger.debug("Exception while rendering completion cancelOrClose request")
            cancelOrClose()
            lastStatistic?.let {
                lastStatistic = null
            }
        }
    }

    override fun hide() {
        if (!isInActiveState()) return
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                completionLayout?.hide()
            }
        }
    }

    override fun show() {
        if (isInActiveState()) return
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                completionLayout?.show()
            }
        }
    }

    private fun process(
        request: SMCRequest,
        editorState: EditorTextState,
        force: Boolean,
    ) {
        val completionState = CompletionState(editorState, force = force)
        if (!force && !completionState.readyForCompletion) return
        if (!force && !completionState.multiline && hasOneLineCompletionBefore) {
            hasOneLineCompletionBefore = false
            return
        }
        if (force) {
            request.body.maxTokens = 512
        }
        request.body.stopTokens = completionState.stopTokens
        InferenceGlobalContext.status = ConnectionStatus.PENDING
        completionInProgress = true
        lastStatistic = CompletionStatistic()
        streamedInferenceFetch(request, dataReceiveEnded = {
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = null
            lastStatistic?.addStatistic("requestRendered")
        }) { prediction ->
            if (!completionInProgress) {
                return@streamedInferenceFetch
            }

            if (prediction.status == null) {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = "Parameters are not correct"
                return@streamedInferenceFetch
            }

            val headMidTail =
                prediction.choices.firstOrNull()?.filesHeadMidTail?.get(request.body.cursorFile)
                    ?: return@streamedInferenceFetch

            editorState.restoreInplace()
            val completionData = completionState.makeCompletion(
                headMidTail.head,
                headMidTail.mid,
                headMidTail.tail,
                prediction.choices.first().finishReason
            )
            if (!completionData.isMakeSense()) return@streamedInferenceFetch
            if (!editorState.currentLineIsEmptySymbols() && headMidTail.head != editorState.offset) return@streamedInferenceFetch
            synchronized(this) {
                CompletionCache.addCompletion(completionData)
                renderCompletion(
                    editorState.editor, editorState, completionData, true
                )
            }
        }?.also {
            var requestFuture: Future<*>? = null
            try {
                requestFuture = it.get()
                requestFuture.get()
                logger.debug("Completion request finished")
            } catch (e: InterruptedException) {
                handleInterruptedException(requestFuture)
            } catch (e: InterruptedIOException) {
                handleInterruptedException(requestFuture)
            } catch (e: ExecutionException) {
                cancelOrClose()
                requestFuture?.cancel(true)
                catchNetExceptions(e.cause)
                lastStatistic?.let {
                    lastStatistic = null
                }
            } catch (e: Exception) {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = e.message
                cancelOrClose()
                lastStatistic?.let {
                    lastStatistic = null
                }
                logger.warn("Exception while completion request processing", e)
            }
        }
    }

    private fun handleInterruptedException(requestFuture: Future<*>?) {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        requestFuture?.cancel(true)
        lastStatistic?.let {
            lastStatistic?.addStatistic("cancel", "request_abort")
            StatisticService.instance.addCompletionStatistic(lastStatistic!!)
            lastStatistic = null
        }
        cancelOrClose()
        logger.debug("lastReqJob abort")
    }

    private fun catchNetExceptions(e: Throwable?) {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        InferenceGlobalContext.lastErrorMsg = e?.message
        logger.warn("Exception while completion request processing", e)
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        lastStatistic?.let {
            if (completionLayout != null) {
                it.addStatistic("accept", "tab")
                StatisticService.instance.addCompletionStatistic(it)
                lastStatistic = null
            }
        }

        completionLayout?.apply {
            applyPreview(caret ?: editor.caretModel.currentCaret)
            lastCompletionData?.let {
                val nextLine = it.endIndex >= it.originalText.length || it.originalText[it.endIndex] == '\n'
                hasOneLineCompletionBefore = it.isSingleLineComplete && nextLine
            }
            dispose()
        }
        completionLayout = null
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        lastStatistic?.let {
            if (completionLayout != null) {
                it.addStatistic("cancel", "esc")
                StatisticService.instance.addCompletionStatistic(it)
                lastStatistic = null
            }
        }
        cancelOrClose()
    }

    override fun onCaretChange(event: CaretEvent) {
    }

    override fun caretPositionChanged(event: CaretEvent) {
        lastStatistic?.let {
            fun isWriting(): Boolean {
                return event.newPosition.line == event.oldPosition.line &&
                        event.newPosition.column == (event.oldPosition.column + 1)
            }
            if (completionLayout != null && !isWriting()) {
                it.addStatistic("cancel", "caret_changed")
                StatisticService.instance.addCompletionStatistic(it)
                lastStatistic = null
            }
        }
        cancelOrClose()
    }

    override fun isInActiveState(): Boolean = completionLayout != null && completionLayout!!.rendered && needToRender

    override fun cleanup() {
        lastStatistic?.let {
            if (completionLayout != null) {
                it.addStatistic("cancel", "mode_changed")
                StatisticService.instance.addCompletionStatistic(it)
                lastStatistic = null
            }
        }
        cancelOrClose()
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
        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }

    private fun cancelOrClose() {
        lastStatistic = null
        try {
            processTask?.cancel(true)
            processTask?.get(1, TimeUnit.SECONDS)
        } catch (_: CancellationException) {
        } finally {
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            completionInProgress = false
            processTask = null
            completionLayout?.dispose()
            completionLayout = null
        }
    }
}
