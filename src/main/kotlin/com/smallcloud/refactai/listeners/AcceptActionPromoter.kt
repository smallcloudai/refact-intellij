package com.smallcloud.refactai.listeners

import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor

class AcceptActionsPromoter : ActionPromoter {
    private fun getEditor(dataContext: DataContext): Editor? {
        return CommonDataKeys.EDITOR.getData(dataContext)
    }
    override fun promote(actions: MutableList<out AnAction>, context: DataContext): List<AnAction> {
        val editor = getEditor(context) ?: return emptyList()
        if (InlineCompletionContext.getOrNull(editor) == null) {
            return emptyList()
        }
        actions.filterIsInstance<TabPressedAction>().takeIf { it.isNotEmpty() }?.let { return it }
        return emptyList()
    }
}