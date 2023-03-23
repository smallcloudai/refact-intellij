package com.smallcloud.refact.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.event.DocumentListener

class LastEditorGetterListener : EditorFactoryListener, Disposable {
    override fun editorCreated(event: EditorFactoryEvent) {
        event.editor.caretModel.addCaretListener(object: CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                LAST_EDITOR = event.editor
            }
            override fun caretRemoved(event: CaretEvent) {
                LAST_EDITOR = event.editor
            }

            override fun caretAdded(event: CaretEvent) {
                LAST_EDITOR = event.editor
            }
        })
        event.editor.document.addDocumentListener(object: DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                LAST_EDITOR = getActiveEditor(event.document)
            }

        })
        LAST_EDITOR = event.editor
    }
    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }
    override fun dispose() {}
    companion object {
        var LAST_EDITOR: Editor? = null
    }
}