package com.smallcloud.refactai.panes.gptchat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory.Companion.gptChatPanes
import com.smallcloud.refactai.utils.getLastUsedProject

class ChatGPTPaneInvokeAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact Chat")
        chat?.activate{
            gptChatPanes?.requestFocus()
        }
    }

    fun doActionPerformed(needToInlineCode: Boolean = false) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact Chat")
        chat?.activate{
            gptChatPanes?.addTab(needInsertCode=needToInlineCode)
        }
    }
}