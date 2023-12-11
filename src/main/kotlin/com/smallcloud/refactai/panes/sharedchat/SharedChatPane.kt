package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ui.jcef.*
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent


class SharedChatPane {
    private val JS_POOL_SIZE = "200"

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", JS_POOL_SIZE)
    }

    val webView by lazy {
        // TODO: add a debugging window
        // TBD: should chats share a browser instance?
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
                // TODO: handle events from chat.html
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

        browser
    }

    fun getComponent(): JComponent {
        return webView.component
    }

}