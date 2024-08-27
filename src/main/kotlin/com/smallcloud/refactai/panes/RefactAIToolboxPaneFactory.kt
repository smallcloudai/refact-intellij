package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.sharedchat.ChatPanes
import com.smallcloud.refactai.utils.getLastUsedProject


class RefactAIToolboxPaneFactory : ToolWindowFactory {
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(Resources.Icons.LOGO_RED_13x13)
        super.init(toolWindow)
    }

    override fun isApplicable(project: Project): Boolean {
        return try {
            JBCefApp.isSupported() && JBCefApp.isStarted()
            JBCefApp.isSupported()
        } catch (_: Exception) {
            false
        }
    }


    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val chatPanes = ChatPanes(project)
        Disposer.register(toolWindow.disposable, chatPanes)
        val content: Content = contentFactory.createContent(chatPanes.getComponent(), "Chat", false)
        content.isCloseable = false
        content.putUserData(panesKey, chatPanes)
        toolWindow.contentManager.addContent(content)
    }



    companion object {
        private val panesKey = Key.create<ChatPanes>("refact.panes")
        val chat: ChatPanes?
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