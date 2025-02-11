package com.smallcloud.refactai.panes.sharedchat.browser

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.ui.jcef.executeJavaScript
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.panes.sharedchat.Editor
import com.smallcloud.refactai.panes.sharedchat.Events
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.callback.CefFileDialogCallback
import org.cef.handler.*
import java.util.*
import java.util.concurrent.atomic.AtomicReference
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
    private val logger = Logger.getInstance(ChatWebView::class.java)

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    fun setStyle() {
        val isDarkMode = LafManager.getInstance().currentUIThemeLookAndFeel.isDark

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
        val webView = this.webView
        println("bodyClass: ${bodyClass}")
        CoroutineScope(Dispatchers.Default).launch {
            webView.executeJavaScript("""document.body.style.setProperty("background-color", "rgb($red, $green, $blue");""")
            webView.executeJavaScript("""document.body.className = "$bodyClass $mode";""")
            println("updated dom")
            webView.component.repaint()
        }
    }

    fun showFileChooserDialog(project: Project?, title: String?, isMultiple: Boolean, filters: Vector<String>): String {
        val filePath: AtomicReference<String> = AtomicReference("")
        ApplicationManager.getApplication().invokeAndWait {
            var fileChooserDescriptor =
                FileChooserDescriptor(true, false, false, false, false, false)
            fileChooserDescriptor.title = if (title.isNullOrEmpty() || title.isBlank()) "Choose File" else title
            fileChooserDescriptor =
                fileChooserDescriptor.withFileFilter { file -> filters.any { filter -> file.name.endsWith(filter) } }
            val file = FileChooser.chooseFile(fileChooserDescriptor, project, null)
            if (file != null) {
                filePath.set(file.canonicalPath)
            }
        }
        return filePath.get()
    }


    val webView by lazy {
        val isOSREnable = when {
            SystemInfo.isWindows -> false
            SystemInfo.isMac -> false
            SystemInfo.isLinux -> true
            else -> false
        }
        val browser = JBCefBrowser
            .createBuilder()
            .setEnableOpenDevToolsMenuItem(true)
            .setUrl("http://refactai/index.html")
            // change this to enable dev tools
            // setting to false prevents "Accept diff with tab": fixed with onTabHandler
            // setting to true causes slow scroll issues :/
            .setOffScreenRendering(isOSREnable)
            .build()


        if (!isOSREnable) {
            val onTabHandler: CefKeyboardHandler = object : CefKeyboardHandlerAdapter() {
                override fun onKeyEvent(browser: CefBrowser?, event: CefKeyboardHandler.CefKeyEvent?): Boolean {
                    val wasTabPressed =
                        event?.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_KEYUP && event.modifiers == 0 && event.character == '\t';
                    val currentEditor = FileEditorManager.getInstance(editor.project).selectedTextEditor
                    val isInDiffMode =
                        currentEditor != null && ModeProvider.getOrCreateModeProvider(currentEditor).isDiffMode()

                    if (wasTabPressed && currentEditor != null && isInDiffMode) {
                        ApplicationManager.getApplication().invokeLater {
                            ModeProvider.getOrCreateModeProvider(currentEditor)
                                .onTabPressed(currentEditor, null, DataContext.EMPTY_CONTEXT)
                        }
                        return false
                    }
                    return super.onKeyEvent(browser, event)
                }
            }

            browser.jbCefClient.addKeyboardHandler(onTabHandler, browser.cefBrowser)
        }

        if (System.getenv("REFACT_DEBUG") != "1") {
            browser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        }

        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            private fun logSeverityToString(severity: CefSettings.LogSeverity?): String {
                return when (severity) {
                    CefSettings.LogSeverity.LOGSEVERITY_DEFAULT -> "DEFAULT"
                    CefSettings.LogSeverity.LOGSEVERITY_VERBOSE -> "VERBOSE"
                    CefSettings.LogSeverity.LOGSEVERITY_INFO -> "INFO"
                    CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "WARNING"
                    CefSettings.LogSeverity.LOGSEVERITY_ERROR -> "ERROR"
                    CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "FATAL"
                    else -> "UNKNOWN"
                }
            }

            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                logger.warn("CONSOLE: ${logSeverityToString(level)} $message $source $line")
                return super.onConsoleMessage(browser, level, message, source, line)
            }
        }, browser.cefBrowser)
        if (SystemInfo.isLinux) {
            browser.jbCefClient.addDialogHandler({ cefBrowser, mode, title, defaultFilePath, filters, callback ->
                val filePath = showFileChooserDialog(
                    editor.project,
                    title,
                    mode == CefDialogHandler.FileDialogMode.FILE_DIALOG_OPEN_MULTIPLE,
                    filters
                )
                if (filePath.isNotEmpty()) {
                    callback.Continue(Vector(listOf(filePath)))
                } else {
                    callback.Cancel()
                }
                true
            }, browser.cefBrowser)
        }

        CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        addMessageHandler(myJSQueryOpenInBrowser)

        val myJSQueryOpenInBrowserRedirectHyperlink = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        myJSQueryOpenInBrowserRedirectHyperlink.addHandler { href ->
            if (href.isNotEmpty() && !href.contains("#") && !href.equals("http://refactai/index.html")) {
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
                val snippetJson = if (snippet != null) Gson().toJson(snippet) else "undefined";
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
            logger.info("post message json: $json")
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