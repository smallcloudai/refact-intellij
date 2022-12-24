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
import com.smallcloud.codify.Resources.defaultContrastUrlSuffix
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.inferenceFetch
import com.smallcloud.codify.modes.EditorTextHelper
import com.smallcloud.codify.modes.Mode
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.util.concurrent.CancellationException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

data class EditorState(
    val modificationStamp: Long,
    val offset: Int,
    val text: String
)

class CompletionMode : Mode(), CaretListener {
    private val scope: String = "CompletionProvider"
    private val app = ApplicationManager.getApplication()
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private val taskDelayMs: Long = 250
    private var processTask: Future<*>? = null
    private var completionLayout: CompletionLayout? = null
    private val logger = Logger.getInstance("CompletionMode")

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent, editor: Editor) {
        cancelOrClose(editor)
    }

    override fun onTextChange(event: DocumentEvent, editor: Editor) {
        if (editor.caretModel.offset + event.newLength > editor.document.text.length) return
        if (event.newLength + event.oldLength <= 0) return

        val hasManyChanges = event.newLength > 5 || event.oldLength > 5
        val state = EditorState(
            editor.document.modificationStamp,
            editor.caretModel.offset + event.newLength,
            editor.document.text
        )
        val completionData = CompletionCache.getCompletion(state.text, state.offset)
        if (completionData != null) {
            processTask = scheduler.submit {
                app.invokeLater { renderCompletion(editor, state, completionData) }
            }
            return
        }

        val document = event.document
        val fileName = getActiveFile(document) ?: return
        if (shouldIgnoreChange(event, editor, state.offset)) {
            return
        }
        val request = makeRequest(fileName, state.text, state.offset) ?: return
        request.uri = request.uri.resolve(defaultContrastUrlSuffix)
        val editorHelper = EditorTextHelper(editor, state.offset)

        processTask = scheduler.schedule({
            process(request, editor, state, editorHelper)
        }, if (hasManyChanges) 0 else taskDelayMs, TimeUnit.MILLISECONDS)
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
        val req = InferenceGlobalContext.makeRequest(requestBody)
        if (req != null) {
            req.scope = scope
        }
        return req
    }

    private fun renderCompletion(
        editor: Editor,
        state: EditorState,
        completionData: Completion,
    ) {
        val invalidStamp = state.modificationStamp != editor.document.modificationStamp
        val invalidOffset = state.offset != editor.caretModel.offset
        if (invalidStamp || invalidOffset) {
            logger.info("Completion is droppped: invalidStamp || invalidOffset")
            return
        }
        if (processTask == null) {
            logger.info("Completion is droppped: there is no active processTask is left")
            return
        }
        logger.info(
            "Completion rendering: state_offset: ${state.offset}," +
                    " state_modificationStamp: ${state.modificationStamp}"
        )
        logger.info(
            "Completion rendering: editor_offset: ${editor.caretModel.offset}," +
                    " editor_modificationStamp: ${editor.document.modificationStamp}"
        )
        logger.info("Competion data: ${completionData.completion}")
        completionLayout = CompletionLayout(editor, completionData).render()
        editor.caretModel.addCaretListener(this)
    }

    fun hideCompletion() {
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                app.invokeLater { completionLayout?.hide() }
            }
        }
    }

    fun showCompletion() {
        scheduler.submit {
            try {
                processTask?.get()
            } catch (_: CancellationException) {
            } finally {
                app.invokeLater { completionLayout?.show() }
            }
        }
    }

    private fun process(
        request: SMCRequest,
        editor: Editor,
        state: EditorState,
        editorHelper: EditorTextHelper
    ) {
        val conn = InferenceGlobalContext.connection ?: return
        val lastReqJob = inferenceFetch(request)

        try {
            val completionState = CompletionState(editorHelper)
            if (!completionState.readyForCompletion) return
            request.body.stopTokens = completionState.stopTokens

            val prediction = lastReqJob?.future?.get() as SMCPrediction

            if (prediction.status == null) {
                conn.status = ConnectionStatus.ERROR
                conn.lastErrorMsg = "Parameters are not correct"
                return
            }

            val predictedText = prediction.choices?.firstOrNull()?.files?.get(request.body.cursorFile)
            if (predictedText == null) {
                conn.status = ConnectionStatus.ERROR
                conn.lastErrorMsg = "Request was succeeded but there is no predicted data"
                return
            } else {
                conn.status = ConnectionStatus.CONNECTED
                conn.lastErrorMsg = null
            }

            val completionData = completionState.difference(predictedText) ?: return
            if (!completionData.isMakeSense()) return
            synchronized(this) {
                CompletionCache.addCompletion(completionData)
            }
            app.invokeAndWait {
                renderCompletion(editor, state, completionData)
            }
        } catch (_: InterruptedException) {
            if (lastReqJob != null) {
                lastReqJob.request?.abort()
                logger.info("lastReqJob abort")
            }
        } catch (e: Exception) {
            conn.status = ConnectionStatus.ERROR
            conn.lastErrorMsg = e.toString()
            logger.warn("Exception while completion request processing", e)
        }
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        completionLayout?.applyPreview(caret ?: editor.caretModel.currentCaret)
        cancelOrClose(editor)
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancelOrClose(editor)
    }

    override fun caretPositionChanged(event: CaretEvent) {
        cancelOrClose(event.editor)
    }

    override fun isInActiveState(): Boolean = completionLayout != null && completionLayout!!.rendered

    private fun shouldIgnoreChange(event: DocumentEvent, editor: Editor, offset: Int): Boolean {
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

    fun cancelOrClose(editor: Editor) {
        logger.info("cancelOrClose request")
        try {
            processTask?.cancel(true)
            processTask?.get()
        } catch (_: CancellationException) {
        } finally {
            processTask = null
            completionLayout?.dispose()
            completionLayout = null
            editor.caretModel.removeCaretListener(this)
        }
    }
}
