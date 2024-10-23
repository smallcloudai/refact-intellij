package com.smallcloud.refactai.panes.sharedchat.browser

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.ui.jcef.*
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.lsp.LSPProcessHolderChangedNotifier
import com.smallcloud.refactai.panes.sharedchat.Editor
import com.smallcloud.refactai.panes.sharedchat.Events
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefAuthCallback
import org.cef.callback.CefCallback
import org.cef.handler.*
import org.cef.misc.BoolRef
import org.cef.misc.StringRef
import org.cef.network.CefCookie
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import org.cef.network.CefURLRequest
import org.cef.security.CefSSLInfo
import javax.swing.JComponent


fun getActionKeybinding(actionId: String): String {
    // Get the KeymapManager instance
    val keymapManager: KeymapManager = KeymapManager.getInstance()

    // Get the active keymap
    val activeKeymap: Keymap = keymapManager.activeKeymap

    // Find the shortcuts for the given action ID
    val shortcuts = activeKeymap.getShortcuts(actionId).toList()

    return KeymapUtil.getShortcutText(shortcuts[0])
}

class ChatWebView(val editor: Editor, val messageHandler: (event: Events.FromChat) -> Unit) : Disposable {
    private val jsPoolSize = "200"

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    fun setStyle() {
        val isDarkMode = UIUtil.isUnderDarcula()
        val mode = if (isDarkMode) {
            "dark"
        } else {
            "light"
        }
        val bodyClass = if (isDarkMode) {
            "vscode-dark"
        } else {
            "vscode-light"
        }
        val backgroundColour = UIUtil.getPanelBackground()
        val red = backgroundColour.red
        val green = backgroundColour.green
        val blue = backgroundColour.blue
        this.webView.executeJavaScriptAsync("""document.body.style.setProperty("background-color", "rgb($red, $green, $blue");""")
        this.webView.executeJavaScriptAsync("""document.body.class = "$bodyClass";""")
        this.webView.executeJavaScriptAsync("""document.documentElement.className = "$mode";""")

    }

    val webView by lazy {


        val browser = JBCefBrowser
            .createBuilder()
            .setEnableOpenDevToolsMenuItem(true)
            .setUrl("http://refactai/index.html")
            // change this to enable dev tools
            // setting to false prevents "Accept diff with tab"
            // setting to true causes slow scroll issues :/
            .setOffScreenRendering(true)
            .build()

        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
                browser?.uiComponent?.cursor = java.awt.Cursor.getPredefinedCursor(cursorType);
                return false
            }
        }, browser.cefBrowser)

        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            jsPoolSize,
        )
        if (System.getenv("REFACT_DEBUG") != "1") {
            browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        }


        CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        addMessageHandler(myJSQueryOpenInBrowser)

        val myJSQueryOpenInBrowserRedirectHyperlink = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        myJSQueryOpenInBrowserRedirectHyperlink.addHandler { href ->
            if (href.isNotEmpty() && !href.contains("#") && !href.equals("http://refactai/index.html") ) {
                BrowserUtil.browse(href)
            }
            null
        }

        var installedScript = false
        var setupReact = false

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (isLoading) {
                    return;
                }

                if (!installedScript) {
                    installedScript = setUpJavaScriptMessageBus(browser, myJSQueryOpenInBrowser)
                }

                if (!setupReact) {
                    setupReact = true
                    setUpReact(browser)
                }

                setUpJavaScriptMessageBusRedirectHyperlink(browser, myJSQueryOpenInBrowserRedirectHyperlink)
                setStyle()
            }

        }, browser.cefBrowser)

        browser.createImmediately()
        browser
    }

    fun addMessageHandler(myJSQueryOpenInBrowser: JBCefJSQuery) {
        myJSQueryOpenInBrowser.addHandler { msg ->
            val event = Events.parse(msg)

            if (event != null) {
                messageHandler(event)
            }
            null
        }
    }

    fun setUpReact(browser: CefBrowser) {
        val config = this.editor.getUserConfig()
        val configJson = Gson().toJson(config)
        this.editor.getActiveFileInfo { file ->
            val fileJson = Gson().toJson(file)
            this.editor.getSelectedSnippet { snippet ->
                val snippetJson = if(snippet != null) Gson().toJson(snippet) else "undefined";
                val script = """
                    const config = ${configJson};
                    const active_file = ${fileJson};
                    const selected_snippet = ${snippetJson};
                    window.__INITIAL_STATE__ = { config, active_file, selected_snippet };
                    
                    function loadChatJs() {
                        const element = document.getElementById("refact-chat");
                        RefactChat.render(element, config);
                    };
                    
                    const script = document.createElement("script");
                    script.onload = loadChatJs;
                    script.src = "http://refactai/dist/chat/index.umd.cjs";
                    document.head.appendChild(script);
                    """.trimIndent()
                println(script)
                browser.executeJavaScript(script, browser.url, 0)
            }
        }
    }

    fun setUpJavaScriptMessageBusRedirectHyperlink(browser: CefBrowser?, myJSQueryOpenInBrowser: JBCefJSQuery) {
        val script = """window.openLink = function(href) {
             ${myJSQueryOpenInBrowser.inject("href")}
        }
        document.addEventListener('click', function(event) { 
            if (event.target.tagName.toLowerCase() === 'a') { 
                event.preventDefault(); 
                window.openLink(event.target.href); 
            } 
        });""".trimIndent()
        browser?.executeJavaScript(script, browser.url, 0)
    }

    fun setUpJavaScriptMessageBus(browser: CefBrowser?, myJSQueryOpenInBrowser: JBCefJSQuery): Boolean {

        val script = """window.postIntellijMessage = function(event) {
             const msg = JSON.stringify(event);
             ${myJSQueryOpenInBrowser.inject("msg")}
        }""".trimIndent()
        if (browser != null) {
            browser.executeJavaScript(script, browser.url, 0);
            return true
        }
        return false
    }

    fun postMessage(message: Events.ToChat<*>?) {
        if (message != null) {
            val json = Events.stringify(message)
            // println("post message json: $json")
            this.postMessage(json)
        }
    }

    fun postMessage(message: String) {
        // println("postMessage: $message")
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {
        return webView.component
    }

    override fun dispose() {
        this.webView.dispose()
    }


}