package com.smallcloud.refactai.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class ForceCompletionAction :
    EditorAction(InlineCompletionHandler()),
    ActionToIgnore {
    val ACTION_ID = "ForceCompletionAction"

    init {
        this.templatePresentation.icon = Resources.Icons.LOGO_RED_16x16
    }

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
            return InferenceGlobalContext.useForceCompletion
        }
    }
}
