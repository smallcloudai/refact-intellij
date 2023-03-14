package com.smallcloud.codify.panes.gptchat

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.codify.panes.CodifyAiToolboxPaneFactory.Companion.gptChatPanes
import com.smallcloud.codify.utils.getLastUsedProject

class ChatGPTPaneInvokeAction: AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val chat = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Codify AI Toolbox")
        chat?.activate{
            gptChatPanes?.requestFocus()
        }
    }
}