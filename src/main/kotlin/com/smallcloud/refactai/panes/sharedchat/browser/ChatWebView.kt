package com.smallcloud.refactai.panes.sharedchat.browser

import com.google.gson.Gson
import com.intellij.ide.BrowserUtil
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
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.panes.sharedchat.Editor
import com.smallcloud.refactai.panes.sharedchat.Events
import com.intellij.ide.ui.LafManager
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.utils.CefLifecycleManager
import com.smallcloud.refactai.utils.JSQueryManager
import com.smallcloud.refactai.utils.AsyncMessageHandler
import com.smallcloud.refactai.utils.OSRRenderer
import com.smallcloud.refactai.utils.JavaScriptExecutor
import com.intellij.ui.jcef.JBCefApp
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JComponent
import javax.swing.Timer


fun getActionKeybinding(actionId: String): String {
    val keymapManager = KeymapManager.getInstance()
    val shortcuts = keymapManager.activeKeymap.getShortcuts(actionId)
    return if (shortcuts.isNotEmpty()) KeymapUtil.getShortcutText(shortcuts[0]) else ""
}

class ChatWebView(val editor: Editor, val messageHandler: (event: Events.FromChat) -> Unit) : Disposable {
    private val logger = Logger.getInstance(ChatWebView::class.java)

    private val initializationState = AtomicInteger(0) // 0=not loaded, 1=page loaded, 2=React ready
    private val browserHealthy = AtomicBoolean(true)
    private var healthCheckTimer: Timer? = null
    private var setupDelayTimer: Timer? = null
    private var lastPingResponse = AtomicLong(System.currentTimeMillis())
    private lateinit var pingQuery: JBCefJSQuery
    private lateinit var readyQuery: JBCefJSQuery

    companion object {
        private val schemeHandlerRegistered = AtomicBoolean(false)
        private val logger = Logger.getInstance(ChatWebView::class.java)

        fun isSupported(): Boolean = JBCefApp.isSupported()

        /**
         * Registers scheme handler for http://refactai/ URLs.
         * NOTE: This is a global, permanent registration that survives plugin reload.
         * If plugin is dynamically unloaded, the handler may keep the old classloader alive.
         * This is a known limitation of CefApp.registerSchemeHandlerFactory.
         */
        private fun registerSchemeHandlerOnce() {
            if (!schemeHandlerRegistered.compareAndSet(false, true)) return
            try {
                CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())
                logger.info("Registered scheme handler for http://refactai/")
            } catch (e: Exception) {
                logger.warn("Failed to register scheme handler: ${e.message}")
                schemeHandlerRegistered.set(false)
            }
        }
    }

    private val jsQueryManager: JSQueryManager
    private val asyncMessageHandler: AsyncMessageHandler<Events.FromChat>
    private val jsExecutor: JavaScriptExecutor
    private var osrRenderer: OSRRenderer? = null

    private lateinit var mainQuery: JBCefJSQuery
    private lateinit var linkQuery: JBCefJSQuery

    private val cefBrowser: CefBrowser
    private val jbcefBrowser: JBCefBrowser
    private val component: JComponent
    private val useOffscreenRendering = SystemInfo.isLinux

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

            jsExecutor.executeAsync(
                """
                document.body.style.setProperty("background-color", "rgb($red, $green, $blue)");
                document.body.className = "$bodyClass $mode";
                """.trimIndent(),
                "set-style"
            )
        } catch (e: Exception) {
            logger.warn("Error setting style: ${e.message}", e)
        }
    }

    fun showFileChooserDialog(project: Project?, title: String?, filters: Vector<String>): String {
        val filePath: AtomicReference<String> = AtomicReference("")
        val action = Runnable {
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
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            action.run()
        } else {
            app.invokeAndWait(action)
        }
        return filePath.get()
    }

    init {
        logger.info("Initializing ChatWebView, OSR=$useOffscreenRendering")

        try {
            jbcefBrowser = JBCefBrowser.createBuilder()
                .setEnableOpenDevToolsMenuItem(true)
                .setUrl("http://refactai/index.html")
                .setOffScreenRendering(useOffscreenRendering)
                .build()

            cefBrowser = jbcefBrowser.cefBrowser
            CefLifecycleManager.registerBrowser(cefBrowser)

            jsQueryManager = JSQueryManager(jbcefBrowser)
            asyncMessageHandler = AsyncMessageHandler(Events::parse, messageHandler)
            jsExecutor = JavaScriptExecutor(jbcefBrowser, timeoutMs = 5000L, poolSize = 3)

            component = setupPlatformSpecificFeatures()
            registerSchemeHandlerOnce()
            setupJavaScriptQueries()
            setupLoadHandler()
            jbcefBrowser.createImmediately()

        } catch (e: Exception) {
            logger.error("Failed to initialize ChatWebView", e)
            dispose()
            throw e
        }
    }

    private fun setupPlatformSpecificFeatures(): JComponent {
        val resultComponent = if (useOffscreenRendering) {
            osrRenderer = OSRRenderer(targetFps = 30)
            val browserComponent = jbcefBrowser.component
            osrRenderer!!.attach(browserComponent)
            browserComponent
        } else {
            setupNativeRenderingFeatures()
            jbcefBrowser.component
        }

        setupCommonBrowserFeatures()
        return resultComponent
    }

    private fun setupNativeRenderingFeatures() {
        val onTabHandler: CefKeyboardHandler = object : CefKeyboardHandlerAdapter() {
            override fun onKeyEvent(browser: CefBrowser?, event: CefKeyboardHandler.CefKeyEvent?): Boolean {
                val wasTabPressed = event?.type == CefKeyboardHandler.CefKeyEvent.EventType.KEYEVENT_KEYUP &&
                                   event.modifiers == 0 && event.character == '\t'
                val currentEditor = FileEditorManager.getInstance(editor.project).selectedTextEditor
                val isInDiffMode = currentEditor != null &&
                                  ModeProvider.getOrCreateModeProvider(currentEditor).isDiffMode()

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

        jbcefBrowser.jbCefClient.addKeyboardHandler(onTabHandler, cefBrowser)
    }

    private fun setupCommonBrowserFeatures() {
        if (System.getenv("REFACT_DEBUG") != "1") {
            jbcefBrowser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        }

        jbcefBrowser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                val levelStr = when (level) {
                    CefSettings.LogSeverity.LOGSEVERITY_ERROR, CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "ERROR"
                    CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "WARNING"
                    else -> "INFO"
                }
                logger.info("BROWSER_CONSOLE[$levelStr]: $message ($source:$line)")
                return super.onConsoleMessage(browser, level, message, source, line)
            }

            override fun onCursorChange(browser: CefBrowser?, cursorType: Int): Boolean {
                return super.onCursorChange(browser, cursorType)
            }
        }, cefBrowser)

        if (SystemInfo.isLinux) {
            jbcefBrowser.jbCefClient.addDialogHandler({ _, _, title, _, filters, callback ->
                val filePath = showFileChooserDialog(
                    editor.project,
                    title,
                    filters
                )
                if (filePath.isNotEmpty()) {
                    callback.Continue(Vector(listOf(filePath)))
                } else {
                    callback.Cancel()
                }
                true
            }, cefBrowser)
        }

        setupFocusRecovery()
        setupHealthCheck()
    }

    private fun setupFocusRecovery() {
        component.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                checkBrowserHealth()
            }
        })
    }

    private var unhealthyCount = AtomicInteger(0)
    private val maxUnhealthyBeforeRecovery = 3

    private fun setupHealthCheck() {
        healthCheckTimer = Timer(30000, null).apply {
            isRepeats = true
            addActionListener {
                if (initializationState.get() >= 2 && !jbcefBrowser.isDisposed) {
                    checkBrowserHealth()
                }
            }
            start()
        }
    }

    private fun checkBrowserHealth() {
        if (jbcefBrowser.isDisposed) return

        try {
            val timeSinceLastPing = System.currentTimeMillis() - lastPingResponse.get()
            if (timeSinceLastPing > 60000) {
                val count = unhealthyCount.incrementAndGet()
                browserHealthy.set(false)
                logger.warn("Browser unresponsive for ${timeSinceLastPing}ms (unhealthy count: $count)")

                if (count >= maxUnhealthyBeforeRecovery) {
                    attemptRecovery()
                }
                return
            }

            // Reset unhealthy count on successful response window
            if (timeSinceLastPing < 35000) {
                unhealthyCount.set(0)
            }

            jsExecutor.executeJavaScript(
                "try { ${pingQuery.inject("'pong'")} } catch(e) { console.error('ping failed', e); }",
                "health-ping"
            )
        } catch (e: Exception) {
            logger.warn("Health check error", e)
            browserHealthy.set(false)
            unhealthyCount.incrementAndGet()
        }
    }

    private fun attemptRecovery() {
        logger.warn("Attempting browser recovery after $maxUnhealthyBeforeRecovery unhealthy checks")
        unhealthyCount.set(0)

        try {
            // Try to recover by reloading the page
            ApplicationManager.getApplication().invokeLater {
                if (!jbcefBrowser.isDisposed) {
                    logger.info("Reloading browser to recover from unhealthy state")
                    initializationState.set(0)
                    jbcefBrowser.cefBrowser.reload()
                    lastPingResponse.set(System.currentTimeMillis())
                    browserHealthy.set(true)
                }
            }
        } catch (e: Exception) {
            logger.error("Recovery attempt failed", e)
        }
    }

    fun isBrowserHealthy(): Boolean = browserHealthy.get()

    private fun setupJavaScriptQueries() {
        mainQuery = jsQueryManager.createStringQuery { message ->
            if (!asyncMessageHandler.offerMessage(message)) {
                logger.warn("Failed to queue message")
            }
        }

        linkQuery = jsQueryManager.createStringQuery { href ->
            if (href.isNotEmpty() && !href.contains("#") && href != "http://refactai/index.html") {
                ApplicationManager.getApplication().invokeLater {
                    BrowserUtil.browse(href)
                }
            }
        }

        pingQuery = jsQueryManager.createStringQuery {
            lastPingResponse.set(System.currentTimeMillis())
            browserHealthy.set(true)
        }

        readyQuery = jsQueryManager.createStringQuery { message ->
            if (message == "ready") {
                val previousState = initializationState.getAndSet(2)
                if (previousState < 2) {
                    logger.info("React application signaled ready")
                }
            }
        }
    }

    private fun setupLoadHandler() {
        jbcefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (isLoading) {
                    // Reset state when a new page load starts
                    val previousState = initializationState.getAndSet(0)
                    if (previousState > 0) {
                        logger.info("Page reload detected, resetting initialization state from $previousState to 0")
                    }
                    // Cancel any pending setup timer
                    setupDelayTimer?.stop()
                    setupDelayTimer = null
                    return
                }

                logger.info("Page loading completed, current state: ${initializationState.get()}")

                if (initializationState.compareAndSet(0, 1)) {
                    logger.info("Page loaded, scheduling React setup")
                    // Cancel any existing timer to prevent duplicates
                    setupDelayTimer?.stop()
                    setupDelayTimer = Timer(100, null).apply {
                        isRepeats = false
                        addActionListener {
                            if (initializationState.get() == 1) {
                                logger.info("Setting up React application")
                                setupReactApplication()
                            }
                        }
                        start()
                    }
                }

                ApplicationManager.getApplication().invokeLater {
                    setStyle()
                }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                logger.info("Load end event - HTTP status: $httpStatusCode, state: ${initializationState.get()}")

                // Setup React if still at state 1 (page loaded but React not setup yet)
                if (initializationState.get() == 1) {
                    logger.info("Triggering React setup from onLoadEnd")
                    setupReactApplication()
                }
            }
        }, cefBrowser)
    }

    private fun setupReactApplication() {
        logger.info("Starting React application setup")

        try {
            val config = editor.getUserConfig()
            val configJson = Gson().toJson(config)
            val currentProject = """{ name: "${editor.project.name}" }"""

            logger.info("Got user config: $configJson")

            editor.getActiveFileInfo { file ->
                val fileJson = Gson().toJson(file)
                logger.info("Got active file info: $fileJson")

                editor.getSelectedSnippet { snippet ->
                    val snippetJson = if (snippet != null) Gson().toJson(snippet) else "undefined"
                    logger.info("Got selected snippet: $snippetJson")

                    val scripts = listOf(
                        """
                        if (window.__REFACT_BRIDGE_INSTALLED__) {
                            console.log('Bridge already installed, skipping');
                        } else {
                            window.__REFACT_BRIDGE_INSTALLED__ = true;
                            const config = $configJson;
                            const active_file = $fileJson;
                            const selected_snippet = $snippetJson;
                            const current_project = $currentProject;
                            window.__INITIAL_STATE__ = { config, active_file, selected_snippet, current_project };
                        }
                        """.trimIndent(),

                        """
                        if (window.__REFACT_BRIDGE_INSTALLED__) {
                            const config = window.__INITIAL_STATE__?.config || {};
                            if (config.themeProps && config.themeProps.appearance === "dark") {
                                document.body.className = "vscode-dark dark";
                            } else if (config.themeProps && config.themeProps.appearance === "light") {
                                document.body.className = "vscode-light light";
                            }
                        }
                        """.trimIndent(),

                        """
                        if (!window.__REFACT_MESSAGE_LISTENER__) {
                            window.__REFACT_MESSAGE_LISTENER__ = true;
                            window.addEventListener('message', function(event) {
                                try {
                                    let messageData = typeof event.data === 'string' 
                                        ? event.data 
                                        : JSON.stringify(event.data);
                                    ${mainQuery.inject("messageData")};
                                } catch (e) {
                                    console.error('Error processing message:', e);
                                }
                            });
                            window.ideMessageHandler = function(message) {
                                ${mainQuery.inject("message")};
                            };
                            window.ideLinkHandler = function(href) {
                                ${linkQuery.inject("href")};
                            };
                        }
                        """.trimIndent(),

                        // Chat script loading with ready handshake
                        """
                        function loadChatJs() {
                            const element = document.getElementById("refact-chat");
                            if (typeof RefactChat !== 'undefined') {
                                RefactChat.render(element, config);
                                console.log('RefactChat initialized successfully');
                                try { ${readyQuery.inject("'ready'")} } catch(e) { console.error('Ready signal failed', e); }
                            } else {
                                console.error('RefactChat not available');
                            }
                        }

                        const script = document.createElement("script");
                        script.onload = loadChatJs;
                        script.onerror = function(e) {
                            console.error('Failed to load chat script:', e);
                        };
                        script.src = "http://refactai/dist/chat/index.umd.cjs";
                        document.head.appendChild(script);
                        """.trimIndent()
                    )

                    logger.info("Executing React setup scripts")
                    jsExecutor.executeBatch(scripts, "react-setup").exceptionally { throwable ->
                        logger.error("Failed to setup React application", throwable)
                        null
                    }
                }
            }
        } catch (e: Exception) {
            logger.error("Error in setupReactApplication", e)
        }
    }

    fun getComponent(): JComponent {
        return component
    }

    // Expose webView for backward compatibility with tests
    val webView: JBCefBrowser
        get() = jbcefBrowser

    fun postMessage(message: Events.ToChat<*>?) {
        if (message == null) return

        // Check if browser is initialized and healthy
        if (initializationState.get() < 2) {
            logger.warn("Attempted to post message before browser initialization complete")
            return
        }

        if (jbcefBrowser.isDisposed) {
            logger.warn("Attempted to post message to disposed browser")
            return
        }

        val json = Events.stringify(message)
        postMessageString(json)
    }

    private fun postMessageString(message: String) {
        // Use template for better performance with repeated postMessage calls
        val template = jsExecutor.createTemplate("window.postMessage(%s, '*');")
        template.execute(message, description = "post-message")
    }

    override fun dispose() {
        logger.info("Disposing ChatWebView")

        try {
            // Stop timers first (safe on any thread)
            healthCheckTimer?.stop()
            healthCheckTimer = null
            setupDelayTimer?.stop()
            setupDelayTimer = null

            // Clean up OSR renderer (removes listeners)
            osrRenderer?.cleanup()
            osrRenderer = null

            // Dispose resource managers - these are thread-safe
            asyncMessageHandler.dispose()
            jsQueryManager.dispose()

            // JS executor disposal may wait - do it without blocking EDT
            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) {
                app.executeOnPooledThread {
                    disposeJsExecutorAndBrowser()
                }
            } else {
                disposeJsExecutorAndBrowser()
            }

            logger.info("ChatWebView disposal initiated")

        } catch (e: Exception) {
            logger.error("Error during ChatWebView disposal", e)
        }
    }

    private fun disposeJsExecutorAndBrowser() {
        try {
            jsExecutor.dispose()

            try {
                CefLifecycleManager.releaseBrowser(cefBrowser)
            } catch (e: Exception) {
                logger.warn("Failed to release browser through lifecycle manager", e)
                if (!jbcefBrowser.isDisposed) {
                    jbcefBrowser.dispose()
                }
            }

            logger.info("ChatWebView disposal completed")
        } catch (e: Exception) {
            logger.error("Error in background disposal", e)
        }
    }
}
