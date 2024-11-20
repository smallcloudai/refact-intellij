package com.smallcloud.refactai.codecompletion

import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.util.application

class RefactInlineCompletionDocumentListener : BulkAwareDocumentListener {
    override fun documentChangedNonBulk(event: DocumentEvent) {
        val editor = getActiveEditor(event.document) ?: return
        val handler = InlineCompletion.getHandlerOrNull(editor)
        application.invokeLater {
            handler?.invokeEvent(
                RefactAIContinuousEvent(
                    editor, editor.caretModel.offset
                )
            )
        }
    }


    private fun getActiveEditor(document: Document): Editor? {
        if (!ApplicationManager.getApplication().isDispatchThread) {
            return null
        }
        return EditorFactory.getInstance().getEditors(document).firstOrNull()
    }
}
