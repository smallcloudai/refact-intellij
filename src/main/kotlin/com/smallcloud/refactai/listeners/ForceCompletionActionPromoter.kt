package com.smallcloud.refactai.listeners

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext

class ForceCompletionActionPromoter : ActionPromoter {
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): List<AnAction> {
        val editor = CommonDataKeys.EDITOR.getData(context) ?: return emptyList()

        if (InlineCompletionContext.getOrNull(editor) == null) {
            return emptyList()
        }
        return actions.filterIsInstance<ForceCompletionAction>()
    }
}