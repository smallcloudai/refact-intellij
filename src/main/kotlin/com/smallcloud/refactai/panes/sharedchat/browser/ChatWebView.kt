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

internal const val JCEF_UNRESPONSIVE_PONG_TIMEOUT_MS = 20_000L
internal const val JCEF_HEALTHY_PONG_WINDOW_MS = 10_000L
internal const val JCEF_HEALTH_CHECK_INTERVAL_MS = 10_000

internal fun isJcefRendererUnresponsive(nowMs: Long, lastPongAtMs: Long): Boolean {
    return nowMs - lastPongAtMs > JCEF_UNRESPONSIVE_PONG_TIMEOUT_MS
}

internal fun hasRecentJcefPong(nowMs: Long, lastPongAtMs: Long): Boolean {
    return nowMs - lastPongAtMs < JCEF_HEALTHY_PONG_WINDOW_MS
}

internal fun hasTimedOutOutstandingPing(
    nowMs: Long,
    lastPingSentAtMs: Long,
    pingInFlight: Boolean,
): Boolean {
    return pingInFlight && nowMs - lastPingSentAtMs > JCEF_UNRESPONSIVE_PONG_TIMEOUT_MS
}

class ChatWebView(val editor: Editor, val messageHandler: (event: Events.FromChat) -> Unit) : Disposable {
    private val logger = Logger.getInstance(ChatWebView::class.java)

    // 0=not loaded, 1=page loaded, 2=setup in progress, 3=React ready
    private val initializationState = AtomicInteger(0)
    private val browserHealthy = AtomicBoolean(true)
    private val disposing = AtomicBoolean(false)
    private var healthCheckTimer: Timer? = null
    private var setupDelayTimer: Timer? = null
    // lastPingSentAt: updated when a health ping is queued for the renderer
    // lastPongAt:     updated when the JS→Kotlin pong callback fires
    private val lastPingSentAt = AtomicLong(System.currentTimeMillis())
    private val lastPongAt = AtomicLong(System.currentTimeMillis())
    private val pingInFlight = AtomicBoolean(false)
    private lateinit var pingQuery: JBCefJSQuery
    private lateinit var readyQuery: JBCefJSQuery

    companion object {
        private val schemeHandlerRegistered = AtomicBoolean(false)
        private val companionLogger = Logger.getInstance(ChatWebView::class.java)

        private const val JS_QUERY_POOL_SIZE = 512
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

            val lastCrashTime = props.getLong(PREF_KEY_LAST_CRASH_TIME, 0L)
            if (now - lastCrashTime > CRASH_WINDOW_MS) {
                props.setValue(PREF_KEY_CRASH_COUNT, "0")
            }

            val crashCount = props.getInt(PREF_KEY_CRASH_COUNT, 0) + 1
            props.setValue(PREF_KEY_CRASH_COUNT, crashCount.toString())
            props.setValue(PREF_KEY_LAST_CRASH_TIME, now.toString())
            sessionCrashCount.incrementAndGet()

            companionLogger.warn("JCEF crash detected (session: ${sessionCrashCount.get()}, total: $crashCount)")

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

            val savedMode = PropertiesComponent.getInstance().getValue(PREF_KEY_RENDERING_MODE, "auto")
            if (savedMode == "osr") {
                companionLogger.info("Using OSR mode (saved preference)")
                return true
            }
            if (savedMode == "native") {
                companionLogger.info("Using native mode (saved preference)")
                return false
            }

            if (SystemInfo.isWindows || SystemInfo.isMac) {
                companionLogger.info("Using native rendering on Windows/Mac (auto)")
                return false
            }

            companionLogger.info("Using OSR mode (Linux default)")
            return true
        }
    }

    private lateinit var jsQueryManager: JSQueryManager
    private lateinit var asyncMessageHandler: AsyncMessageHandler<Events.FromChat>
    private lateinit var jsExecutor: JavaScriptExecutor
    private var osrRenderer: OSRRenderer? = null

    private lateinit var mainQuery: JBCefJSQuery
    private lateinit var linkQuery: JBCefJSQuery

    private lateinit var cefBrowser: CefBrowser
    private lateinit var jbcefBrowser: JBCefBrowser
    private lateinit var component: JComponent
    private val useOffscreenRendering: Boolean = determineRenderingMode()
    private lateinit var postMessageTemplate: JavaScriptTemplate
    private val syntheticResizeInFlight = AtomicBoolean(false)

    private fun dispatchCefVisibilitySignals() {
        if (!::cefBrowser.isInitialized || !::component.isInitialized) return

        runCatching {
            cefBrowser.setWindowVisibility(component.isShowing)
        }
        runCatching {
            cefBrowser.notifyScreenInfoChanged()
        }

        val width = component.width
        val height = component.height
        if (width > 1 && height > 1) {
            runCatching {
                cefBrowser.wasResized(width, height)
            }
        }
    }

    private fun nudgeComponentSize() {
        if (!::component.isInitialized) return
        if (!syntheticResizeInFlight.compareAndSet(false, true)) return

        ApplicationManager.getApplication().invokeLater {
            try {
                if (disposing.get() || !::component.isInitialized) return@invokeLater

                val width = component.width
                val height = component.height
                if (width <= 1 || height <= 1) return@invokeLater

                component.setSize(width + 1, height)
                component.revalidate()
                component.repaint()
                dispatchCefVisibilitySignals()

                ApplicationManager.getApplication().invokeLater {
                    try {
                        if (disposing.get() || !::component.isInitialized) return@invokeLater

                        component.setSize(width, height)
                        component.revalidate()
                        component.repaint()
                        dispatchCefVisibilitySignals()
                    } finally {
                        syntheticResizeInFlight.set(false)
                    }
                }
            } catch (_: Exception) {
                syntheticResizeInFlight.set(false)
            }
        }
    }

    fun setStyle() {
        try {
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

        setupRenderProcessHandler()
        setupFocusRecovery(browserComponent)
        setupHealthCheck()
    }

    private fun setupRenderProcessHandler() {
        jbcefBrowser.jbCefClient.addRequestHandler(object : CefRequestHandlerAdapter() {
            override fun onRenderProcessTerminated(
                browser: CefBrowser?,
                status: CefRequestHandler.TerminationStatus?
            ) {
                if (disposing.get()) return
                logger.warn("Render process terminated: $status")
                browserHealthy.set(false)
                stableRunCount.set(0)

                val shouldSwitch = reportCrash()
                ApplicationManager.getApplication().invokeLater {
                    if (disposing.get() || jbcefBrowser.isDisposed) return@invokeLater
                    if (shouldSwitch && modeSwitchCallback != null) {
                        logger.warn("Render crash triggered mode switch")
                        recoveryInProgress.set(false)
                        modeSwitchCallback?.invoke()
                    } else if (recoveryInProgress.compareAndSet(false, true)) {
                        logger.info("Reloading browser after render process crash")
                        initializationState.set(0)
                        resetHealthTracking()
                        recoveryAttempts.set(0)
                        jbcefBrowser.cefBrowser.reload()
                        // recoveryInProgress is cleared by onLoadingStateChange
                    } else {
                        logger.info("Render process terminated but recovery already in progress")
                    }
                }
            }
        }, cefBrowser)
    }

    private fun setupFocusRecovery(browserComponent: JComponent) {
        browserComponent.addFocusListener(object : FocusAdapter() {
            override fun focusGained(e: FocusEvent?) {
                checkBrowserHealth()
            }
        })
    }

    private val unhealthyCount = AtomicInteger(0)
    private val maxUnhealthyBeforeRecovery = 3
    private val recoveryAttempts = AtomicInteger(0)
    private val maxRecoveryAttempts = 2
    private val stableRunCount = AtomicInteger(0)
    private val stableThreshold = 10
    private val recoveryInProgress = AtomicBoolean(false)
    private var modeSwitchCallback: (() -> Unit)? = null

    fun setModeSwitchCallback(callback: () -> Unit) {
        modeSwitchCallback = callback
    }

    private fun resetHealthTracking(now: Long = System.currentTimeMillis()) {
        lastPingSentAt.set(now)
        lastPongAt.set(now)
        pingInFlight.set(false)
        browserHealthy.set(true)
        unhealthyCount.set(0)
        stableRunCount.set(0)
    }

    private fun markRendererResponsive(now: Long = System.currentTimeMillis()) {
        lastPongAt.set(now)
        pingInFlight.set(false)
        browserHealthy.set(true)
        unhealthyCount.set(0)
        recoveryAttempts.set(0)

        val stableCount = stableRunCount.incrementAndGet()
        if (stableCount == stableThreshold) {
            logger.info("Browser stable for $stableThreshold successful pings, reporting stable")
            reportStable()
        }
    }

    private fun dispatchHealthPing(now: Long) {
        if (!pingInFlight.compareAndSet(false, true)) return

        lastPingSentAt.set(now)

        try {
            val pingFuture = jsExecutor.executeJavaScript(
                "try { ${pingQuery.inject("'pong'")} } catch(e) { console.error('ping failed', e); }",
                "health-ping"
            )
            pingFuture.whenComplete { _, ex ->
                if (ex != null) {
                    pingInFlight.set(false)
                    stableRunCount.set(0)
                    logger.warn("Health check: ping dispatch failed", ex)
                }
            }
        } catch (e: Exception) {
            pingInFlight.set(false)
            stableRunCount.set(0)
            logger.warn("Health check: ping dispatch failed", e)
        }
    }

    private fun setupHealthCheck() {
        healthCheckTimer = Timer(JCEF_HEALTH_CHECK_INTERVAL_MS, null).apply {
            isRepeats = true
            addActionListener {
                if (initializationState.get() >= 3 && !jbcefBrowser.isDisposed) {
                    checkBrowserHealth()
                }
            }
            start()
        }
    }

    private fun checkBrowserHealth() {
        if (disposing.get() || jbcefBrowser.isDisposed) return
        if (!::pingQuery.isInitialized) return
        if (initializationState.get() < 3) return
        if (recoveryInProgress.get()) return

        val now = System.currentTimeMillis()
        val timeSincePingSent = now - lastPingSentAt.get()
        val lastPongAtMs = lastPongAt.get()
        val timeSincePong = now - lastPongAtMs
        val pingTimedOut = hasTimedOutOutstandingPing(
            nowMs = now,
            lastPingSentAtMs = lastPingSentAt.get(),
            pingInFlight = pingInFlight.get(),
        )

        if (pingTimedOut) {
            val count = unhealthyCount.incrementAndGet()
            browserHealthy.set(false)
            stableRunCount.set(0)
            pingInFlight.set(false)
            logger.warn("Browser ping timed out: lastPong=${timeSincePong}ms, lastPing=${timeSincePingSent}ms (count: $count)")

            if (count >= maxUnhealthyBeforeRecovery) {
                attemptRecovery()
                return
            }
        }

        if (pingInFlight.get()) {
            return
        }

        dispatchHealthPing(now)
    }

    private fun attemptRecovery() {
        if (disposing.get()) return
        if (!recoveryInProgress.compareAndSet(false, true)) {
            logger.info("Recovery already in progress, skipping")
            return
        }

        val attempts = recoveryAttempts.incrementAndGet()
        logger.warn("Attempting browser recovery (attempt $attempts/$maxRecoveryAttempts)")
        unhealthyCount.set(0)

        val shouldSwitchMode = attempts > maxRecoveryAttempts

        if (shouldSwitchMode || attempts > maxRecoveryAttempts) {
            logger.warn("Recovery failed repeatedly or crash threshold exceeded, recommending mode switch")
            lastInitError.set(
                "Browser repeatedly became unresponsive. " +
                if (!useOffscreenRendering) "Switching to OSR mode may help." else "There may be a graphics driver issue."
            )

            if (modeSwitchCallback != null) {
                logger.info("Triggering mode switch callback")
                PropertiesComponent.getInstance().setValue(PREF_KEY_RENDERING_MODE, "osr")
                osrFallbackTriggered.set(true)
                ApplicationManager.getApplication().invokeLater {
                    recoveryInProgress.set(false)
                    modeSwitchCallback?.invoke()
                }
                return
            }
        }

        try {
            ApplicationManager.getApplication().invokeLater {
                if (disposing.get() || jbcefBrowser.isDisposed) {
                    recoveryInProgress.set(false)
                    return@invokeLater
                }
                logger.info("Reloading browser to recover from unhealthy state")
                initializationState.set(0)
                resetHealthTracking()
                jbcefBrowser.cefBrowser.reload()
            }
        } catch (e: Exception) {
            recoveryInProgress.set(false)
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
                val uri = runCatching { java.net.URI(href) }.getOrNull()
                if (uri?.scheme?.lowercase() in listOf("http", "https")) {
                    ApplicationManager.getApplication().invokeLater {
                        BrowserUtil.browse(href)
                    }
                }
            }
        }

        pingQuery = jsQueryManager.createStringQuery {
            markRendererResponsive()
        }

        readyQuery = jsQueryManager.createStringQuery { message ->
            if (message == "ready") {
                val previousState = initializationState.getAndSet(3)
                if (previousState < 3) {
                    logger.info("React application signaled ready")
                }
                markRendererResponsive()
                refreshAfterVisibilityChange()
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
                    val previousState = initializationState.getAndSet(0)
                    if (previousState > 0) {
                        logger.info("Page reload detected, resetting initialization state from $previousState to 0")
                    }
                    recoveryInProgress.set(false)
                    pingInFlight.set(false)
                    setupDelayTimer?.stop()
                    setupDelayTimer = null
                    return
                }

                logger.info("Page loading completed, current state: ${initializationState.get()}")

                if (initializationState.compareAndSet(0, 1)) {
                    logger.info("Page loaded, scheduling React setup")
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
                if (!disposing.get() && initializationState.get() == 1) {
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

                if (errorCode == CefLoadHandler.ErrorCode.ERR_UNKNOWN_URL_SCHEME) {
                    logger.debug("Unknown URL scheme (e.g. mailto:, tel:) — ignored: $failedUrl")
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
        if (disposing.get()) return
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
                    if (disposing.get()) return@getSelectedSnippet
                    val snippetJson = if (snippet != null) gson.toJson(snippet) else "undefined"

                    val scripts = try { listOf(
                        """
                        if (!window.__REFACT_BRIDGE_INSTALLED__) {
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
                    ) } catch (e: IllegalStateException) {
                        logger.warn("Skipping React setup: JS queries already disposed", e)
                        return@getSelectedSnippet
                    }

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

    fun refreshAfterVisibilityChange() {
        if (disposing.get()) return

        ApplicationManager.getApplication().invokeLater {
            if (disposing.get()) return@invokeLater
            if (!::component.isInitialized) return@invokeLater

            component.revalidate()
            component.repaint()
            dispatchCefVisibilitySignals()
            nudgeComponentSize()
        }

        if (!::jsExecutor.isInitialized) return
        if (!::jbcefBrowser.isInitialized || jbcefBrowser.isDisposed) return
        if (initializationState.get() < 3) return

        jsExecutor.executeAsync(
            """
            window.dispatchEvent(new Event('resize'));
            requestAnimationFrame(() => {
                window.dispatchEvent(new Event('resize'));
            });
            """.trimIndent(),
            "visibility-refresh",
        )
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
        disposing.set(true)
        logger.info("Disposing ChatWebView")

        try {
            healthCheckTimer?.stop()
            healthCheckTimer = null
            setupDelayTimer?.stop()
            setupDelayTimer = null

            osrRenderer?.cleanup()
            osrRenderer = null

            if (::asyncMessageHandler.isInitialized) asyncMessageHandler.dispose()
            if (::jsQueryManager.isInitialized) jsQueryManager.dispose()

            val app = ApplicationManager.getApplication()
            if (app.isDispatchThread) {
                app.executeOnPooledThread { disposeJsExecutorAndBrowser() }
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
            if (::jsExecutor.isInitialized) jsExecutor.dispose()

            if (::cefBrowser.isInitialized) {
                try {
                    CefLifecycleManager.releaseBrowser(cefBrowser)
                } catch (e: Exception) {
                    logger.warn("Failed to release browser through lifecycle manager", e)
                    if (::jbcefBrowser.isInitialized && !jbcefBrowser.isDisposed) {
                        jbcefBrowser.dispose()
                    }
                }
            }

            logger.info("ChatWebView disposal completed")
        } catch (e: Exception) {
            logger.error("Error in background disposal", e)
        }
    }
}
