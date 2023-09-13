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
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.streamedInferenceFetch
import com.smallcloud.refactai.modes.Mode
import com.smallcloud.refactai.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.refactai.modes.ModeType
import com.smallcloud.refactai.modes.completion.prompt.RequestCreator
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import com.smallcloud.refactai.modes.highlight.HighlightContext
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.utils.getExtension
import dev.gitlive.difflib.DiffUtils
import dev.gitlive.difflib.patch.Patch
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

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
            app.invokeLater {
                finishRenderRainbow()
                diffLayout?.cancelPreview()
                diffLayout = null
            }
            if (editor != null && !Thread.currentThread().stackTrace.any { it.methodName == "switchMode" }) {
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
            getOrCreateModeProvider(editor).getHighlightMode().actionPerformed(editor)
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

    override fun cleanup(editor: Editor) {
        cancel(editor)
    }

    fun actionPerformed(editor: Editor, highlightContext: HighlightContext? = null,
                        entryFromContext: LongthinkFunctionEntry? = null) {
        lastFromHL = highlightContext != null
        val fileName = getActiveFile(editor.document) ?: return
        val selectionModel = editor.selectionModel
        var startSelectionOffset: Int = selectionModel.selectionStart
        var endSelectionOffset: Int = selectionModel.selectionEnd
        val startPosition: LogicalPosition
        val finishPosition: LogicalPosition
        val request: SMCRequest
        if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return
        if (diffLayout == null || highlightContext != null) {
            val entry: LongthinkFunctionEntry = highlightContext?.entry ?: entryFromContext ?: return
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
            ) ?: return
            startPosition = editor.offsetToLogicalPosition(startSelectionOffset)
            finishPosition = editor.offsetToLogicalPosition(endSelectionOffset - 1)
            selectionModel.removeSelection()
            editor.contentComponent.requestFocus()
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
        request.body.stopTokens = listOf()
        request.body.maxTokens = 550
        request.body.maxEdits = if (request.body.functionName == "diff-atcursor") 1 else 10

        InferenceGlobalContext.status = ConnectionStatus.PENDING

        var lastPatch: Patch<String>? = null
        streamedInferenceFetch(request, dataReceiveEnded = {
            finishRenderRainbow()
            if (lastPatch == null) return@streamedInferenceFetch
            diffLayout = DiffLayout(editor, request)
            app.invokeLater {
                diffLayout?.update(lastPatch!!)
            }

            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            InferenceGlobalContext.lastErrorMsg = null
        }) { prediction ->
            if (prediction.status == null || prediction.status == "error") {
                InferenceGlobalContext.status = ConnectionStatus.ERROR
                InferenceGlobalContext.lastErrorMsg = "Parameters are not correct"
                return@streamedInferenceFetch
            }

            val predictedText = prediction.choices.firstOrNull()?.filesHeadMidTail?.get(request.body.cursorFile)?.mid
            val finishReason = prediction.choices.firstOrNull()?.finishReason
            if (predictedText == null || finishReason == null) {
                return@streamedInferenceFetch
            }

            lastPatch = request.body.sources[request.body.cursorFile]?.let { originText ->
                prediction.choices.firstOrNull()?.filesHeadMidTail?.get(request.body.cursorFile)?.let { headMidTail ->
                    val newText = originText.replaceRange(headMidTail.head,
                            originText.length - headMidTail.tail, headMidTail.mid)
                    DiffUtils.diff(
                            originText.split('\n'),
                            newText.split('\n'),
                    )
                }
            }
        }?.also {
            var requestFuture: Future<*>? = null
            try {
                requestFuture = it.get()
                requestFuture.get()
                logger.debug("Diff request finished")
            } catch (_: InterruptedException) {
                requestFuture?.cancel(true)
                finishRenderRainbow()
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