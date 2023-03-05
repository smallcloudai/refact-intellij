package com.smallcloud.codify.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.smallcloud.codify.panes.gptchat.ChatGPTPane


class CodifyAiToolboxPaneFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.SERVICE.getInstance()

        val content: Content = contentFactory.createContent(
                gptChatPane.getComponent(),
                "ChatGPT",
                false
        )
        content.setCloseable(false);
        toolWindow.getContentManager().addContent(content);
    }

    companion object {
        var gptChatPane = ChatGPTPane()
    }
}