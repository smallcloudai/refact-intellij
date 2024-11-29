package com.smallcloud.refactai.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.smallcloud.refactai.modes.ModeProvider

class AcceptActionsPromoter : ActionPromoter {
    private fun getEditor(dataContext: DataContext): Editor? {
        return CommonDataKeys.EDITOR.getData(dataContext)
    }
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val editor = getEditor(context) ?: return actions.toMutableList()
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        if (!provider.isInCompletionMode()) {
            return actions.filterIsInstance<TabPressedAction>().toMutableList()
        }
        return actions.toMutableList()
    }
}