package com.smallcloud.refactai.panes

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory

import com.intellij.util.ui.JBUI
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.aitoolbox.ToolboxPane
import com.smallcloud.refactai.panes.gptchat.ChatGPTPanes
import com.smallcloud.refactai.utils.getLastUsedProject
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import javax.swing.JPanel
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.intellij.ui.jcef.*
import org.cef.browser.CefFrame
import org.cef.handler.CefRenderHandler
import org.cef.handler.CefRequestHandler
import org.cef.network.CefRequest

val JS_POOL_SIZE = "200"

class RefactAIToolboxPaneFactory : ToolWindowFactory {
//    init {
//        System.setProperty("ide.browser.jcef.jsQueryPoolSize", JS_POOL_SIZE)
//    }
    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(Resources.Icons.LOGO_RED_13x13)
        super.init(toolWindow)
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val toolbox = ToolboxPane(toolWindow.disposable)
        val toolboxContent: Content = contentFactory.createContent(
                toolbox.getComponent(),
                "Toolbox",
                false
        )
        toolboxContent.isCloseable = false
        toolboxContent.putUserData(toolboxKey, toolbox)
        toolWindow.contentManager.addContent(toolboxContent)

        val gptChatPanes = ChatGPTPanes(project, toolWindow.disposable)
        val content: Content = contentFactory.createContent(
                gptChatPanes.getComponent(),
                "Chat",
                false
        )
        content.isCloseable = false
        content.putUserData(panesKey, gptChatPanes)
        toolWindow.contentManager.addContent(content)

        // TODO: move broser to it's own component
        // TODO: add a debugging window
        val browser = JBCefBrowser()
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            JS_POOL_SIZE,
        )
        browser.loadURL("http://127.0.0.1:8001/webgui/chat.html")

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        myJSQueryOpenInBrowser.addHandler { msg ->
            println("event from chat")
            println(msg)
            val json = Gson().fromJson(msg, JsonObject::class.java)
            val type = json.get("type").asString
            val data = json.get("data").asJsonObject
            when (type) {
                "user_submit_name" -> {
                    data.addProperty("type", "update_name")
                    val dataAsJson = Gson().toJson(data)
                    val script = """window.postMessage($dataAsJson, "*");"""
                    browser.executeJavaScriptAsync(script)
                }
            }

            null
        }

        browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (!isLoading) {
                    // The page has finished loading
                    println("adding script to  browser")
                    val script = """window.postIntellijMessage = function(type, data) {
                        const msg = JSON.stringify({type, data});
                        ${myJSQueryOpenInBrowser.inject("msg")}
                    }""".trimIndent()
                    browser.executeJavaScript(script, browser.url, 0);

                    // populate the chat with some data.
                    val initData = """{"command":"chat-models-populate","chat_models":["gpt-3.5-turbo"],"chat_use_model":"gpt-3.5-turbo"}"""
                    val initScript = """window.postMessage($initData, "*");"""
                    browser.executeJavaScript(initScript, browser.url, 0)

                }
            }
        }, browser.cefBrowser)

        val chatIframeContent: Content = contentFactory.createContent(
              browser.component,
            "Iframe",
            false
        )

        chatIframeContent.isCloseable = false
        toolWindow.contentManager.addContent(chatIframeContent)
    }

    companion object {
        private val panesKey = Key.create<ChatGPTPanes>("refact.panes")
        private val toolboxKey = Key.create<ToolboxPane>("refact.toolbox")
        val chat: ChatGPTPanes?
            get() {
                val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
                return tw?.contentManager?.findContent("Chat")?.getUserData(panesKey)
            }

        fun focusToolbox() {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val content = tw?.contentManager?.findContent("Toolbox") ?: return
            tw.contentManager.setSelectedContent(content, true)
            val toolbox = content.getUserData(toolboxKey)
            toolbox?.requestFocus()
        }
        fun isToolboxFocused(): Boolean {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val toolbox =  tw?.contentManager?.findContent("Toolbox")?.getUserData(toolboxKey)
            return toolbox?.isFocused() ?: false
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