package com.smallcloud.refactai.panes.gptchat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.utils.getLastUsedProject

class ChatGPTPaneInvokeAction: AnAction(Resources.Icons.LOGO_RED_16x16) {
    override fun actionPerformed(e: AnActionEvent) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
        chat?.activate{
            RefactAIToolboxPaneFactory.focusChat()
        }
    }

    fun doActionPerformed(needToInlineCode: Boolean = false) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
        chat?.activate{
            RefactAIToolboxPaneFactory.chat?.addTab(needInsertCode=needToInlineCode, needSend = false)
            RefactAIToolboxPaneFactory.focusChat()
        }
    }
}