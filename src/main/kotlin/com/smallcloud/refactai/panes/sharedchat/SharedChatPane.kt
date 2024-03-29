package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.smallcloud.refactai.panes.sharedchat.events.Events
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent


class SharedChatPane {
    private val jsPoolSize = "200"

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
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
        // maybe load this later ?
        println("html")
        println(html)
        browser.loadHTML(html)

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)

        myJSQueryOpenInBrowser.addHandler { msg ->
            // TODO: add handlers here.
            println("event from chat");
            println(msg)
            Events.parse(msg)
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

    // TODO: narrow this type
    fun postMessage(message: String) {
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {
        return webView.component
    }
}