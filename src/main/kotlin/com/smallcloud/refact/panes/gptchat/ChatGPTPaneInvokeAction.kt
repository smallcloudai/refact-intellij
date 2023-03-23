package com.smallcloud.refact.panes.gptchat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refact.panes.CodifyAiToolboxPaneFactory.Companion.gptChatPanes
import com.smallcloud.refact.utils.getLastUsedProject

class ChatGPTPaneInvokeAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        doActionPerformed()
    }

    fun doActionPerformed(needToInlineCode: Boolean = false) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Codify AI Toolbox")
        chat?.activate{
            gptChatPanes?.addTab(needInsertCode=needToInlineCode)
        }
    }
}