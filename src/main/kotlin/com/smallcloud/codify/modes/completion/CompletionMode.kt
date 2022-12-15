package com.smallcloud.codify.modes.completion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.io.Connection
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.Resources.defaultContrastUrlSuffix
import com.smallcloud.codify.io.fetch
import com.smallcloud.codify.modes.EditorTextHelper
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class EditorState(
    val modificationStamp: Long,
    val offset: Int,
    val text: String
)

class CompletionMode : Mode() {
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private val taskDelayMs: Long = 250
    private var processTask: Future<*>? = null
    private var completionLayout: CompletionLayout? = null
    private val logger = Logger.getInstance("CompletionMode")

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent, editor: Editor) {
        if (completionLayout != null && completionLayout!!.blockEvents) return
        cancelOrClose()
    }

    override fun onTextChange(event: DocumentEvent, editor: Editor) {
        if (completionLayout != null && completionLayout!!.blockEvents) return
        if (editor.caretModel.offset + event.newLength > editor.document.text.length) return

        val state = EditorState(
            editor.document.modificationStamp,
            editor.caretModel.offset + event.newLength,
            editor.document.text
        )
        val document = event.document
        val fileName = getActiveFile(document) ?: return
        if (shouldIgnoreChange(event, editor, state.offset)) {
            return
        }
        val request = makeRequest(fileName, state.text, state.offset) ?: return
        request.url += defaultContrastUrlSuffix
        val editorHelper = EditorTextHelper(editor, state.offset)
        processTask = scheduler.schedule({
            process(request, editor, state, editorHelper)
        }, taskDelayMs, TimeUnit.MILLISECONDS)
    }

    private fun makeRequest(fileName: String, text: String, offset: Int): SMCRequest? {
        val requestBody = SMCRequestBody(
            mapOf(fileName to text),
            "Infill",
            "infill",
            fileName,
            offset, offset,
            50,
            1,
            listOf("\n\n")
        )
        return InferenceGlobalContext.makeRequest(requestBody)
    }

    private fun process(request: SMCRequest, editor: Editor, state: EditorState, editorHelper: EditorTextHelper) {
        try {
            val completionState = CompletionState(editorHelper)
            if (!completionState.readyForCompletion) return
            var completionData = CompletionCache.getCompletion(state.text)
            if (completionData == null) {
                request.body.stopTokens = completionState.stopTokens

                val prediction = fetch(request) ?: return
                if (prediction.status == null) {
                    Connection.status = ConnectionStatus.ERROR
                    Connection.lastErrorMsg = "Parameters are not correct"
                    return
                }

                val predictedText = prediction.choices?.firstOrNull()?.files?.get(request.body.cursorFile)
                if (predictedText == null) {
                    Connection.status = ConnectionStatus.ERROR
                    Connection.lastErrorMsg = "Request was succeeded but there is no predicted data"
                    return
                }

                completionData = completionState.difference(predictedText) ?: return
                if (!completionData.isMakeSense()) return
                synchronized(this) {
                    CompletionCache.addCompletion(completionData)
                }
            }

            ApplicationManager.getApplication()
                .invokeAndWait {
                    val invalidStamp = state.modificationStamp != editor.document.modificationStamp
                    val invalidOffset = state.offset != editor.caretModel.offset
                    if (invalidStamp || invalidOffset) {
                        return@invokeAndWait
                    }
                    completionLayout = CompletionLayout(editor, completionData)
                    completionLayout!!.render()
                }
        } catch (_: ProcessCanceledException) {
            // do nothing now
            // send a cancel request later
        } catch (e: Exception) {
            Connection.status = ConnectionStatus.ERROR
            Connection.lastErrorMsg = e.toString()
            logger.warn("Exception while completion request processing", e)
        }
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        completionLayout?.applyPreview(caret ?: editor.caretModel.currentCaret)
        cancelOrClose()
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancelOrClose()
    }

    override fun isInActiveState(): Boolean = completionLayout != null

    private fun shouldIgnoreChange(event: DocumentEvent, editor: Editor, offset: Int): Boolean {
        val document = event.document

        if (editor.editorKind != EditorKind.MAIN_EDITOR
            && !ApplicationManager.getApplication().isUnitTestMode
        ) {
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
        if (!ApplicationManager.getApplication().isDispatchThread) return null

        val file = FileDocumentManager.getInstance().getFile(document)
        return file?.presentableName
    }

    private fun cancelOrClose() {
        ApplicationManager.getApplication()
        logger.info("cancelOrClose request")
        processTask?.cancel(true)
        processTask = null
        completionLayout?.let { Disposer.dispose(it) }
        completionLayout = null
    }
}
