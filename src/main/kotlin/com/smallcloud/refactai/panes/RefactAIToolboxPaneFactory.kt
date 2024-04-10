package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.gptchat.ChatGPTPanes
import com.smallcloud.refactai.utils.getLastUsedProject
import com.smallcloud.refactai.panes.sharedchat.SharedChatPane


class RefactAIToolboxPaneFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(Resources.Icons.LOGO_RED_13x13)
        super.init(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

//        val gptChatPanes = ChatGPTPanes(project, toolWindow.disposable)
//        val content: Content = contentFactory.createContent(
//                gptChatPanes.getComponent(),
//                "Chat",
//                false
//        )
//        content.isCloseable = false
//        content.putUserData(panesKey, gptChatPanes)
//        toolWindow.contentManager.addContent(content)

        val chatIframeContent: Content = contentFactory.createContent(
            SharedChatPane(project).getComponent(),
            "Shared Chat",
            false
        )
        chatIframeContent.isCloseable = false
        toolWindow.contentManager.addContent(chatIframeContent)
    }

    companion object {
        private val panesKey = Key.create<ChatGPTPanes>("refact.panes")
        val chat: ChatGPTPanes?
            get() {
                val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
                return tw?.contentManager?.findContent("Chat")?.getUserData(panesKey)
            }


        fun focusChat() {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val content = tw?.contentManager?.findContent("Chat") ?: return
            tw.contentManager.setSelectedContent(content, true)
            val panes = content.getUserData(panesKey)
            panes?.requestFocus()
        }
    }
}