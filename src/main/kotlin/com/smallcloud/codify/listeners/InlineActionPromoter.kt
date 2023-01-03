package com.smallcloud.codify.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.smallcloud.codify.modes.ModeProvider

class InlineActionsPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val editor = CommonDataKeys.EDITOR.getData(context) ?: return actions.toMutableList()
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        if (provider.modeInActiveState()) return actions.toMutableList()
        return actions.filterIsInstance<CompletionAction>().toMutableList()
    }
}