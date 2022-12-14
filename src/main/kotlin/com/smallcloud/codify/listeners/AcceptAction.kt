package com.smallcloud.codify.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.codify.modes.ModeProvider
import com.intellij.openapi.diagnostic.Logger

object TabPressedAction :
    EditorAction(AcceptInlineCompletionHandler()),
    ActionToIgnore {
    const val ACTION_ID = "TabPressedAction"

    class AcceptInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            Logger.getInstance("TabPressedAction").warn("executeWriteAction")
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            provider.onTabPressed(editor, caret, dataContext)
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
