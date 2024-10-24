package com.smallcloud.refactai.code_lens

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Future

class CodeLensEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        refactCodeLensEditorKey[event.editor] = CodeLensLayout(event.editor)
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        refactCodeLensEditorKey[event.editor].dispose()
        refactCodeLensEditorKey[event.editor] = null
    }
}

class CodeLensDocumentListener : BulkAwareDocumentListener, Disposable {
    private var futures: MutableMap<Editor, Future<*>> = mutableMapOf()
    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        val editor = getActiveEditor(event.document)?: return
        refactCodeLensEditorKey[editor].dispose()
        refactCodeLensEditorKey[editor] = null
        futures.remove(editor)
    }

    override fun documentChangedNonBulk(event: DocumentEvent) {
        val editor = getActiveEditor(event.document)?: return
        futures[editor] = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            refactCodeLensEditorKey[editor] = CodeLensLayout(editor)
            futures.remove(editor)
        }, 500, java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }

    override fun dispose() {}
}