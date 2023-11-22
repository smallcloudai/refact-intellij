package com.smallcloud.refactai.aitoolbox

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.listeners.AIToolboxInvokeAction
import com.smallcloud.refactai.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.utils.getLastUsedProject

class ToolboxPaneInvokeAction: AnAction(Resources.Icons.LOGO_RED_16x16) {
    private val rerunAction = AIToolboxInvokeAction()

    private fun getEditor(e: AnActionEvent): Editor? {
        return CommonDataKeys.EDITOR.getData(e.dataContext)
                ?: e.presentation.getClientProperty(Key(CommonDataKeys.EDITOR.name))
    }
    override fun actionPerformed(e: AnActionEvent) {
        val editor = getEditor(e)
        if (editor != null && (getOrCreateModeProvider(editor).isInDiffMode() ||
                        getOrCreateModeProvider(editor).isInHighlightMode())) {
            rerunAction.actionPerformed(e)
            return
        }

        val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
        if (RefactAIToolboxPaneFactory.isToolboxFocused()) {
            tw?.hide()
        } else {
            tw?.activate({
                RefactAIToolboxPaneFactory.focusToolbox()
            }, false)
        }
    }
}