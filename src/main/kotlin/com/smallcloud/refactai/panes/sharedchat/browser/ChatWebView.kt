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
import com.intellij.ui.jcef.JBCefClient
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
import com.smallcloud.refactai.utils.JavaScriptTemplate
import com.intellij.ide.util.PropertiesComponent
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

    private val initializationState = AtomicInteger(0) // 0=not loaded, 1=page loaded, 2=setup in progress, 3=React ready
    private val browserHealthy = AtomicBoolean(true)
    private var healthCheckTimer: Timer? = null
    private var setupDelayTimer: Timer? = null
    private var lastPingResponse = AtomicLong(System.currentTimeMillis())
    private lateinit var pingQuery: JBCefJSQuery
    private lateinit var readyQuery: JBCefJSQuery

    companion object {
        private val schemeHandlerRegistered = AtomicBoolean(false)
        private val companionLogger = Logger.getInstance(ChatWebView::class.java)

        private const val JS_QUERY_POOL_SIZE = 200
        private const val PREF_KEY_RENDERING_MODE = "refact.jcef.rendering.mode"
        private const val PREF_KEY_CRASH_COUNT = "refact.jcef.crash.count"
        private const val PREF_KEY_LAST_CRASH_TIME = "refact.jcef.last.crash.time"
        private const val CRASH_THRESHOLD = 3
        private const val CRASH_WINDOW_MS = 3600000L

        private val lastInitError = AtomicReference<String?>(null)
        private val sessionCrashCount = AtomicInteger(0)
        private val osrFallbackTriggered = AtomicBoolean(false)

        fun isSupported(): Boolean = JBCefApp.isSupported()

        fun getLastInitError(): String? = lastInitError.get()
        fun clearLastInitError() = lastInitError.set(null)

        fun reportCrash(): Boolean {
            val props = PropertiesComponent.getInstance()
            val now = System.currentTimeMillis()

            // Check if we should reset crash count (outside crash window)
            val lastCrashTime = props.getLong(PREF_KEY_LAST_CRASH_TIME, 0L)
            if (now - lastCrashTime > CRASH_WINDOW_MS) {
                props.setValue(PREF_KEY_CRASH_COUNT, "0")
            }

            // Increment crash count
            val crashCount = props.getInt(PREF_KEY_CRASH_COUNT, 0) + 1
            props.setValue(PREF_KEY_CRASH_COUNT, crashCount.toString())
            props.setValue(PREF_KEY_LAST_CRASH_TIME, now.toString())

            sessionCrashCount.incrementAndGet()

            companionLogger.warn("JCEF crash detected (session: ${sessionCrashCount.get()}, total: $crashCount)")

            // Switch to OSR if threshold exceeded
            if (crashCount >= CRASH_THRESHOLD && !osrFallbackTriggered.get()) {
                companionLogger.warn("Crash threshold exceeded ($crashCount >= $CRASH_THRESHOLD), switching to OSR mode")
                props.setValue(PREF_KEY_RENDERING_MODE, "osr")
                osrFallbackTriggered.set(true)
                return true
            }

            return false
        }

        fun reportStable() {
            val props = PropertiesComponent.getInstance()
            val crashCount = props.getInt(PREF_KEY_CRASH_COUNT, 0)
            if (crashCount > 0) {
                // Slowly decrease crash count when things are working
                props.setValue(PREF_KEY_CRASH_COUNT, (crashCount - 1).coerceAtLeast(0).toString())
            }
        }

        fun resetRenderingPreferences() {
            val props = PropertiesComponent.getInstance()
            props.setValue(PREF_KEY_RENDERING_MODE, "auto")
            props.setValue(PREF_KEY_CRASH_COUNT, "0")
            sessionCrashCount.set(0)
            osrFallbackTriggered.set(false)
            companionLogger.info("Rendering preferences reset to auto")
        }

        fun isOsrFallbackTriggered(): Boolean = osrFallbackTriggered.get()

        fun getRenderingModePreference(): String {
            return PropertiesComponent.getInstance().getValue(PREF_KEY_RENDERING_MODE, "auto")
        }

        private fun registerSchemeHandlerOnce() {
            if (!schemeHandlerRegistered.compareAndSet(false, true)) return
            try {
                CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())
                companionLogger.info("Registered scheme handler for http://refactai/")
            } catch (e: Exception) {
                companionLogger.warn("Failed to register scheme handler", e)
                schemeHandlerRegistered.set(false)
            }
        }

        fun determineRenderingMode(): Boolean {
            // Check explicit system property overrides first
            if (System.getProperty("refact.jcef.force-osr") == "true" ||
                System.getenv("REFACT_FORCE_OSR") == "1") {
                companionLogger.info("OSR forced via system property/env")
                return true
            }
            if (System.getProperty("refact.jcef.force-native") == "true" ||
                System.getenv("REFACT_FORCE_NATIVE") == "1") {
                companionLogger.info("Native rendering forced via system property/env")
                return false
            }

            // Windows/Mac: use native rendering (generally stable)
            if (SystemInfo.isWindows || SystemInfo.isMac) {
                companionLogger.info("Using native rendering on Windows/Mac")
                return false
            }

            // Linux: check persisted preference (from crash detection)
            val props = PropertiesComponent.getInstance()
            val savedMode = props.getValue(PREF_KEY_RENDERING_MODE, "auto")
            val crashCount = props.getInt(PREF_KEY_CRASH_COUNT, 0)

            return when (savedMode) {
                "osr" -> {
                    companionLogger.info("Using OSR mode (saved preference)")
                    true
                }
                "native" -> {
                    companionLogger.info("Using native mode (explicit preference)")
                    false
                }
                else -> {
                    companionLogger.info("Using OSR mode (Linux default)")
                    true
                }
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
    private val useOffscreenRendering: Boolean = determineRenderingMode()
    private lateinit var postMessageTemplate: JavaScriptTemplate

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
        logger.info("Initializing ChatWebView: OSR=$useOffscreenRendering, platform=${SystemInfo.OS_NAME}, " +
                "JBCefSupported=${JBCefApp.isSupported()}")

        try {
            // Clear any previous error state
            clearLastInitError()

            logger.info("Creating JBCefBrowser with OSR=$useOffscreenRendering")
            jbcefBrowser = JBCefBrowser.createBuilder()
                .setEnableOpenDevToolsMenuItem(true)
                .setOffScreenRendering(useOffscreenRendering)
                .build()

            cefBrowser = jbcefBrowser.cefBrowser
            CefLifecycleManager.registerBrowser(cefBrowser)
            logger.info("JBCefBrowser created successfully, cefBrowser=${cefBrowser.javaClass.simpleName}")

            jbcefBrowser.jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, JS_QUERY_POOL_SIZE)

            jsQueryManager = JSQueryManager(jbcefBrowser)
            asyncMessageHandler = AsyncMessageHandler(Events::parse, messageHandler)
            jsExecutor = JavaScriptExecutor(jbcefBrowser, timeoutMs = 5000L, poolSize = 3)
            postMessageTemplate = jsExecutor.createTemplate("window.postMessage(%s, '*');")

            component = setupPlatformSpecificFeatures()
            setupJavaScriptQueries()
            setupLoadHandler()
            registerSchemeHandlerOnce()
            jbcefBrowser.createImmediately()
            jbcefBrowser.loadURL("http://refactai/index.html")

            logger.info("ChatWebView initialization completed successfully")

        } catch (e: Exception) {
            val errorMsg = buildJcefErrorMessage(e)
            lastInitError.set(errorMsg)
            logger.error("Failed to initialize ChatWebView: $errorMsg", e)
            dispose()
            throw e
        }
    }

    private fun buildJcefErrorMessage(e: Exception): String {
        val cause = e.cause?.message ?: e.message ?: "Unknown error"
        return when {
            cause.contains("GPU", ignoreCase = true) ||
            cause.contains("SIGSEGV", ignoreCase = true) ||
            cause.contains("crash", ignoreCase = true) ->
                "JCEF browser crashed (GPU/rendering issue). " +
                "Try adding -Drefact.jcef.linux-osr=true to VM options, or " +
                "-Djcef.disable-gpu=true"

            cause.contains("disposed", ignoreCase = true) ->
                "Browser was disposed unexpectedly. Please restart the IDE."

            cause.contains("not supported", ignoreCase = true) ->
                "JCEF is not supported on this platform. " +
                "The Refact panel requires a JetBrains Runtime with JCEF support."

            else -> "JCEF initialization failed: $cause"
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

        setupCommonBrowserFeatures(resultComponent)
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

    private fun setupCommonBrowserFeatures(browserComponent: JComponent) {
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
                if (System.getenv("REFACT_DEBUG") == "1" || logger.isDebugEnabled) {
                    val levelStr = when (level) {
                        CefSettings.LogSeverity.LOGSEVERITY_ERROR, CefSettings.LogSeverity.LOGSEVERITY_FATAL -> "ERROR"
                        CefSettings.LogSeverity.LOGSEVERITY_WARNING -> "WARNING"
                        else -> "DEBUG"
                    }
                    logger.debug("BROWSER_CONSOLE[$levelStr]: $message ($source:$line)")
                }
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

        setupFocusRecovery(browserComponent)
        setupHealthCheck()
    }

    private fun setupFocusRecovery(browserComponent: JComponent) {
        browserComponent.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                checkBrowserHealth()
            }
        })
    }

    private var unhealthyCount = AtomicInteger(0)
    private val maxUnhealthyBeforeRecovery = 3
    private var recoveryAttempts = AtomicInteger(0)
    private val maxRecoveryAttempts = 2
    private var stableRunCount = AtomicInteger(0)
    private val stableThreshold = 10
    private var modeSwitchCallback: (() -> Unit)? = null

    fun setModeSwitchCallback(callback: () -> Unit) {
        modeSwitchCallback = callback
    }

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
                stableRunCount.set(0)
                logger.warn("Browser unresponsive for ${timeSinceLastPing}ms (unhealthy count: $count)")

                if (count >= maxUnhealthyBeforeRecovery) {
                    attemptRecovery()
                }
                return
            }

            // Reset unhealthy count on successful response window
            if (timeSinceLastPing < 35000) {
                unhealthyCount.set(0)

                // Track stable operation
                val stableCount = stableRunCount.incrementAndGet()
                if (stableCount == stableThreshold) {
                    logger.info("Browser stable for $stableThreshold health checks, reporting stable")
                    reportStable()
                }
            }

            jsExecutor.executeJavaScript(
                "try { ${pingQuery.inject("'pong'")} } catch(e) { console.error('ping failed', e); }",
                "health-ping"
            )
        } catch (e: Exception) {
            logger.warn("Health check error", e)
            browserHealthy.set(false)
            unhealthyCount.incrementAndGet()
            stableRunCount.set(0)

            // Report potential crash
            reportCrash()
        }
    }

    private fun attemptRecovery() {
        val attempts = recoveryAttempts.incrementAndGet()
        logger.warn("Attempting browser recovery (attempt $attempts/$maxRecoveryAttempts)")
        unhealthyCount.set(0)

        val shouldSwitchMode = reportCrash()

        if (shouldSwitchMode || attempts > maxRecoveryAttempts) {
            logger.warn("Recovery failed repeatedly or crash threshold exceeded, recommending mode switch")
            lastInitError.set(
                "Browser repeatedly became unresponsive. " +
                if (!useOffscreenRendering) "Switching to OSR mode may help." else "There may be a graphics driver issue."
            )

            if ((shouldSwitchMode || attempts > maxRecoveryAttempts) && modeSwitchCallback != null) {
                logger.info("Triggering mode switch callback")
                ApplicationManager.getApplication().invokeLater {
                    modeSwitchCallback?.invoke()
                }
                return
            }
        }

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
            reportCrash()
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
                val uri = runCatching { java.net.URI(href) }.getOrNull()
                if (uri?.scheme?.lowercase() in listOf("http", "https")) {
                    ApplicationManager.getApplication().invokeLater {
                        BrowserUtil.browse(href)
                    }
                }
            }
        }

        pingQuery = jsQueryManager.createStringQuery {
            lastPingResponse.set(System.currentTimeMillis())
            browserHealthy.set(true)
        }

        readyQuery = jsQueryManager.createStringQuery { message ->
            if (message == "ready") {
                val previousState = initializationState.getAndSet(3)
                if (previousState < 3) {
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
                if (initializationState.get() == 1 || initializationState.get() == 2) {
                    logger.debug("onLoadEnd: state=${initializationState.get()}, setup may already be running")
                }
                if (initializationState.get() == 1) {
                    setupReactApplication()
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: CefFrame?,
                errorCode: CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                if (errorCode == CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    logger.debug("Load aborted (normal during navigation): $failedUrl")
                    return
                }

                logger.error("JCEF load error: code=$errorCode, text=$errorText, url=$failedUrl")

                val errorMsg = "Browser load failed: $errorText (code: $errorCode, url: $failedUrl)"
                lastInitError.set(errorMsg)
                browserHealthy.set(false)

                val isRenderingError = errorCode == CefLoadHandler.ErrorCode.ERR_FAILED ||
                        errorText?.contains("crash", ignoreCase = true) == true ||
                        errorText?.contains("GPU", ignoreCase = true) == true

                if (isRenderingError) {
                    val shouldSwitch = reportCrash()
                    if (shouldSwitch) {
                        logger.warn("Load error triggered mode switch recommendation")
                        modeSwitchCallback?.let {
                            ApplicationManager.getApplication().invokeLater { it() }
                        }
                    }
                }
            }
        }, cefBrowser)
    }

    private fun setupReactApplication() {
        if (!initializationState.compareAndSet(1, 2)) {
            logger.debug("React setup skipped - already in progress or completed (state: ${initializationState.get()})")
            return
        }
        logger.info("Starting React application setup")

        try {
            val gson = Gson()
            val config = editor.getUserConfig()
            val configJson = gson.toJson(config)
            val currentProject = gson.toJson(mapOf("name" to editor.project.name))

            logger.debug("User config prepared")

            editor.getActiveFileInfo { file ->
                val fileJson = gson.toJson(file)

                editor.getSelectedSnippet { snippet ->
                    val snippetJson = if (snippet != null) gson.toJson(snippet) else "undefined"

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
                            window.postIntellijMessage = function(message) {
                                try {
                                    let messageData = typeof message === 'string' 
                                        ? message 
                                        : JSON.stringify(message);
                                    ${mainQuery.inject("messageData")};
                                } catch (e) {
                                    console.error('Error posting message:', e);
                                }
                            };
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
                            const config = window.__INITIAL_STATE__?.config;
                            if (typeof RefactChat !== 'undefined' && config) {
                                RefactChat.render(element, config);
                                console.log('RefactChat initialized successfully');
                                try { ${readyQuery.inject("'ready'")} } catch(e) { console.error('Ready signal failed', e); }
                            } else {
                                console.error('RefactChat not available or config missing', typeof RefactChat, config);
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

                    jsExecutor.executeBatch(scripts, "react-setup").exceptionally { throwable ->
                        logger.error("Failed to setup React application", throwable)
                        initializationState.set(1)
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

    fun isReady(): Boolean = initializationState.get() >= 3 && !jbcefBrowser.isDisposed

    val webView: JBCefBrowser
        get() = jbcefBrowser

    fun postMessage(message: Events.ToChat<*>?) {
        if (message == null) return

        if (initializationState.get() < 3) {
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
        postMessageTemplate.execute(message, description = "post-message")
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
