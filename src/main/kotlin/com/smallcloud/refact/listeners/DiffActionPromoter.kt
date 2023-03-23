package com.smallcloud.refact.listeners

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

    class DiffActionsPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): MutableList<AnAction> {
        val editor = CommonDataKeys.EDITOR.getData(context)

        if (editor == null || !editor.document.isWritable) return actions.toMutableList()
        return actions.filterIsInstance<AIToolboxInvokeAction>().toMutableList()
    }
}