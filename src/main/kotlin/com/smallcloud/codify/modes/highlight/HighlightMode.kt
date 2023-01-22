package com.smallcloud.codify.modes.highlight

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.RequestJob
import com.smallcloud.codify.io.inferenceFetch
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.modes.ModeProvider
import com.smallcloud.codify.modes.ModeType
import com.smallcloud.codify.modes.completion.prompt.RequestCreator
import com.smallcloud.codify.modes.diff.DiffIntentProvider
import com.smallcloud.codify.modes.diff.dialog.DiffDialog
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequest
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class HighlightMode(
    override var needToRender: Boolean = true
) : Mode {
    private var layout: HighlightLayout? = null
    private val scope: String = "highlight"
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CodifyHighlightScheduler", 2)
    private val app = ApplicationManager.getApplication()
    private var processTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private val logger = Logger.getInstance(HighlightMode::class.java)
    private var needAnimation = false

    private fun isProgress(): Boolean {
        return needAnimation
    }

    private fun finishAnimation() {
        needAnimation = false
//        renderTask?.get()
    }

    private fun cancel(editor: Editor?) {
        try {
            processTask?.cancel(true)
            processTask?.get()
        } catch (_: CancellationException) {
        } finally {
            finishAnimation()
            cleanup()
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            if (editor != null) {
                ModeProvider.getOrCreateModeProvider(editor).switchMode()
            }
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        cancel(editor)
        ModeProvider.getOrCreateModeProvider(editor).switchMode()
    }

    override fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
        cancel(editor)
        ModeProvider.getOrCreateModeProvider(editor).switchMode()
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {}

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancel(editor)
        ModeProvider.getOrCreateModeProvider(editor).switchMode()
    }

    override fun onCaretChange(event: CaretEvent) {
        val offsets = event.caret?.let { layout?.getHighlightsOffsets(it.offset) }
        val intent = DiffIntentProvider.instance.lastHistoryIntent()
        if (offsets != null && intent != null) {
            cleanup()
            ModeProvider.getOrCreateModeProvider(event.editor)
                .getDiffMode().actionPerformed(
                    event.editor, HighlightContext(
                        intent, offsets[0], offsets[1]
                    )
                )
        }

    }

    fun isInRenderState(): Boolean {
        return (layout != null && !layout!!.rendered) ||
                (renderTask != null && !renderTask!!.isDone && !renderTask!!.isCancelled) || isProgress()
    }

    override fun isInActiveState(): Boolean {
        return isInRenderState() ||
                (processTask != null && !processTask!!.isDone && !processTask!!.isCancelled) ||
                layout != null
    }

    override fun show() {
        TODO("Not yet implemented")
    }

    override fun hide() {
        TODO("Not yet implemented")
    }

    override fun cleanup() {
        layout?.dispose()
        layout = null
    }

    fun actionPerformed(editor: Editor, fromDiff: Boolean = false) {
        if (layout != null) return

        val intent: String
        if (fromDiff && DiffIntentProvider.instance.lastHistoryIntent() != null) {
            intent = DiffIntentProvider.instance.lastHistoryIntent()!!
        } else {
            val dialog = DiffDialog(editor.project, true)
            if (!dialog.showAndGet()) return
            intent = dialog.messageText
            DiffIntentProvider.instance.pushFrontHistoryIntent(intent)
        }

        val fileName = getActiveFile(editor.document) ?: return
        val startSelectionOffset = editor.selectionModel.selectionStart
        val endSelectionOffset = editor.selectionModel.selectionEnd
        val request = RequestCreator.create(
            fileName, editor.document.text,
            startSelectionOffset, endSelectionOffset,
            scope, intent, "highlight", listOf()
        ) ?: return
        ModeProvider.getOrCreateModeProvider(editor).switchMode(ModeType.Highlight)

        needAnimation = true
        val startPosition = editor.offsetToLogicalPosition(startSelectionOffset)
        renderTask = scheduler.submit {
            waitingHighlight(editor, startPosition, this::isProgress)
        }
        processTask = scheduler.submit {
            process(request, editor)
        }
    }

    fun process(
        request: SMCRequest,
        editor: Editor
    ) {
        var maybeLastReqJob: RequestJob? = null
        try {
            request.body.stopTokens = listOf()
            request.body.maxTokens = 0

            if (InferenceGlobalContext.status == ConnectionStatus.CONNECTED) {
                InferenceGlobalContext.status = ConnectionStatus.PENDING
            }
            maybeLastReqJob = inferenceFetch(request)
            val lastReqJob = maybeLastReqJob ?: return

            if (InferenceGlobalContext.status == ConnectionStatus.CONNECTED) {
                InferenceGlobalContext.status = ConnectionStatus.PENDING
            }

            val prediction = lastReqJob.future.get() as SMCPrediction
            if (prediction.status == null) {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = "Parameters are not correct"
                return
            }

            val predictedText = prediction.choices.firstOrNull()?.files?.get(request.body.cursorFile)
            val finishReason = prediction.choices.firstOrNull()?.finishReason
            if (predictedText == null || finishReason == null) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = "Request was succeeded but there is no predicted data"
                return
            } else {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            }

            layout = HighlightLayout(editor, request, prediction)
            finishAnimation()
            app.invokeAndWait {
                layout!!.render()
            }

            if (layout!!.isEmpty()) {
                ModeProvider.getOrCreateModeProvider(editor).switchMode()
            }
        } catch (_: InterruptedException) {
            maybeLastReqJob?.let {
                maybeLastReqJob.request?.abort()
                logger.debug("lastReqJob abort")
            }
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        } catch (e: ExecutionException) {
            catchNetExceptions(e.cause)
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        } catch (e: Exception) {
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = e.message
            logger.warn("Exception while completion request processing", e)
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        }
    }

    private fun catchNetExceptions(e: Throwable?) {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        InferenceGlobalContext.lastErrorMsg = e?.message
        logger.warn("Exception while completion request processing", e)
    }

    private fun getActiveFile(document: Document): String? {
        if (!app.isDispatchThread) return null
        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }

}