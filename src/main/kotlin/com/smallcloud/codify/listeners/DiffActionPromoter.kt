package com.smallcloud.codify.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DataContext

class DiffActionsPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        return actions.filterIsInstance<DiffInvokeAction>().toMutableList()
    }
}