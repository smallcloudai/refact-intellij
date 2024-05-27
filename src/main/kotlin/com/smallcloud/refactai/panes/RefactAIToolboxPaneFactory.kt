package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.jcef.JBCefApp
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.gptchat.ChatGPTPanes
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

//        val gptChatPanes = ChatGPTPanes(project, toolWindow.disposable)
//        val content: Content = contentFactory.createContent(
//                gptChatPanes.getComponent(),
//                "Chat",
//                false
//        )
//        content.isCloseable = false
//        content.putUserData(panesKey, gptChatPanes)
//        toolWindow.contentManager.addContent(content)

        val chatPanes = ChatPanes(project, toolWindow.disposable)
        val content: Content = contentFactory.createContent(chatPanes.getComponent(), "Chat", false)
        content.isCloseable = false
        toolWindow.contentManager.addContent(content)

//        val sp = SharedChatPane(project)
//
//        val chatIframeContent: Content = contentFactory.createContent(
//            sp.getComponent(),
//            "Shared Chat",
//            false
//        )
//        chatIframeContent.isCloseable = false
//
//        toolWindow.contentManager.addContent(chatIframeContent)


        // Uncomment to enable dev tools
//        val devToolsBrowser = JBCefBrowser.createBuilder()
//            .setCefBrowser(sp.webView.cefBrowser.devTools)
//            .setClient(sp.webView.jbCefClient)
//            .build();
//
//        val c = contentFactory.createContent(devToolsBrowser.component, "Shared Chat Dev", false)
//        toolWindow.contentManager.addContent(c)
//        devToolsBrowser.openDevtools()


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