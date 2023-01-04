package com.smallcloud.codify.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.smallcloud.codify.settings.AppSettingsState

class InlineActionsPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        if (!AppSettingsState.instance.useForceCompletion) return actions.toMutableList()
        return actions.filterIsInstance<CompletionAction>().toMutableList()
    }
}