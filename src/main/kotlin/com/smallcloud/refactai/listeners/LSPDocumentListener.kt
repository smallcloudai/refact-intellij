package com.smallcloud.refactai.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.lsp.lspDocumentDidChanged


class LSPDocumentListener : BulkAwareDocumentListener, Disposable {
    override fun documentChanged(event: DocumentEvent) {
        val editor = getActiveEditor(event.document) ?: return
        val vFile = getVirtualFile(editor) ?: return
        if (!vFile.exists()) return
        val project = editor.project ?: return

        lspDocumentDidChanged(project, vFile.url, editor.document.text)
    }

    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    override fun dispose() {}
}
