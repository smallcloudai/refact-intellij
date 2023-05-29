package com.smallcloud.refactai.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.smallcloud.refactai.modes.ModeProvider

class ForceCompletionActionPromoter : ActionPromoter {
    private fun getEditor(dataContext: DataContext): Editor? {
        return CommonDataKeys.EDITOR.getData(dataContext)
    }
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val editor: Editor = getEditor(context) ?: return actions.toMutableList()
        val modeProvider = ModeProvider.getOrCreateModeProvider(editor)
        if (modeProvider.isInCompletionMode())
            return actions.filterIsInstance<ForceCompletionAction>().toMutableList()
        return actions.toMutableList()
    }
}