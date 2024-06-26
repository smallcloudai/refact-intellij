package com.smallcloud.refactai.panes.sharedchat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.utils.getLastUsedProject

class ChatPaneInvokeAction: AnAction(Resources.Icons.LOGO_RED_16x16) {
    override fun actionPerformed(e: AnActionEvent) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
        chat?.activate{
            RefactAIToolboxPaneFactory.focusChat()
        }
    }
}