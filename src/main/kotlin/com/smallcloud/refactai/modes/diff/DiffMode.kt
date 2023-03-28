package com.smallcloud.refactai.modes.diff

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.util.TextRange
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.io.RequestJob
import com.smallcloud.refactai.io.inferenceFetch
import com.smallcloud.refactai.modes.Mode
import com.smallcloud.refactai.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.refactai.modes.ModeType
import com.smallcloud.refactai.modes.completion.prompt.RequestCreator
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import com.smallcloud.refactai.modes.diff.dialog.DiffDialog
import com.smallcloud.refactai.modes.highlight.HighlightContext
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.SMCPrediction
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.utils.getExtension
import dev.gitlive.difflib.DiffUtils
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future

class DiffMode(
    override var needToRender: Boolean = true
) : Mode {
    private val scope: String = "query_diff"
    private val logger = Logger.getInstance(DiffMode::class.java)
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("SMCDiffScheduler", 2)
    private val app = ApplicationManager.getApplication()
    private var diffLayout: DiffLayout? = null
    private var processTask: Future<*>? = null
    private var renderTask: Future<*>? = null
    private var needRainbowAnimation: Boolean = false
    private var lastFromHL: Boolean = false

    private fun isProgress(): Boolean {
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
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED &&
                InferenceGlobalContext.status != ConnectionStatus.ERROR
            ) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            app.invokeAndWait {
                finishRenderRainbow()
                diffLayout?.cancelPreview()
                diffLayout = null
            }
            if (editor != null) {
                getOrCreateModeProvider(editor).switchMode()
            }
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {
        cancel(event.editor)
    }

    override fun onTextChange(event: DocumentEventExtra) {
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        diffLayout?.applyPreview()
        diffLayout = null
        editor.putUserData(Resources.ExtraUserDataKeys.addedFromHL, lastFromHL)
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

    fun isInRenderState(): Boolean {
        return (diffLayout != null && !diffLayout!!.rendered) ||
                (renderTask != null && !renderTask!!.isDone && !renderTask!!.isCancelled) || isProgress()
    }

    override fun isInActiveState(): Boolean {
        return isInRenderState() ||
                (processTask != null && !processTask!!.isDone && !processTask!!.isCancelled) ||
                diffLayout != null
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

    fun actionPerformed(editor: Editor, highlightContext: HighlightContext? = null,
                        entryFromContext: LongthinkFunctionEntry? = null) {
        val fileName = getActiveFile(editor.document) ?: return
        val selectionModel = editor.selectionModel
        var startSelectionOffset: Int = selectionModel.selectionStart
        var endSelectionOffset: Int = selectionModel.selectionEnd
        val startPosition: LogicalPosition
        val finishPosition: LogicalPosition
        val request: SMCRequest
        if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return
        if (diffLayout == null || highlightContext != null) {
            val entry: LongthinkFunctionEntry
            if (entryFromContext != null) {
                entry = entryFromContext
            } else {
                if (highlightContext == null) {
                    val dialog = DiffDialog(editor, startPosition = editor.offsetToLogicalPosition(startSelectionOffset),
                            finishPosition = editor.offsetToLogicalPosition(endSelectionOffset),
                            selectedText = editor.document.getText(TextRange(startSelectionOffset,endSelectionOffset)))
                    if (!dialog.showAndGet()) return
                    entry = dialog.entry
                    DiffIntentProvider.instance.pushFrontHistoryIntent(entry)
                    lastFromHL = false
                } else {
                    entry = highlightContext.entry
                    startSelectionOffset = highlightContext.startOffset
                    endSelectionOffset = highlightContext.endOffset
                    lastFromHL = true
                }
            }

            var funcName = (if (lastFromHL) entry.functionHlClick else entry.functionSelection)
            if (funcName.isNullOrEmpty()) {
                funcName = entry.functionName
            }

            val stat = UsageStatistic(scope, entry.functionName, extension = getExtension(fileName))
            request = RequestCreator.create(
                fileName, editor.document.text,
                startSelectionOffset, endSelectionOffset,
                stat, entry.intent, funcName, listOf(),
                model = InferenceGlobalContext.longthinkModel ?: entry.model ?: InferenceGlobalContext.model ?: Resources.defaultModel,
                sendToCloudServer = entry.thirdParty
            ) ?: return
            startPosition = editor.offsetToLogicalPosition(startSelectionOffset)
            finishPosition = editor.offsetToLogicalPosition(endSelectionOffset - 1)
            selectionModel.removeSelection()
            getOrCreateModeProvider(editor).switchMode(ModeType.Diff)
        } else {
            val lastDiffLayout = diffLayout ?: return
            request = lastDiffLayout.request
            startPosition = editor.offsetToLogicalPosition(request.body.cursor0)
            finishPosition = editor.offsetToLogicalPosition(request.body.cursor1)
            lastDiffLayout.cancelPreview()
            diffLayout = null
        }

        needRainbowAnimation = true
        renderTask = scheduler.submit {
            waitingDiff(
                editor,
                startPosition, finishPosition,
                this::isProgress
            )
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

            InferenceGlobalContext.status = ConnectionStatus.PENDING
            maybeLastReqJob = inferenceFetch(request)
            val lastReqJob = maybeLastReqJob ?: return

            val prediction = lastReqJob.future.get() as SMCPrediction
            if (prediction.status == null || prediction.status == "error") {
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

            val patch = request.body.sources[request.body.cursorFile]?.let {
                prediction.choices[0].files[request.body.cursorFile]?.let { it1 ->
                    DiffUtils.diff(
                        it.split('\n'),
                        it1.split('\n'),
                    )
                }
            }
            finishRenderRainbow()
            if (patch == null) return

            diffLayout = DiffLayout(editor, request, patch)
            app.invokeAndWait {
                diffLayout?.render()
            }
        } catch (_: InterruptedException) {
            finishRenderRainbow()
            maybeLastReqJob?.let {
                maybeLastReqJob.request?.abort()
                logger.debug("lastReqJob abort")
            }
            getOrCreateModeProvider(editor).switchMode()
        } catch (e: ExecutionException) {
            catchNetExceptions(e.cause)
            getOrCreateModeProvider(editor).switchMode()
        } catch (e: Exception) {
            InferenceGlobalContext.status = ConnectionStatus.ERROR
            InferenceGlobalContext.lastErrorMsg = e.message
            logger.warn("Exception while diff request processing", e)
            getOrCreateModeProvider(editor).switchMode()
        }
    }

    private fun catchNetExceptions(e: Throwable?) {
        InferenceGlobalContext.status = ConnectionStatus.ERROR
        InferenceGlobalContext.lastErrorMsg = e?.message
        logger.warn("Exception while diff request processing", e)
    }

    private fun getActiveFile(document: Document): String? {
        if (!app.isDispatchThread) return null
        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }
}