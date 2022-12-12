package com.smallcloud.codify.listeners

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.EditorModificationUtil.checkModificationAllowed
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.modes.completion.CompletionUtils
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class DocumentListener : BulkAwareDocumentListener {
    private val DELAY: Long = 50 // ms

    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private var last_task: Future<*>? = null

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        ObjectUtils.doIfNotNull(last_task) { task -> task.cancel(true) }
    }

    override fun documentChangedNonBulk(event: DocumentEvent) {
        val document = event.document
        val editor = EditorFactory.getInstance().getEditors(document).firstOrNull() ?: return

        val file = FileDocumentManager.getInstance().getFile(document)
        val text = document.text
        val file_name = file?.presentableName ?: return

        ObjectUtils.doIfNotNull(last_task) { task -> task.cancel(true) }

        last_task = scheduler.schedule(
            {
                ApplicationManager.getApplication()
                    .invokeLater {
                        val offset = editor.getCaretModel().getOffset()
//                                if (shouldIgnoreChange(event, editor, offset)) {
//                                    return@invokeLater
//                                }
                        val req_data = SMCRequestBody(
                            mapOf(file_name to text),
                            "Infill",
                            "infill",
                            file_name,
                            offset, offset,
                            50,
                            1,
                            listOf("\n\n")

                        )
                        SMCPlugin.instance.process(ProcessType.COMPLETION, req_data, editor)
                    }
            },
            DELAY, TimeUnit.MILLISECONDS
        )
    }

    private fun shouldIgnoreChange(event: DocumentEvent, editor: Editor, offset: Int): Boolean {
        val document = event.document
//        if (event.newLength < 1) {
//            return true
//        }
        if (editor.editorKind != EditorKind.MAIN_EDITOR
            && !ApplicationManager.getApplication().isUnitTestMode
        ) {
            return true
        }
        if (!checkModificationAllowed(editor) || document.getRangeGuard(offset, offset) != null) {
            document.fireReadOnlyModificationAttempt()
            return true
        }
        return !CompletionUtils.isValidDocumentChange(editor, document, offset, event.offset)
    }

}