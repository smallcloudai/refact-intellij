package com.smallcloud.codify.modes.diff

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.Messages
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.RequestJob
import com.smallcloud.codify.io.inferenceFetch
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.codify.modes.ModeType
import com.smallcloud.codify.modes.completion.prompt.RequestCreator
import com.smallcloud.codify.modes.diff.dialog.DiffDialog
import com.smallcloud.codify.modes.highlight.HighlightContext
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequest
import dev.gitlive.difflib.DiffUtils
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future


class DiffMode : Mode {
    private val scope: String = "query_diff"
    private val logger = Logger.getInstance(DiffMode::class.java)
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CodifyDiffScheduler", 2)
    private val app = ApplicationManager.getApplication()
    private var diffLayout: DiffLayout? = null
    private var processTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private var needRainbowAnimation: Boolean = false
    private var lastFromHL: Boolean = false

    private fun isProgress() : Boolean {
        return needRainbowAnimation
    }

    private fun finishRenderRainbow() {
        needRainbowAnimation = false
//        if (!renderTask?.isDone!! || !renderTask?.isCancelled!!)
//            renderTask?.get()
    }

    private fun cancel(editor: Editor?) {
        try {
            processTask?.cancel(true)
            processTask?.get()
        } catch (_: CancellationException) {
        } finally {
            finishRenderRainbow()
            diffLayout?.cancelPreview()
            diffLayout = null
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            if (editor != null) {
                getOrCreateModeProvider(editor).switchMode()
            }
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        cancel(editor)
    }

    override fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        diffLayout?.applyPreview()
        diffLayout = null
        if (lastFromHL) {
            getOrCreateModeProvider(editor).getHighlightMode().actionPerformed(editor, true)
        } else {
            getOrCreateModeProvider(editor).switchMode()
        }
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancel(editor)
        lastFromHL = false
    }

    override fun onCaretChange(event: CaretEvent) {}

    fun isInRenderState() : Boolean {
        return (diffLayout != null && !diffLayout!!.rendered) ||
                (renderTask != null && !renderTask!!.isDone && !renderTask!!.isCancelled) || isProgress()
    }

    override fun isInActiveState(): Boolean {
        return isInRenderState() ||
                (processTask != null && !processTask!!.isDone && !processTask!!.isCancelled) ||
                diffLayout != null
    }

    override fun cleanup() {
        cancel(null)
    }

    fun actionPerformed(editor: Editor, highlightContext: HighlightContext? = null) {
        val fileName = getActiveFile(editor.document) ?: return
        val selectionModel = editor.selectionModel
        var startSelectionOffset: Int = selectionModel.selectionStart
        var endSelectionOffset: Int = selectionModel.selectionEnd
        val startPosition: LogicalPosition
        val finishPosition: LogicalPosition
        val request: SMCRequest
        if (diffLayout == null || highlightContext != null) {
            var intend = ""
            if (highlightContext == null) {
                val dialog = DiffDialog(editor.project)
                if (!dialog.showAndGet()) return
                intend = dialog.messageText
                DiffIntendProvider.instance.pushFrontHistoryIntend(intend)
                lastFromHL = false
            } else {
                intend = highlightContext.intend
                startSelectionOffset = highlightContext.startOffset
                endSelectionOffset = highlightContext.endOffset
                lastFromHL = true
            }

            request = RequestCreator.create(
                fileName, editor.document.text,
                startSelectionOffset, endSelectionOffset,
                scope, intend,
                if (highlightContext == null) "diff-selection" else "diff-atcursor",
                listOf()
            ) ?: return
            startPosition = editor.offsetToLogicalPosition(startSelectionOffset)
            finishPosition = editor.offsetToLogicalPosition(endSelectionOffset)
            selectionModel.removeSelection()
            getOrCreateModeProvider(editor).switchMode(ModeType.Diff)
        } else {
            request = diffLayout!!.request
            startPosition = editor.offsetToLogicalPosition(request.body.cursor0)
            finishPosition = editor.offsetToLogicalPosition(request.body.cursor1)
            diffLayout?.cancelPreview()
            diffLayout = null
        }

        needRainbowAnimation = true
        renderTask = scheduler.submit {
            waitingDiff(editor,
                startPosition, finishPosition,
                this::isProgress)
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
            request.body.maxTokens = 550
            request.body.maxEdits = if (request.body.functionName == "diff-atcursor") 1 else 10

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

            val predictedText = prediction.choices?.firstOrNull()?.files?.get(request.body.cursorFile)
            val finishReason = prediction.choices?.firstOrNull()?.finishReason
            if (predictedText == null || finishReason == null) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = "Request was succeeded but there is no predicted data"
                return
            } else {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
                InferenceGlobalContext.lastErrorMsg = null
            }

            val patch = request.body.sources[request.body.cursorFile]?.let {
                prediction.choices?.get(0)?.files?.get(request.body.cursorFile)?.let { it1 ->
                    DiffUtils.diff(
                        it.split('\n'),
                        it1.split('\n'),
                    )
                }
            }
            finishRenderRainbow()
            if (patch == null || patch.getDeltas().isEmpty()) return

            diffLayout = DiffLayout(editor, request, patch)
            app.invokeAndWait {
                diffLayout!!.render()
            }
        } catch (_: InterruptedException) {
            finishRenderRainbow()
            maybeLastReqJob?.let {
                maybeLastReqJob.request?.abort()
                logger.debug("lastReqJob abort")
            }
            getOrCreateModeProvider(editor).switchMode()
        } catch (e: ExecutionException) {
            finishRenderRainbow()
            catchNetExceptions(e.cause)
            getOrCreateModeProvider(editor).switchMode()
        } catch (e: Exception) {
            finishRenderRainbow()
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = e.message
            logger.warn("Exception while completion request processing", e)
            getOrCreateModeProvider(editor).switchMode()
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