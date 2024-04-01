package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.panes.sharedchat.events.Events
import com.smallcloud.refactai.panes.sharedchat.events.SystemPromptMap
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import java.util.concurrent.Future
import javax.swing.JComponent


class SharedChatPane {
    private val jsPoolSize = "200"
    private val lsp: LSPProcessHolder = LSPProcessHolder()

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    private fun handleCaps(id: String) {
        // Note: I'll need to look in how to do async effectively in kotlin
        this.lsp.fetchCaps().also { caps ->
            // Handle errors?
            val message: Events.Caps.Receive = Events.Caps.Receive(id, caps.get())
            println("handleCaps: message: $message")
            this.postMessage(message)
        }
    }

    private fun handleSystemPrompts(id: String) {
        this.lsp.fetchSystemPrompts().also { res ->
            val prompts: SystemPromptMap = res.get()
            val message: Events.SystemPrompts.Receive = Events.SystemPrompts.Receive(id, prompts)
            this.postMessage(message)
        }
    }

    fun handleEvent(event: Events.FromChat) {
//        println("handleEvent")
//        println(event)
//        println("type: ${event.type}, class: ${event.javaClass.name}")

        when (event) {
            is Events.Caps.Request -> this.handleCaps(event.id)
            is Events.SystemPrompts.Request -> this.handleSystemPrompts(event.id)
            is Events.AtCommands.Completion.Request -> TODO()
            is Events.Chat.Save -> TODO()
            is Events.Chat.AskQuestion -> TODO()
            is Events.Chat.Stop -> TODO()
            is Events.Editor.Paste -> TODO()
            is Events.Editor.NewFile -> TODO()
            else -> Unit
        }
    }

    val html = """
        <!doctype html>
        <html lang="en" class="light">
           <head>
               <title>Refact.ai</title>
               <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/refact-chat-js@0.1/dist/chat/style.css">
           </head>
           <body style="height:100%; padding:0px; margin: 0px;">
               <div id="refact-chat" style="height:100%;"></div>
           </body>
           <script type="module">
               import * as refactChatJs from 'https://cdn.jsdelivr.net/npm/refact-chat-js@0.1/+esm'

               window.onload = function() {
                   console.log(refactChatJs);
                   const element = document.getElementById("refact-chat");
                   const options = {
                     host: "jetbrains",
                     tabbed: false,
                     themeProps: {
                       accentColor: "gray",
                       scaling: "90%",
                     },
                     features: {
                       vecdb: false,
                       ast: false,
                     }
                   };
                   refactChatJs.render(element, options);
               };

           </script>
        </html>
    """.trimIndent()

    val webView by lazy {
        val browser = JBCefBrowser()
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            jsPoolSize,
        )
        browser.loadHTML(html)

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)

        myJSQueryOpenInBrowser.addHandler { msg ->
            val event = Events.parse(msg)
            if(event != null) { this.handleEvent(event) }
            null
        }

        var scriptAdded = false;

        browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if(!scriptAdded) {
                    println("adding script to  browser")
                    val script = """window.postIntellijMessage = function(event) {
                        const msg = JSON.stringify(event);
                        ${myJSQueryOpenInBrowser.inject("msg")}
                    }""".trimIndent()
                    browser.executeJavaScript(script, browser.url, 0);
                    scriptAdded = true;
                }
            }
        }, browser.cefBrowser)

//        val devTools = browser.cefBrowser.devTools
//        val devToolsBrowser = JBCefBrowser.createBuilder()
//            .setCefBrowser(devTools)
//            .setClient(browser.jbCefClient)
//            .build();
//
//        devToolsBrowser.openDevtools()
//
//

        browser
    }

    private fun postMessage(message: Events.ToChat) {
        val json = Events.stringify(message)
        val script = """window.postMessage($json, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {
        return webView.component
    }
}