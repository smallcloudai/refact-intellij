package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.gptchat.ChatGPTPanes


class RefactAIToolboxPaneFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        gptChatPanes = ChatGPTPanes(toolWindow.project, toolWindow.disposable)
        toolWindow.setIcon(Resources.Icons.LOGO_RED_16x16)
        super.init(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.SERVICE.getInstance()

        val content: Content = contentFactory.createContent(
                gptChatPanes?.getComponent(),
                "Refact AI Chat",
                false
        )
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        var gptChatPanes: ChatGPTPanes? = null
    }
}