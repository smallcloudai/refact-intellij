package com.smallcloud.refact.modes.highlight

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refact.Resources
import com.smallcloud.refact.io.ConnectionStatus
import com.smallcloud.refact.io.InferenceGlobalContext
import com.smallcloud.refact.io.RequestJob
import com.smallcloud.refact.io.inferenceFetch
import com.smallcloud.refact.modes.Mode
import com.smallcloud.refact.modes.ModeProvider
import com.smallcloud.refact.modes.ModeType
import com.smallcloud.refact.modes.completion.prompt.RequestCreator
import com.smallcloud.refact.modes.completion.structs.DocumentEventExtra
import com.smallcloud.refact.modes.diff.DiffIntentProvider
import com.smallcloud.refact.modes.diff.dialog.DiffDialog
import com.smallcloud.refact.struct.LongthinkFunctionEntry
import com.smallcloud.refact.struct.SMCPrediction
import com.smallcloud.refact.struct.SMCRequest
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class HighlightMode(
    override var needToRender: Boolean = true
) : Mode {
    private var layout: HighlightLayout? = null
    private val scope: String = "highlight"
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("CodifyHighlightScheduler", 3)
    private val app = ApplicationManager.getApplication()
    private var processTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private var goToDiffTask: Future<*>? = null
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
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED &&
                InferenceGlobalContext.status != ConnectionStatus.ERROR
            ) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            finishAnimation()
            layout?.dispose()
            layout = null
            if (editor != null) {
                ModeProvider.getOrCreateModeProvider(editor).switchMode()
            }
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {
        if (event.editor.getUserData(Resources.ExtraUserDataKeys.addedFromHL) == true) {
            event.editor.putUserData(Resources.ExtraUserDataKeys.addedFromHL, false)
            return
        }
        app.invokeAndWait { cancel(event.editor) }
        ModeProvider.getOrCreateModeProvider(event.editor).switchMode()
    }

    override fun onTextChange(event: DocumentEventExtra) {
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {}

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancel(editor)
        ModeProvider.getOrCreateModeProvider(editor).switchMode()
    }

    override fun onCaretChange(event: CaretEvent) {
        goToDiffTask?.cancel(false)
        val offsets = event.caret?.let { layout?.getHighlightsOffsets(it.offset) }
        val entry = layout?.function
        if (offsets != null && entry != null) {
            goToDiffTask = scheduler.schedule({
                // cleanup must be called from render thread; scheduler creates worker thread only
                app.invokeAndWait {
                    cleanup()
                    ModeProvider.getOrCreateModeProvider(event.editor)
                        .getDiffMode().actionPerformed(
                            event.editor, HighlightContext(entry, offsets[0], offsets[1])
                        )
                }
            }, 300, TimeUnit.MILLISECONDS)
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
        cancel(null)
    }

    fun actionPerformed(editor: Editor, fromDiff: Boolean = false, entryFromContext: LongthinkFunctionEntry? = null) {
        if (layout != null) {
            layout?.dispose()
            layout = null
        }
        if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return

        val entry: LongthinkFunctionEntry
        if (entryFromContext != null) {
            entry = entryFromContext
        } else {
            if (fromDiff && DiffIntentProvider.instance.lastHistoryEntry() != null) {
                entry = DiffIntentProvider.instance.lastHistoryEntry()!!
            } else {
                val dialog = DiffDialog(editor, true)
                if (!dialog.showAndGet()) return
                entry = dialog.entry
                DiffIntentProvider.instance.pushFrontHistoryIntent(entry)
            }
        }

        val fileName = getActiveFile(editor.document) ?: return
        val startSelectionOffset = editor.selectionModel.selectionStart
        val endSelectionOffset = editor.selectionModel.selectionEnd
        val funcName = if (entry.functionHighlight.isNullOrEmpty()) entry.functionName else
            entry.functionHighlight ?: return
        val request = RequestCreator.create(
            fileName, editor.document.text,
            startSelectionOffset, endSelectionOffset,
                "${scope}:${entry.functionName}", entry.intent, funcName, listOf(),
            model = InferenceGlobalContext.longthinkModel ?: entry.model
                ?: InferenceGlobalContext.model ?: Resources.defaultModel,
            sendToCodifyServer = entry.thirdParty
        ) ?: return
        ModeProvider.getOrCreateModeProvider(editor).switchMode(ModeType.Highlight)

        needAnimation = true
        val startPosition = editor.offsetToLogicalPosition(startSelectionOffset)
        renderTask = scheduler.submit {
            waitingHighlight(editor, startPosition, this::isProgress)
        }
        processTask = scheduler.submit {
            process(request, entry, editor)
        }
    }

    fun process(
        request: SMCRequest,
        entry: LongthinkFunctionEntry,
        editor: Editor
    ) {
        var maybeLastReqJob: RequestJob? = null
        try {
            request.body.stopTokens = listOf()
            request.body.maxTokens = 0

            InferenceGlobalContext.status = ConnectionStatus.PENDING
            maybeLastReqJob = inferenceFetch(request)
            val lastReqJob = maybeLastReqJob ?: return

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

            layout = HighlightLayout(editor, entry, request, prediction)
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
            cancel(editor)
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        } catch (e: ExecutionException) {
            catchNetExceptions(e.cause)
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        } catch (e: Exception) {
            InferenceGlobalContext.status = ConnectionStatus.ERROR
            InferenceGlobalContext.lastErrorMsg = e.message
            logger.warn("Exception while highlight request processing", e)
            ModeProvider.getOrCreateModeProvider(editor).switchMode()
        }
    }

    private fun catchNetExceptions(e: Throwable?) {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        InferenceGlobalContext.lastErrorMsg = e?.message
        logger.warn("Exception while highlight request processing", e)
    }

    private fun getActiveFile(document: Document): String? {
        if (!app.isDispatchThread) return null
        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }

}