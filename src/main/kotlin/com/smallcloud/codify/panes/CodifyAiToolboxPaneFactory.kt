package com.smallcloud.codify.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.smallcloud.codify.panes.gptchat.ChatGPTPanes


class CodifyAiToolboxPaneFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        gptChatPanes = ChatGPTPanes(toolWindow.project, toolWindow.disposable)
        super.init(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.SERVICE.getInstance()

        val content: Content = contentFactory.createContent(
                gptChatPanes?.getComponent(),
                "Codify Chat",
                false
        )
        content.isCloseable = false
        toolWindow.contentManager.addContent(content);
    }

    companion object {
        var gptChatPanes: ChatGPTPanes? = null
    }
}