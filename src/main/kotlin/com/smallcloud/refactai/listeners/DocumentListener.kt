package com.smallcloud.refactai.listeners

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.modes.ModeProvider


class DocumentListener : BulkAwareDocumentListener, Disposable {
    override fun beforeDocumentChangeNonBulk(event: DocumentEvent) {
        Logger.getInstance("DocumentListener").debug("beforeDocumentChangeNonBulk")
        val editor = getActiveEditor(event.document) ?: return
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.beforeDocumentChangeNonBulk(event, editor)
    }

    override fun documentChangedNonBulk(event: DocumentEvent) {
        Logger.getInstance("DocumentListener").debug("documentChangedNonBulk")
        if (InferenceGlobalContext.useForceCompletion) return
        val editor = getActiveEditor(event.document) ?: return
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.onTextChange(event, editor, false)
    }

    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }

    override fun dispose() {}
}
