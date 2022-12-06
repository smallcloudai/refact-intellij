package com.smallcloud.codify.inline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorModificationUtil.checkModificationAllowed
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.struct.ProcessType
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.utils.CompletionUtils
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class SMCDocumentListener : BulkAwareDocumentListener {
    private val DELAY: Long = 50 // ms

    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()
    private var last_task: Future<*>? = null

    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        ObjectUtils.doIfNotNull(last_task) { task -> task.cancel(true) }
    }


    override fun documentChangedNonBulk(event: DocumentEvent) {
        val document = event.document
        val editor = getActiveEditor(document) ?: return
        val text = editor.document.text
        val file_name = getActiveFile(document) ?: return

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
                                SMCPlugin.instant.process(ProcessType.COMPLETION, req_data, editor)
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
                && !ApplicationManager.getApplication().isUnitTestMode) {
            return true
        }
        if (!checkModificationAllowed(editor) || document.getRangeGuard(offset, offset) != null) {
            document.fireReadOnlyModificationAttempt()
            return true
        }
        return !CompletionUtils.isValidDocumentChange(editor, document, offset, event.offset)
    }

    private fun getActiveEditor(document: Document): Editor? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        return FileEditorManager.getInstance(project).selectedTextEditor
    }

    private fun getActiveFile(document: Document): String? {
        val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return null
        val vFile = psiFile.getOriginalFile().getVirtualFile() ?: return null
        val path = vFile.presentableName
        return path
    }
}