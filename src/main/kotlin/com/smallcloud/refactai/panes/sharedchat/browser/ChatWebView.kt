package com.smallcloud.refactai.panes.sharedchat.browser

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
import com.intellij.util.ui.UIUtil
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.util.concurrent.atomic.AtomicBoolean
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.panes.sharedchat.Editor
import com.smallcloud.refactai.panes.sharedchat.Events
import com.smallcloud.refactai.utils.safeExecuteJavaScript
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.*
import java.awt.Cursor
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

    // Modal dialog state management
    private val isModalActive = AtomicBoolean(false)
    private val isTourGuideActive = AtomicBoolean(false)
    private var pendingContextMenuCleanup: (() -> Unit)? = null

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
        setupModalDialogDetection()
    }

    fun setStyle() {
        try {
            // Safely get the theme information
            val lafManager = LafManager.getInstance()
            val theme = lafManager?.currentUIThemeLookAndFeel
            val isDarkMode = theme?.isDark ?: false

            val mode = if (isDarkMode) "dark" else "light"
            val bodyClass = if (isDarkMode) "vscode-dark" else "vscode-light"
            
            val backgroundColour = UIUtil.getPanelBackground()
            val red = backgroundColour.red
            val green = backgroundColour.green
            val blue = backgroundColour.blue

            logger.info("Setting style: bodyClass=$bodyClass, mode=$mode")

            executeJavaScript(
                """
                document.body.style.setProperty("background-color", "rgb($red, $green, $blue)");
                document.body.className = "$bodyClass $mode";
                """.trimIndent(),
                true
            )

        } catch (e: Exception) {
            logger.warn("Error setting style: ${e.message}", e)
        }
    }

    /**
     * Sets up modal dialog detection to handle browser interactions properly
     */
    private fun setupModalDialogDetection() {
        try {
            // Monitor application modality state
            ApplicationManager.getApplication().invokeLater({
                detectModalDialogState()
            }, ModalityState.any())
        } catch (e: Exception) {
            logger.warn("Error setting up modal dialog detection: ${e.message}", e)
        }
    }

    /**
     * Detects if a modal dialog is currently active
     */
    private fun detectModalDialogState() {
        try {
            val currentModalityState = ModalityState.current()
            val wasModalActive = isModalActive.get()
            val isCurrentlyModalActive = !currentModalityState.equals(ModalityState.nonModal())

            if (wasModalActive != isCurrentlyModalActive) {
                isModalActive.set(isCurrentlyModalActive)
                handleModalStateChange(isCurrentlyModalActive)
            }

            // Continue monitoring if not disposed
            if (!webView.isDisposed) {
                ApplicationManager.getApplication().invokeLater({
                    detectModalDialogState()
                }, ModalityState.any())
            }
        } catch (e: Exception) {
            logger.warn("Error detecting modal dialog state: ${e.message}", e)
        }
    }

    /**
     * Handles modal dialog state changes
     */
    private fun handleModalStateChange(modalActive: Boolean) {
        try {
            if (modalActive) {
                logger.info("Modal dialog detected - preparing browser for modal state")
                onModalDialogOpened()
            } else {
                logger.info("Modal dialog closed - restoring browser state")
                onModalDialogClosed()
            }
        } catch (e: Exception) {
            logger.warn("Error handling modal state change: ${e.message}", e)
        }
    }

    /**
     * Called when a modal dialog is opened
     */
    private fun onModalDialogOpened() {
        try {
            // Clean up any existing context menus or dropdowns
            cleanupContextMenus()

            // Disable browser interactions temporarily
            disableBrowserInteractions()
        } catch (e: Exception) {
            logger.warn("Error handling modal dialog opened: ${e.message}", e)
        }
    }

    /**
     * Called when a modal dialog is closed
     */
    private fun onModalDialogClosed() {
        try {
            // Restore browser interactions
            enableBrowserInteractions()

            // Execute any pending cleanup
            pendingContextMenuCleanup?.invoke()
            pendingContextMenuCleanup = null
        } catch (e: Exception) {
            logger.warn("Error handling modal dialog closed: ${e.message}", e)
        }
    }

    /**
     * Cleans up any stuck context menus or dropdowns
     */
    private fun cleanupContextMenus() {
        try {
            val script = """
                // Close any open context menus or dropdowns
                if (document.activeElement) {
                    document.activeElement.blur();
                }

                // Close any modal dialogs in the browser content
                const modals = document.querySelectorAll('[role="dialog"], .modal, .dropdown-menu');
                modals.forEach(modal => {
                    if (modal.style.display !== 'none') {
                        modal.style.display = 'none';
                    }
                });

                // Dispatch escape key to close any remaining dropdowns
                document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape' }));
            """.trimIndent()

            executeJavaScript(script)
        } catch (e: Exception) {
            logger.warn("Error cleaning up context menus: ${e.message}", e)
        }
    }

    /**
     * Temporarily disables browser interactions
     */
    private fun disableBrowserInteractions() {
        try {
            val script = """
                // Create overlay to prevent interactions
                if (!document.getElementById('modal-overlay')) {
                    const overlay = document.createElement('div');
                    overlay.id = 'modal-overlay';
                    overlay.style.cssText = 'position: fixed; top: 0; left: 0; width: 100%; height: 100%; z-index: 99999; pointer-events: auto; background: transparent;';
                    document.body.appendChild(overlay);
                }
            """.trimIndent()

            executeJavaScript(script)
        } catch (e: Exception) {
            logger.warn("Error disabling browser interactions: ${e.message}", e)
        }
    }

    /**
     * Re-enables browser interactions
     */
    private fun enableBrowserInteractions() {
        try {
            val script = """
                // Remove interaction overlay
                const overlay = document.getElementById('modal-overlay');
                if (overlay) {
                    overlay.remove();
                }
            """.trimIndent()

            executeJavaScript(script)
        } catch (e: Exception) {
            logger.warn("Error enabling browser interactions: ${e.message}", e)
        }
    }

    /**
     * Detects if tour guide with GIF is active
     */
    fun setTourGuideActive(active: Boolean) {
        isTourGuideActive.set(active)
        if (active) {
            logger.info("Tour guide with GIF detected - enhanced modal detection active")
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

        // Add focus listener to detect tour guide interactions
        browser.component.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {
                if (isModalActive.get()) {
                    logger.debug("Focus gained while modal active - checking for tour guide")
                    checkForTourGuideWithGif()
                }
            }

            override fun focusLost(e: FocusEvent?) {
                if (isModalActive.get() && isTourGuideActive.get()) {
                    logger.debug("Focus lost during modal with tour guide - scheduling cleanup")
                    scheduleContextMenuCleanup()
                }
            }
        })

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

            override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
                val cursor = Cursor.getPredefinedCursor(cursorType)
                browser?.uiComponent?.cursor = cursor
                return super.onCursorChange(browser, cursorType)
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
            logger.warn("msg = ${msg}")
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
        val currentProject = """{name: "${editor.project.name}"}"""
        this.editor.getActiveFileInfo { file ->
            val fileJson = Gson().toJson(file)
            this.editor.getSelectedSnippet { snippet ->
                val snippetJson = if (snippet != null) Gson().toJson(snippet) else "undefined";
                val script = """
                    const config = ${configJson};
                    const active_file = ${fileJson};
                    const selected_snippet = ${snippetJson};
                    const current_project = ${currentProject};
                    window.__INITIAL_STATE__ = { config, active_file, selected_snippet, current_project };
                    
                    function loadChatJs() {
                        const element = document.getElementById("refact-chat");
                        console.log(RefactChat);
                        RefactChat.render(element, config);
                    };
                    
                    if(config.themeProps.appearance === "dark") {
                      document.body.className = "vscode-dark";
                    } else if (config.themeProps.appearance === "light") {
                      document.body.className = "vscode-light";
                    }
                                       
                    const script = document.createElement("script");
                    script.onload = loadChatJs;
                    script.src = "http://refactai/dist/chat/index.umd.cjs";
                    document.head.appendChild(script);
                    """.trimIndent()
                logger.info("Setting up React")
                try {
                    executeJavaScript(script)
                    // browser.executeJavaScript(script, browser.url, 0)
                } catch (e: Exception) {
                    logger.warn("Error setting up React: ${e.message}", e)
                }
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
        
        try {
            if (browser != null) {
                executeJavaScript(script)
            } else {
                logger.warn("Cannot set up JavaScript message bus redirect hyperlink: browser is null")
            }
        } catch (e: Exception) {
            logger.warn("Error setting up JavaScript message bus redirect hyperlink: ${e.message}", e)
        }
    }

    fun setUpJavaScriptMessageBus(browser: CefBrowser?, myJSQueryOpenInBrowser: JBCefJSQuery): Boolean {
        val script = """window.postIntellijMessage = function(event) {
             const msg = JSON.stringify(event);
             ${myJSQueryOpenInBrowser.inject("msg")}
        }""".trimIndent()

        try {
            executeJavaScript(script)
            return true
        } catch (e: Exception) {
            logger.warn("Error setting up JavaScript message bus: ${e.message}", e)
        }
        return false
    }

    fun postMessage(message: Events.ToChat<*>?) {
        if (message != null) {
            val json = Events.stringify(message)
//            logger.info("post message json: $json")
            this.postMessage(json)
        }
    }

    fun postMessage(message: String) {
//        logger.info("Posting message to browser")
        val script = """window.postMessage($message, "*");"""
        executeJavaScript(script)
    }

    fun getComponent(): JComponent {
        return webView.component
    }

    private fun executeJavaScript(script: String, repaint: Boolean = false) {
        safeExecuteJavaScript(webView, script, repaint)
    }

    /**
     * Checks for tour guide with GIF elements in the browser
     */
    private fun checkForTourGuideWithGif() {
        try {
            val script = """
                // Check for tour guide elements with GIF content
                const tourElements = document.querySelectorAll('[class*="tour"], [class*="guide"], [class*="walkthrough"], [data-tour]');
                let hasGif = false;

                tourElements.forEach(element => {
                    const gifs = element.querySelectorAll('img[src*=".gif"], video, [style*="gif"]');
                    if (gifs.length > 0) {
                        hasGif = true;
                    }
                });

                // Return result to Java
                window.postIntellijMessage({
                    type: 'ide/tourGuideState',
                    payload: { hasGif: hasGif, tourActive: tourElements.length > 0 }
                });
            """.trimIndent()

            executeJavaScript(script)
        } catch (e: Exception) {
            logger.warn("Error checking for tour guide with GIF: ${e.message}", e)
        }
    }

    /**
     * Schedules context menu cleanup for later execution
     */
    private fun scheduleContextMenuCleanup() {
        pendingContextMenuCleanup = {
            ApplicationManager.getApplication().invokeLater({
                cleanupContextMenus()
            }, ModalityState.nonModal())
        }
    }

    override fun dispose() {
        try {
            logger.info("Disposing ChatWebView")

            // Clean up modal state detection
            isModalActive.set(false)
            isTourGuideActive.set(false)
            pendingContextMenuCleanup = null

            if (!webView.isDisposed) {
                webView.dispose()
            }
        } catch (e: Exception) {
            logger.warn("Error disposing ChatWebView: ${e.message}", e)
        }
    }


}