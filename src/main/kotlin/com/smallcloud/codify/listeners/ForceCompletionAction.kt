package com.smallcloud.codify.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.codify.modes.ModeProvider
import com.smallcloud.codify.settings.AppSettingsState

object ForceCompletionAction :
    EditorAction(InlineCompletionHandler()),
    ActionToIgnore {
    const val ACTION_ID = "ForceCompletionAction"

    class InlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            Logger.getInstance("ForceCompletionAction").debug("executeWriteAction")
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            provider.beforeDocumentChangeNonBulk(null, editor)
            provider.onTextChange(null, editor, true)
        }

        override fun isEnabledForCaret(
            editor: Editor,
            caret: Caret,
            dataContext: DataContext
        ): Boolean {
            return AppSettingsState.instance.useForceCompletion
        }
    }
}
