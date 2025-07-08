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
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.*
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
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
    private val logger = Logger.getInstance(ChatWebView::class.java)

    // Thread-safe initialization state management
    // 0 = not initialized, 1 = JS bridge ready, 2 = React initialized, 3 = fully ready
    private val initializationState = AtomicInteger(0)

    // Resource managers
    private val jsQueryManager: JSQueryManager
    private val asyncMessageHandler: AsyncMessageHandler<Events.FromChat>
    private val jsExecutor: JavaScriptExecutor
    private var osrRenderer: OSRRenderer? = null

    // JavaScript queries for IDE communication
    private lateinit var mainQuery: JBCefJSQuery
    private lateinit var linkQuery: JBCefJSQuery

    // Browser instances
    private val cefBrowser: CefBrowser
    private val jbcefBrowser: JBCefBrowser
    private val component: JComponent

    // Determine rendering mode based on platform
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

    init {
        logger.info("Initializing ChatWebView with OSR: $useOffscreenRendering")

        try {
            // Create browser using existing builder pattern for compatibility
            jbcefBrowser = JBCefBrowser
                .createBuilder()
                .setEnableOpenDevToolsMenuItem(true)
                .setUrl("http://refactai/index.html")
                .setOffScreenRendering(useOffscreenRendering)
                .build()
            
            cefBrowser = jbcefBrowser.cefBrowser

            // Register browser with lifecycle manager
            CefLifecycleManager.registerBrowser(cefBrowser)

            // Initialize resource managers
            jsQueryManager = JSQueryManager(jbcefBrowser)
            asyncMessageHandler = AsyncMessageHandler(Events::parse, messageHandler)
            jsExecutor = JavaScriptExecutor(jbcefBrowser, timeoutMs = 5000L, poolSize = 3)

            // Setup platform-specific rendering and create component
            component = setupPlatformSpecificFeatures()

            // Register scheme handler
            CefApp.getInstance().registerSchemeHandlerFactory("http", "refactai", RequestHandlerFactory())

            // Setup JavaScript queries
            setupJavaScriptQueries()

            // Setup load handler with atomic state management
            setupLoadHandler()

            // Create browser immediately
            jbcefBrowser.createImmediately()

            logger.info("ChatWebView initialization completed")

        } catch (e: Exception) {
            logger.error("Failed to initialize ChatWebView", e)
            dispose() // Clean up any partial initialization
            throw e
        }
    }

    private fun setupPlatformSpecificFeatures(): JComponent {
        val resultComponent = if (useOffscreenRendering) {
            // Setup OSR optimizations for Linux
            osrRenderer = OSRRenderer(targetFps = 30)

            // JBCef handles OSR internally, we just get the component
            val browserComponent = jbcefBrowser.component

            // Attach OSR optimizations to the browser component
            osrRenderer!!.attach(browserComponent)
            logger.info("OSR optimizations attached for Linux")

            browserComponent
        } else {
            // Setup native rendering features for Windows/Mac
            setupNativeRenderingFeatures()
            jbcefBrowser.component
        }

        // Setup common features
        setupCommonBrowserFeatures()

        return resultComponent
    }

    private fun setupNativeRenderingFeatures() {
        // Tab handling for diff mode
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
        logger.info("Native rendering features configured")
    }

    private fun setupCommonBrowserFeatures() {
        // Disable context menu unless in debug mode
        if (System.getenv("REFACT_DEBUG") != "1") {
            jbcefBrowser.setProperty(JBCefBrowserBase.Properties.NO_CONTEXT_MENU, true)
        }

        // Setup display handler for console messages and cursor changes
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
                // Cursor handling is a known issue in this IDE version - skip custom handling
                return super.onCursorChange(browser, cursorType)
            }
        }, cefBrowser)

        // Setup file dialog handler for Linux
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
    }

    private fun setupJavaScriptQueries() {
        // Main message handler query
        mainQuery = jsQueryManager.createStringQuery { message ->
            if (!asyncMessageHandler.offerMessage(message)) {
                logger.warn("Failed to queue message for processing")
            }
        }

        // Hyperlink redirect handler query
        linkQuery = jsQueryManager.createStringQuery { href ->
            if (href.isNotEmpty() && !href.contains("#") && href != "http://refactai/index.html") {
                ApplicationManager.getApplication().invokeLater {
                    BrowserUtil.browse(href)
                }
            }
        }

        logger.info("JavaScript queries configured successfully")
    }

    private fun setupLoadHandler() {
        jbcefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (isLoading) return
                
                logger.info("Page loading completed, current state: ${initializationState.get()}")

                // Thread-safe initialization state machine
                when (initializationState.get()) {
                    0 -> {
                        if (initializationState.compareAndSet(0, 1)) {
                            logger.info("Page loaded, setting up JavaScript bridge")
                            // Setup JavaScript queries and message bus
                            ApplicationManager.getApplication().invokeLater {
                                // Small delay to ensure DOM is ready
                                Thread.sleep(100)
                                // Trigger React setup
                                if (initializationState.compareAndSet(1, 2)) {
                                    logger.info("Setting up React application")
                                    setupReactApplication()
                                }
                            }
                        }
                    }
                }

                // Always update style on each load completion
                ApplicationManager.getApplication().invokeLater {
                    setStyle()
                }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                logger.info("Load end event - HTTP status: $httpStatusCode")

                // Force React setup if it hasn't happened yet
                if (initializationState.get() < 2) {
                    logger.info("Forcing React setup on load end")
                    ApplicationManager.getApplication().invokeLater {
                        Thread.sleep(200) // Give more time for resources to load
                        setupReactApplication()
                        initializationState.set(2)
                    }
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

                    // Use batched execution for better performance
                    val scripts = listOf(
                        // Initial state setup
                        """
                        const config = $configJson;
                        const active_file = $fileJson;
                        const selected_snippet = $snippetJson;
                        const current_project = $currentProject;
                        window.__INITIAL_STATE__ = { config, active_file, selected_snippet, current_project };
                        """.trimIndent(),

                        // Theme class setup - apply both vscode-* and plain mode classes
                        """
                        if (config.themeProps && config.themeProps.appearance === "dark") {
                            document.body.className = "vscode-dark dark";
                        } else if (config.themeProps && config.themeProps.appearance === "light") {
                            document.body.className = "vscode-light light";
                        }
                        """.trimIndent(),

                        // Setup JavaScript bridge for IDE communication
                        """
                        console.log('Setting up JavaScript bridge...');
                        
                        // Listen for postMessage events from React app
                        window.addEventListener('message', function(event) {
                            try {
                                let messageData;
                                if (typeof event.data === 'string') {
                                    messageData = event.data;
                                } else {
                                    messageData = JSON.stringify(event.data);
                                }
                                
                                ${mainQuery.inject("messageData")};
                            } catch (e) {
                                console.error('Error processing message:', e);
                            }
                        });

                        // Also set up legacy handlers for compatibility
                        window.ideMessageHandler = function(message) {
                            ${mainQuery.inject("message")};
                        };

                        window.ideLinkHandler = function(href) {
                            ${linkQuery.inject("href")};
                        };
                        """.trimIndent(),

                        // Chat script loading
                        """
                        function loadChatJs() {
                            const element = document.getElementById("refact-chat");
                            if (typeof RefactChat !== 'undefined') {
                                RefactChat.render(element, config);
                                console.log('RefactChat initialized successfully');
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
        if (message != null) {
            val json = Events.stringify(message)
            postMessageString(json)
        }
    }

    private fun postMessageString(message: String) {
        // Use template for better performance with repeated postMessage calls
        val template = jsExecutor.createTemplate("window.postMessage(%s, '*');")
        template.execute(message, description = "post-message")
    }

    override fun dispose() {
        logger.info("Disposing ChatWebView")

        try {
            // Wait for pending JavaScript executions
            jsExecutor.awaitCompletion(2000L)

            // Dispose resource managers in proper order
            jsExecutor.dispose()
            asyncMessageHandler.dispose()
            jsQueryManager.dispose()

            // Clean up OSR renderer if used
            osrRenderer?.cleanup()
            osrRenderer = null

            // Register browser for lifecycle management cleanup
            try {
                CefLifecycleManager.releaseBrowser(cefBrowser)
            } catch (e: Exception) {
                logger.warn("Failed to release browser through lifecycle manager", e)
                // Fallback to direct disposal
                if (!jbcefBrowser.isDisposed) {
                    jbcefBrowser.dispose()
                }
            }

            logger.info("ChatWebView disposal completed")

        } catch (e: Exception) {
            logger.error("Error during ChatWebView disposal", e)
        }
    }
}
