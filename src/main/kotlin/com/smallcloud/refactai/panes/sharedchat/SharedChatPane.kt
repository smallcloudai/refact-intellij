package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ProjectTopics
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootEvent
import com.intellij.openapi.roots.ModuleRootListener
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Alarm
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.FimCache
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.lsp.LSPProcessHolderChangedNotifier
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.modes.diff.waitingDiff
import com.smallcloud.refactai.notifications.emitChat
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.settings.AppSettingsConfigurable
import com.smallcloud.refactai.struct.ChatMessage
import com.smallcloud.refactai.utils.EventDebouncer
import com.smallcloud.refactai.utils.SmartMessageQueue
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.HierarchyEvent
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.JPanel

class SharedChatPane(val project: Project) : JPanel(), Disposable {
    private val paneScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val logger = Logger.getInstance(SharedChatPane::class.java)
    private val editor = Editor(project)
    private var currentPage: String = ""
    var id: String? = null
    private val animatedFiles = mutableSetOf<String>()
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("SMCRainbowScheduler", 2)
    private val canonicalProjectRoot: String? = project.basePath?.let {
        runCatching { File(it).canonicalPath }.getOrNull()
    }
    private val canonicalRefactConfigDir: String? = runCatching {
        File(File(SystemProperties.getUserHome(), ".config"), "refact").canonicalPath
    }.getOrNull()

    private var isDropdownOpen = false
    private val dropdownStateCheckAlarm = Alarm(this)

    @Volatile
    private var isPanelVisible = true
    private var activationListener: PropertyChangeListener? = null
    private var trackedComponent: Component? = null

    private val messageQueue = SmartMessageQueue(
        maxCommands = 200,
        flushDebounceMs = 16L,
        parentDisposable = this
    )

    private val selectionDebouncer = EventDebouncer<Unit>(150L, this) {
        if (isPanelVisible) {
            doSendActiveFileInfo()
            doSendSelectedSnippet()
        }
    }

    private val configDebouncer = EventDebouncer<Unit>(100L, this) {
        if (isPanelVisible) {
            doSendUserConfig()
        }
    }

    init {
        messageQueue.setReadyCheck { browserLazy.isInitialized() && browser.isReady() }
        messageQueue.setSuspendFlushCheck {
            browserLazy.isInitialized() && !browser.isBrowserHealthy()
        }
        messageQueue.setFlushCallback { messages ->
            messages.forEach { browser.postMessage(it) }
        }
        this.addEventListeners()
        this.setupDropdownStateTracking()
        this.setupVisibilityTracking()
        this.setupActivationTracking()
    }

    private fun updateTrackedVisibility() {
        isPanelVisible = (trackedComponent ?: this).isShowing
    }

    private fun refreshBrowserSoon(delayMs: Long = 0L) {
        updateTrackedVisibility()
        if (!isPanelVisible || !browserLazy.isInitialized()) return

        paneScope.launch {
            if (delayMs > 0) delay(delayMs)
            updateTrackedVisibility()
            if (isPanelVisible && browserLazy.isInitialized()) {
                browser.refreshAfterVisibilityChange()
            }
        }
    }

    private fun setupTrackedComponent(component: Component) {
        if (trackedComponent === component) {
            updateTrackedVisibility()
            return
        }

        trackedComponent = component
        updateTrackedVisibility()

        component.addComponentListener(object : ComponentAdapter() {
            override fun componentShown(e: ComponentEvent?) {
                updateTrackedVisibility()
                onPanelBecameVisible()
            }

            override fun componentHidden(e: ComponentEvent?) {
                updateTrackedVisibility()
            }
        })

        component.addHierarchyListener { event ->
            val flags = HierarchyEvent.SHOWING_CHANGED.toLong() or
                HierarchyEvent.DISPLAYABILITY_CHANGED.toLong() or
                HierarchyEvent.PARENT_CHANGED.toLong()
            if (event.changeFlags and flags != 0L) {
                val wasVisible = isPanelVisible
                updateTrackedVisibility()
                if (!wasVisible && isPanelVisible) {
                    onPanelBecameVisible()
                }
            }
        }
    }

    private fun setupVisibilityTracking() {
        addHierarchyListener { e ->
            if (e.changeFlags and java.awt.event.HierarchyEvent.SHOWING_CHANGED.toLong() != 0L) {
                val wasVisible = isPanelVisible
                isPanelVisible = isShowing
                if (!wasVisible && isPanelVisible) {
                    onPanelBecameVisible()
                }
            }
        }
    }

    private fun setupActivationTracking() {
        val listener = PropertyChangeListener { event ->
            val name = event.propertyName
            if (name != "activeWindow" && name != "focusedWindow") return@PropertyChangeListener
            if (event.newValue == null) return@PropertyChangeListener

            updateTrackedVisibility()
            if (!isPanelVisible || !browserLazy.isInitialized()) return@PropertyChangeListener

            refreshBrowserSoon()
            refreshBrowserSoon(48)
            refreshBrowserSoon(180)
        }

        activationListener = listener
        val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
        focusManager.addPropertyChangeListener("activeWindow", listener)
        focusManager.addPropertyChangeListener("focusedWindow", listener)
    }

    private fun onPanelBecameVisible() {
        if (browserLazy.isInitialized()) {
            browser.refreshAfterVisibilityChange()
            selectionDebouncer.flush()
            configDebouncer.flush()
            messageQueue.flushNow()

            paneScope.launch {
                delay(32)
                if (isPanelVisible && browserLazy.isInitialized()) {
                    browser.refreshAfterVisibilityChange()
                }
            }
        }
    }

    fun newChat() {
        this.postMessage(Events.NewChat)
    }

    private fun sendSelectedSnippet() {
        selectionDebouncer.debounce(Unit)
    }

    private fun doSendSelectedSnippet() {
        this.editor.getSelectedSnippet { snippet ->
            if (snippet != null) {
                val message = Editor.SetSnippetToChat(snippet)
                this.postMessage(message)
            }
        }
    }

    fun executeCodeLensCommand(messages: Array<ChatMessage>, sendImmediately: Boolean, openNewTab: Boolean) {
        if (openNewTab || this.currentPage != "chat" || messages.isEmpty()) {
            newChat()
        }
        if (messages.isEmpty()) {
            return
        }
        this.postMessage(Events.CodeLensCommand(Events.CodeLensCommandPayload("", sendImmediately, messages)))
    }

    private fun sendUserConfig() {
        configDebouncer.debounce(Unit)
    }

    private fun doSendUserConfig() {
        val config = this.editor.getUserConfig()
        val message = Events.Config.Update(config)
        this.postMessage(message)
    }

    private fun sendCurrentProjectInfo() {
        val message = Events.CurrentProject.SetCurrentProject(this.editor.getCurrentProject())
        this.postMessage(message)
    }

    private fun sendActiveFileInfo() {
        selectionDebouncer.debounce(Unit)
    }

    private fun doSendActiveFileInfo() {
        this.editor.getActiveFileInfo { file ->
            val safeFile = if (file.path.isNotEmpty() && isPathWithinAllowedScope(file.path)) {
                file
            } else {
                file.copy(content = null)
            }
            val message = ActiveFileToChat(safeFile)
            this.postMessage(message)
        }
    }

    private fun handleForceReloadFileByPath(fileName: String) {
        val validatedPath = validateAndSanitizePath(fileName, "handleForceReloadFileByPath") ?: return
        ApplicationManager.getApplication().invokeLater {
            val virtualFile: VirtualFile? =
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(validatedPath))
            if (virtualFile == null) {
                logger.warn("handleForceReloadFileByPath: File not found: $fileName (validated: $validatedPath)")
                return@invokeLater
            }
            VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile)
        }
    }

    private fun isSafeExternalUrl(url: String): Boolean {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in listOf("http", "https")
    }

    private fun openExternalUrl(url: String) {
        if (!isSafeExternalUrl(url)) {
            logger.warn("Blocked unsafe external URL: $url")
            return
        }
        BrowserUtil.browse(url)
    }

    private fun handlePasteDiff(content: String) {
        val currentEditor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        ApplicationManager.getApplication().invokeLater {
            ModeProvider.getOrCreateModeProvider(currentEditor).getDiffMode().actionPerformed(currentEditor, content)
        }
    }

    private fun handleNewFile(content: String): LightVirtualFile {
        val vf = LightVirtualFile("Untitled", content)
        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
        }

        return vf
    }


    private fun addEventListeners() {
        logger.info("Adding ide event listeners")
        val listener: FileEditorManagerListener = object : FileEditorManagerListener {
            override fun fileOpened(@NotNull source: FileEditorManager, @NotNull file: VirtualFile) {
                this@SharedChatPane.sendActiveFileInfo()
            }

            override fun fileClosed(@NotNull source: FileEditorManager, @NotNull file: VirtualFile) {
                this@SharedChatPane.sendActiveFileInfo()
            }

            override fun selectionChanged(@NotNull event: FileEditorManagerEvent) {
                this@SharedChatPane.sendActiveFileInfo()
                this@SharedChatPane.sendSelectedSnippet()
            }

        }

        val selectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                this@SharedChatPane.sendActiveFileInfo()
                this@SharedChatPane.sendSelectedSnippet()
            }
        }

        project.messageBus.connect(this).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)

        val ef = EditorFactory.getInstance()
        ef.eventMulticaster.addSelectionListener(selectionListener, this)

        project.messageBus.connect(this).subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener {
            ApplicationManager.getApplication().invokeLater {
                if(!project.isDisposed) {
                    this@SharedChatPane.setLookAndFeel()
                }
            }
        })

        project.messageBus.connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun astFlagChanged(newValue: Boolean) {
                    logger.info("ast changed to: $newValue")
                    this@SharedChatPane.sendUserConfig()
                }

                override fun vecdbFlagChanged(newValue: Boolean) {
                    logger.info("vecdb changed to: $newValue")
                    this@SharedChatPane.sendUserConfig()
                }
            })

        editor.project.messageBus.connect(this)
            .subscribe(
                InferenceGlobalContextChangedNotifier.TOPIC,
                object : InferenceGlobalContextChangedNotifier {
                    override fun userInferenceUriChanged(newUrl: String?) {
                        this@SharedChatPane.sendUserConfig()
                    }
                })
        editor.project.messageBus
            .connect(this)
            .subscribe(LSPProcessHolderChangedNotifier.TOPIC, object : LSPProcessHolderChangedNotifier {
                override fun lspIsActive(isActive: Boolean) {
                    this@SharedChatPane.sendUserConfig()
                    this@SharedChatPane.sendCurrentProjectInfo()
                }
            })

        project.messageBus.connect(this).subscribe(ProjectTopics.PROJECT_ROOTS, object : ModuleRootListener {
            override fun rootsChanged(event: ModuleRootEvent) {
                this@SharedChatPane.sendCurrentProjectInfo()
                this@SharedChatPane.sendUserConfig()
            }
        })

        paneScope.launch {
            FimCache.subscribe { data ->
                this@SharedChatPane.sendFimData(data)
            }
        }
    }

    private fun setLookAndFeel() {
        if (browserLazy.isInitialized()) {
            browser.setStyle()
            sendUserConfig()
        }
    }

    private fun setupDropdownStateTracking() {
        val trackingScript = """
            (function() {
                if (window.__refactDropdownTrackerInstalled) return;
                window.__refactDropdownTrackerInstalled = true;

                window.refactDropdownTracker = {
                    isOpen: false,
                    checkPending: false,

                    notifyStateChange: function(isOpen) {
                        if (this.isOpen !== isOpen) {
                            this.isOpen = isOpen;
                            window.postMessage({
                                type: 'ide/dropdownStateChanged',
                                payload: { isOpen: isOpen }
                            }, '*');
                        }
                    },

                    checkDropdownState: function() {
                        if (this.checkPending) return;
                        this.checkPending = true;

                        requestAnimationFrame(() => {
                            this.checkPending = false;
                            const selectors = [
                                '[role="menu"]',
                                '[role="listbox"]',
                                '[data-state="open"]',
                                '[aria-expanded="true"]'
                            ];

                            let foundOpen = false;
                            for (const selector of selectors) {
                                const el = document.querySelector(selector);
                                if (el && el.offsetParent !== null) {
                                    foundOpen = true;
                                    break;
                                }
                            }
                            this.notifyStateChange(foundOpen);
                        });
                    }
                };

                const observer = new MutationObserver(function() {
                    window.refactDropdownTracker.checkDropdownState();
                });

                observer.observe(document.body, {
                    childList: true,
                    subtree: true,
                    attributes: true,
                    attributeFilter: ['data-state', 'aria-expanded', 'aria-hidden']
                });

                document.addEventListener('click', function() {
                    setTimeout(() => window.refactDropdownTracker.checkDropdownState(), 50);
                }, true);
            })();
        """.trimIndent()

        val setupAlarm = Alarm(this)
        setupAlarm.addRequest({
            if (!browserLazy.isInitialized()) return@addRequest
            try {
                browser.webView.cefBrowser.executeJavaScript(trackingScript, null, 0)
                logger.debug("Dropdown state tracking script injected")
            } catch (e: Exception) {
                logger.warn("Failed to inject dropdown tracking script", e)
            }
        }, 1500)
    }

    private fun waitForDropdownClose(callback: () -> Unit) {
        if (!isDropdownOpen) {
            logger.info("Dropdown state indicates closed, executing immediately")
            callback()
            return
        }

        try {
            browser.webView.cefBrowser.executeJavaScript(
                "window.refactDropdownTracker && window.refactDropdownTracker.checkDropdownState();",
                null, 0
            )
        } catch (e: Exception) {
            logger.debug("Could not execute dropdown state check: ${e.message}")
        }

        dropdownStateCheckAlarm.cancelAllRequests()

        fun checkDropdownState(attempt: Int = 0) {
            val elapsedMs = attempt * 16

            if (!isDropdownOpen) {
                logger.info("Dropdown closed after ${elapsedMs}ms, executing callback")
                callback()
            } else if (attempt < 31) {
                dropdownStateCheckAlarm.addRequest({
                    checkDropdownState(attempt + 1)
                }, 16)
            } else if (attempt < 62) {
                dropdownStateCheckAlarm.addRequest({
                    checkDropdownState(attempt + 1)
                }, 32)
            } else if (attempt < 93) {
                dropdownStateCheckAlarm.addRequest({
                    checkDropdownState(attempt + 1)
                }, 50)
            } else {
                logger.warn("Dropdown detection may have failed after ${elapsedMs}ms, using fallback delay")
                dropdownStateCheckAlarm.addRequest({
                    logger.info("Fallback delay completed, executing callback")
                    callback()
                }, 100)
            }
        }

        checkDropdownState()
    }

    private fun handleOpenSettings() {
        browser.getComponent().repaint()
        waitForDropdownClose {
            val finalAlarm = Alarm(this)
            finalAlarm.addRequest({
                ApplicationManager.getApplication().invokeLater({
                    logger.info("Opening settings dialog")
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, AppSettingsConfigurable::class.java)
                }, ModalityState.defaultModalityState())
            }, 25)
        }
    }

    private fun sendFimData(data: Events.Fim.FimDebugPayload) {
        val message = Events.Fim.Receive(data)
        this.postMessage(message)
    }

    private fun handleFimRequest() {
        if (FimCache.last == null) {
            val message = Events.Fim.Error("Data not found, try causing a completion in the editor.")
            postMessage(message)
        } else {
            this.sendFimData(FimCache.last!!)
        }
    }

    private fun handleOpenHotKeys() {
        logger.info("Hotkeys button clicked - ensuring UI is ready for modal")

        browser.getComponent().repaint()

        waitForDropdownClose {
            val finalAlarm = Alarm(this)
            finalAlarm.addRequest({
                ApplicationManager.getApplication().invokeLater({
                    logger.info("Opening hotkeys dialog")
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, KeymapPanel::class.java) {
                        it.enableSearch("Refact.ai")
                    }
                }, ModalityState.defaultModalityState())
            }, 25)
        }
    }

    private fun handleOpenFile(fileName: String, line: Int?) {
        val validatedPath = validateAndSanitizePath(fileName, "handleOpenFile") ?: return
        val file = File(validatedPath)
        invokeLater {
            val vf = VfsUtil.findFileByIoFile(file, true) ?: return@invokeLater
            val fileDescriptor = OpenFileDescriptor(project, vf)
            val editor = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
            line?.let {
                val targetLine = (line - 1).coerceAtLeast(0)
                editor?.caretModel?.moveToLogicalPosition(LogicalPosition(targetLine, 0))
            }
        }
    }

    private fun deleteFile(fileName: String) {
        val validatedPath = validateAndSanitizePath(fileName, "deleteFile") ?: return
        logger.warn("deleteFile: $validatedPath")
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().runWriteAction {
                LocalFileSystem.getInstance().findFileByPath(validatedPath)?.delete(this.project)
            }
        }
    }

    private fun sanitizeFileNameForPosix(fileName: String): String {
        val patterns = listOf(
            Regex("""^\\\\\\\\\?\\.*""") to 5, // '\\\\?\\' prefix
            Regex("""^\\\\\?\\[^\\].*""") to 4,     // '\\?\' prefix
            Regex("""^\\\\\?\\\\.*""") to 5,     // '\\?\\' prefix
            Regex("""^\\\?\\.*""") to 3        // '\?\' prefix
        )

        var result = fileName
        for ((pattern, length) in patterns) {
            if (pattern.containsMatchIn(result)) {
                result = result.substring(length)
                break
            }
        }

        return result
    }

    private enum class PathScope { PROJECT_ONLY, PROJECT_OR_REFACT_CONFIG }

    private fun isPathWithinAllowedScope(path: String, scope: PathScope = PathScope.PROJECT_ONLY): Boolean {
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        if (scope == PathScope.PROJECT_OR_REFACT_CONFIG) {
            canonicalRefactConfigDir?.let { configDir ->
                if (canonicalPath == configDir || canonicalPath.startsWith(configDir + File.separator)) return true
            }
        }
        val root = canonicalProjectRoot ?: return false
        return canonicalPath == root || canonicalPath.startsWith(root + File.separator)
    }

    private fun resolveProjectPath(fileName: String): String {
        val sanitized = sanitizeFileNameForPosix(fileName)
        val file = File(sanitized)
        if (file.isAbsolute) return sanitized
        val base = project.basePath ?: return sanitized
        return File(base, sanitized).path
    }

    private fun validateAndSanitizePath(
        fileName: String,
        operation: String,
        scope: PathScope = PathScope.PROJECT_ONLY
    ): String? {
        val resolved = resolveProjectPath(fileName)

        val canonical = try {
            File(resolved).canonicalPath
        } catch (e: Exception) {
            logger.warn("$operation blocked: failed to canonicalize path: $fileName", e)
            return null
        }

        if (!isPathWithinAllowedScope(canonical, scope)) {
            logger.warn("$operation blocked: path outside allowed scope ($scope): $fileName (project: ${project.basePath})")
            return null
        }

        return canonical
    }

    private fun openNewFile(fileName: String): File? {
        val validatedPath = validateAndSanitizePath(fileName, "openNewFile") ?: return null
        val file = File(validatedPath)
        if (!file.exists()) {
            val created = runCatching {
                file.parentFile?.mkdirs()
                file.createNewFile()
            }.getOrElse { e ->
                logger.warn("openNewFile: failed to create file: $validatedPath", e)
                return null
            }
            if (!created) {
                logger.warn("openNewFile: createNewFile returned false: $validatedPath")
                return null
            }
        }
        val fileSystem = StandardFileSystems.local()
        fileSystem.refresh(false)
        logger.info("openNewFile: $validatedPath")

        return file
    }

    private fun setContent(fileName: String, content: String) {
        val validatedPath = validateAndSanitizePath(fileName, "setContent") ?: return
        logger.warn("setContent: item.fileNameEdit = $validatedPath")
        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(validatedPath)
            if (file == null) {
                logger.warn("setContent: item.fileNameEdit = $validatedPath is null")
                return@invokeLater
            }

            ApplicationManager.getApplication().runWriteAction {
                FileDocumentManager.getInstance().getDocument(file)?.setText(content)
            }
        }
    }

    private suspend fun handlePatchApply(payload: Events.Patch.ApplyPayload) {
        withContext(Dispatchers.IO) {
            payload.items.forEach { item ->
                if (item.fileNameAdd != null) {
                    val file = openNewFile(item.fileNameAdd)
                    if (file != null) {
                        logger.warn("handlePatchApply: item.fileNameAdd = ${file.path}")
                        setContent(file.path, item.fileText)
                    }
                }

                if (item.fileNameDelete != null) {
                    logger.warn("handlePatchApply: item.fileNameDelete = ${item.fileNameDelete}")
                    deleteFile(item.fileNameDelete)
                }

                if (item.fileNameEdit != null) {
                    logger.warn("handlePatchApply: item.fileNameEdit = ${item.fileNameEdit}")
                    setContent(item.fileNameEdit, item.fileText)
                }
            }
        }
    }

    private fun showPatch(
        fileName: String,
        fileText: String,
        onTab: ((com.intellij.openapi.editor.Editor, Caret?, DataContext) -> Unit)? = null,
        onEsc: ((com.intellij.openapi.editor.Editor, Caret?, DataContext) -> Unit)? = null
    ) {
        logger.warn("showPatch: item.fileNameEdit = $fileName")
        this.handleAnimationStop(fileName)

        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileName)
            if (file == null) {
                logger.warn("showPatch: item.fileNameEdit = $fileName is null")
                return@invokeLater
            }

            val fileDescriptor = OpenFileDescriptor(project, file)
            val editor = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
            editor?.selectionModel?.setSelection(0, editor.document.textLength)
            if (editor != null && (onTab == null || onEsc == null)) {
                ModeProvider.getOrCreateModeProvider(editor)
                    .getDiffMode()
                    .actionPerformed(editor, fileText)
            } else if(editor != null && onTab != null && onEsc != null) {
                ModeProvider
                    .getOrCreateModeProvider(editor)
                    .addSideEffects(onTab, onEsc)
                    .actionPerformed(editor, fileText)
            }
        }
    }

    private suspend fun handlePatchShow(payload: Events.Patch.ShowPayload) {
        withContext(Dispatchers.IO) {
            payload.results.forEach { result ->
                if (result.fileNameAdd != null) {
                    val file = openNewFile(result.fileNameAdd)
                    if (file != null) {
                        logger.warn("handlePatchShow: item.fileNameAdd = ${file.path}")
                        showPatch(file.path, result.fileText)
                    }
                }
                if (result.fileNameDelete != null) {
                    logger.warn("handlePatchShow: item.fileNameDelete = ${result.fileNameDelete}")
                    deleteFile(result.fileNameDelete)
                }

                if (result.fileNameEdit != null) {
                    val validatedPath = validateAndSanitizePath(result.fileNameEdit, "handlePatchShow")
                    if (validatedPath != null) {
                        logger.warn("handlePatchShow: item.fileNameEdit = $validatedPath")
                        showPatch(validatedPath, result.fileText)
                    }
                }
            }
        }
    }

    private fun handleAnimationStart(fileName: String) {
        synchronized(this) { // action thread
            val validatedPath = validateAndSanitizePath(fileName, "handleAnimationStart") ?: return
            if (animatedFiles.contains(validatedPath)) return
            animatedFiles.add(validatedPath)
            val file = ApplicationManager.getApplication().runReadAction<VirtualFile?> {
                LocalFileSystem.getInstance().findFileByPath(validatedPath)
            } ?: return
            val fileDescriptor = OpenFileDescriptor(project, file)
            ApplicationManager.getApplication().invokeLater {
                val editor =
                    FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true) ?: return@invokeLater
                scheduler.submit {
                    waitingDiff(
                        editor,
                        editor.offsetToLogicalPosition(0),
                        editor.offsetToLogicalPosition(editor.document.textLength)
                    ) {
                        synchronized(this) {
                            animatedFiles.contains(validatedPath)
                        }
                    }
                }
            }
        }
    }

    private fun handleAnimationStop(fileName: String) {
        synchronized(this) {
            val validatedPath = validateAndSanitizePath(fileName, "handleAnimationStop") ?: run {
                val sanitized = sanitizeFileNameForPosix(fileName)
                animatedFiles.remove(sanitized)
                return
            }
            animatedFiles.remove(validatedPath)
            ApplicationManager.getApplication().invokeLater {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(validatedPath)
                virtualFile?.refresh(true, false)
            }
        }
    }

    private fun handleToolCall(payload: Events.IdeAction.ToolCallPayload) {
        when (val toolCall = payload.toolCall) {
            is TextDocToolCall.CreateTextDocToolCall -> {
                val path = validateAndSanitizePath(toolCall.function.arguments.path, "handleToolCall") ?: run {
                    handleFileAction(toolCall.id, payload.chatId, false)
                    return
                }
                val content = payload.edit.fileAfter
                createAndSetFileContent(path, content, payload.chatId, toolCall.id)
            }
            is TextDocToolCall.UpdateTextDocToolCall -> {
                val path = validateAndSanitizePath(toolCall.function.arguments.path, "handleToolCall") ?: run {
                    handleFileAction(toolCall.id, payload.chatId, false)
                    return
                }
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true) },
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, false) }
                )
            }
            is TextDocToolCall.ReplaceTextDocToolCall -> {
                val path = validateAndSanitizePath(toolCall.function.arguments.path, "handleToolCall") ?: run {
                    handleFileAction(toolCall.id, payload.chatId, false)
                    return
                }
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true) },
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, false) }
                )
            }
            is TextDocToolCall.UpdateRegexTextDocToolCall -> {
                val path = validateAndSanitizePath(toolCall.function.arguments.path, "handleToolCall") ?: run {
                    handleFileAction(toolCall.id, payload.chatId, false)
                    return
                }
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true) },
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, false) }
                )
            }
            else -> {}
        }
    }


    private fun writeContentToVirtualFile(virtualFile: VirtualFile, content: String) {
        ApplicationManager.getApplication().runWriteAction {
            FileDocumentManager.getInstance().getDocument(virtualFile)?.setText(content)
        }
    }
    private fun openVirtualFileInIde(virtualFile: VirtualFile) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(virtualFile, true)
    }

    private fun createAndSetFileContent(path: String, content: String, chatId: String, toolCallId: String) {
        val validatedPath = validateAndSanitizePath(path, "createAndSetFileContent")
        if (validatedPath == null) {
            handleFileAction(toolCallId, chatId, false)
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val file = File(validatedPath)
                file.parentFile?.mkdirs()
                file.createNewFile()

                ApplicationManager.getApplication().invokeLater {
                    val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                    if (virtualFile != null) {
                        writeContentToVirtualFile(virtualFile, content)
                        openVirtualFileInIde(virtualFile)
                        handleFileAction(toolCallId, chatId, true)
                    } else {
                        handleFileAction(toolCallId, chatId, false)
                    }
                }
            } catch (e: Exception) {
                logger.error("Error creating or setting file content", e)
                ApplicationManager.getApplication().invokeLater {
                    handleFileAction(toolCallId, chatId, false)
                }
            }
        }
    }


    private fun handleFileAction(toolCallId: String, chatId: String, saved: Boolean) {
        val actionPayload = Events.IdeAction.ToolCallResponsePayload(toolCallId, chatId, saved)
        val action = Events.IdeAction.ToolCallResponse(actionPayload)
        postMessage(action)
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun handleTaskDone(payload: Events.TaskDonePayload) {
        val message = escapeHtml(payload.summary.ifEmpty { "Task completed" })
        ApplicationManager.getApplication().invokeLater {
            emitChat(project, message, payload.chatId)
        }
    }

    private fun handleAskQuestions(payload: Events.AskQuestionsPayload) {
        val count = payload.questions.size
        val text = when (count) {
            0 -> "your input"
            1 -> "1 question"
            else -> "$count questions"
        }
        ApplicationManager.getApplication().invokeLater {
            emitChat(project, "AI needs $text to continue", payload.chatId)
        }
    }

    fun switchToThread(chatId: String) {
        val action = Events.SwitchToThread(chatId)
        postMessage(action)
    }

    private suspend fun handleEvent(event: Events.FromChat) {
//        logger.warn("${event.toString()} ${event.payload.toString()}")
        when (event) {
            is Editor.PasteDiff -> this.handlePasteDiff(event.content)
            is Editor.NewFile -> this.handleNewFile(event.content)
            is Events.OpenSettings -> this.handleOpenSettings()
            is Events.Setup.OpenExternalUrl -> this.openExternalUrl(event.url)
            is Events.Fim.Request -> this.handleFimRequest()
            is Events.OpenHotKeys -> this.handleOpenHotKeys()
            is Events.OpenFile -> this.handleOpenFile(event.payload.filePath, event.payload.line)
            is Events.Patch.Apply -> this.handlePatchApply(event.payload)
            is Events.Patch.Show -> this.handlePatchShow(event.payload)
            is Events.Animation.Start -> this.handleAnimationStart(event.fileName)
            is Events.Animation.Stop -> this.handleAnimationStop(event.fileName)
            is Events.ChatPageChange -> {
                currentPage = event.payload.toString()
            }

            is Events.IdeAction.ToolCall -> {
                this.handleToolCall(event.payload)
            }
            is Editor.ForceReloadFileByPath -> {
                this.handleForceReloadFileByPath(event.path)
            }

            is Editor.ForceReloadProjectTreeFiles -> {
                ProjectRootManager.getInstance(project).contentRoots.forEach {
                    this.handleForceReloadFileByPath(it.path)
                }
            }

            is Editor.SetCodeCompletionModel -> {
                InferenceGlobalContext.instance.model = event.model
            }

            is Events.DropdownStateChanged -> {
                logger.debug("Dropdown state changed: isOpen=${event.isOpen}")
                isDropdownOpen = event.isOpen
            }

            is Events.TaskDone -> {
                handleTaskDone(event.payload)
            }

            is Events.AskQuestions -> {
                handleAskQuestions(event.payload)
            }

            else -> Unit
        }
    }

    private val browserLazy = lazy {
        ChatWebView(this.editor) { event ->
            paneScope.launch { handleEvent(event) }
        }
    }
    private val browser by browserLazy

    val webView: com.intellij.ui.jcef.JBCefBrowser
        get() = browser.webView

    internal val chatWebView: ChatWebView
        get() = browser

    fun attachMountedComponent(component: Component) {
        setupTrackedComponent(component)
    }

    private fun postMessage(message: Events.ToChat<*>?) {
        if (message != null) {
            messageQueue.enqueue(message)
        }
    }

    override fun dispose() {
        activationListener?.let { listener ->
            val focusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            focusManager.removePropertyChangeListener("activeWindow", listener)
            focusManager.removePropertyChangeListener("focusedWindow", listener)
        }
        activationListener = null
        paneScope.cancel()
        selectionDebouncer.cancel()
        configDebouncer.cancel()
        messageQueue.dispose()
        if (browserLazy.isInitialized()) {
            browser.dispose()
        }
        scheduler.shutdownNow()
    }

}

