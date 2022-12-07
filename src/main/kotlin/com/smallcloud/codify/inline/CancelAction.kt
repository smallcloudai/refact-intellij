package com.smallcloud.codify.inline


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.Disposer

object CancelSMCInlineCompletionAction :
        EditorAction(AcceptInlineCompletionHandler()),
        ActionToIgnore{
    const val ACTION_ID = "CancelSMCInlineCompletionAction"

    class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            val preview = CompletionPreview.getInstance(editor)
            if (preview != null) {
                Disposer.dispose(preview)
            }
        }

        override fun isEnabledForCaret(
                editor: Editor,
                caret: Caret,
                dataContext: DataContext
        ): Boolean {
            return CompletionPreview.getInstance(editor) != null
        }
    }
}
