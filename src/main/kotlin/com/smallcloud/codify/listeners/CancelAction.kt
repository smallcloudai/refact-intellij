package com.smallcloud.codify.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.codify.modes.ModeProvider

object CancelSMCInlineCompletionAction :
    EditorAction(AcceptInlineCompletionHandler()),
    ActionToIgnore {
    const val ACTION_ID = "CancelSMCInlineCompletionAction"

    class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            provider.onTabPressed(dataContext)
//            val preview = CompletionProvider.getInstance(editor)
//            if (preview != null) {
//                Disposer.dispose(preview)
//            }
        }

        override fun isEnabledForCaret(
            editor: Editor,
            caret: Caret,
            dataContext: DataContext
        ): Boolean {
            return caret.isValid  // it should work only for the main caret
        }
    }
}
