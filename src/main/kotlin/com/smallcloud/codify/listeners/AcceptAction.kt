package com.smallcloud.codify.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.codify.modes.ModeProvider

object AcceptSMCInlineCompletionAction :
    EditorAction(AcceptInlineCompletionHandler()),
    ActionToIgnore {
    const val ACTION_ID = "AcceptSMCInlineCompletionAction"

    class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            provider.onTabPressed(dataContext)
//            CompletionProvider.getInstance(editor)?.applyPreview(caret ?: editor.caretModel.currentCaret)
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
