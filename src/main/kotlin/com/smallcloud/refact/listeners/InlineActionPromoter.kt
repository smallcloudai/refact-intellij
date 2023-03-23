package com.smallcloud.refact.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext
import com.smallcloud.refact.io.InferenceGlobalContext

class InlineActionsPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        if (!InferenceGlobalContext.useForceCompletion) return actions.toMutableList()
        return actions.filterIsInstance<ForceCompletionAction>().toMutableList()
    }
}