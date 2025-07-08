package com.smallcloud.refactai.panes.sharedchat

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
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
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.*
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.awaitExit
import com.smallcloud.refactai.FimCache
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.BIN_PATH
import com.smallcloud.refactai.lsp.LSPProcessHolderChangedNotifier
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.modes.diff.waitingDiff
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.settings.AppSettingsConfigurable
import com.smallcloud.refactai.settings.Host
import com.smallcloud.refactai.struct.ChatMessage
import kotlinx.coroutines.*
import org.jetbrains.annotations.NotNull
import java.io.File
import java.util.concurrent.Future
import javax.swing.JPanel
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.roots.ProjectRootManager

class SharedChatPane(val project: Project) : JPanel(), Disposable {
    private val logger = Logger.getInstance(SharedChatPane::class.java)
    private val editor = Editor(project)
    private var currentPage: String = ""
    private var isChatStreaming: Boolean = false
    var id: String? = null
    private val animatedFiles = mutableSetOf<String>()
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService("SMCRainbowScheduler", 2)

    private var isDropdownOpen = false
    private val dropdownStateCheckAlarm = Alarm(this)

    private val messageQuery: ArrayDeque<Events.ToChat<*>> = ArrayDeque<Events.ToChat<*>>()
    private var workerFuture: Future<*>? = null
    private val workerScheduler =
        AppExecutorUtil.createBoundedScheduledExecutorService("SMCSharedChatPaneWorkerScheduler", 1)


    init {
        workerFuture = workerScheduler.scheduleWithFixedDelay({
            synchronized(this) {
                while (isReady() && messageQuery.isNotEmpty()) {
                    messageQuery.removeFirst().let {
                        this.browser.postMessage(it)
                    }
                }
            }
        }, 0, 80, java.util.concurrent.TimeUnit.MILLISECONDS)
        this.addEventListeners()
        this.setupDropdownStateTracking()
    }

    private fun isReady(): Boolean {
        return currentPage.isNotEmpty() // didn't get first message
    }

    fun newChat() {
        this.postMessage(Events.NewChat)
    }

    private fun sendSelectedSnippet() {
        this.editor.getSelectedSnippet { snippet ->
            if (snippet != null) {
                val message = Editor.SetSnippetToChat(snippet)
                this.postMessage(message)
            }
        }
    }

    fun executeCodeLensCommand(messages: Array<ChatMessage>, sendImmediately: Boolean, openNewTab: Boolean) {
        if (isChatStreaming) return
        if (openNewTab || this.currentPage != "chat") {
            newChat()
        }
        if (messages.isEmpty()) {
            // Just opening a new chat, no codelens execution
            newChat()
            return
        }
        isChatStreaming = true
        this.postMessage(Events.CodeLensCommand(Events.CodeLensCommandPayload("", sendImmediately, messages)))
    }

    private fun sendUserConfig() {
        val config = this.editor.getUserConfig()
        val message = Events.Config.Update(config)
        this.postMessage(message)
    }

    private fun sendActiveFileInfo() {
        this.editor.getActiveFileInfo { file ->
            val message = ActiveFileToChat(file)
            this.postMessage(message)
        }
    }

    private fun sendCurrentProjectInfo(p: Project = project) {
        val message = Events.CurrentProject.SetCurrentProject(p.name)
        this.postMessage(message)
    }

    private suspend fun handleSetupHost(host: Host) {
        val accountManager = AccountManager.instance
        when (host) {
            is Host.CloudHost -> {
                accountManager.apiKey = host.apiKey
                InferenceGlobalContext.instance.inferenceUri = "Refact"
                accountManager.user = host.userName
            }

            is Host.Enterprise -> {
                accountManager.apiKey = host.apiKey
                InferenceGlobalContext.instance.inferenceUri = host.endpointAddress
            }

            is Host.SelfHost -> {
                accountManager.apiKey = "any-key-will-work"
                InferenceGlobalContext.instance.inferenceUri = host.endpointAddress
            }

            is Host.BringYourOwnKey -> {
                val process = GeneralCommandLine(listOf(BIN_PATH, "--only-create-yaml-configs"))
                    .withRedirectErrorStream(true)
                    .createProcess()
                process.awaitExit()
                val out = process.getResultStdoutStr().getOrNull()
                if (out == null) {
                    println("Save btok file output is null")
                    return
                }
                val fileName = out.lines().last()

                ApplicationManager.getApplication().invokeLater {
                    val virtualFile: VirtualFile? =
                        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(fileName))
                    if (virtualFile != null) {
                        // Open the file in the editor
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    } else {
                        println("File not found: $fileName")
                    }
                    accountManager.apiKey = "any-key-will-work"
                    InferenceGlobalContext.instance.inferenceUri = fileName
                }
            }
        }
    }

    private fun handleForceReloadFileByPath(fileName: String) {
        ApplicationManager.getApplication().invokeLater {
            val sanitizedFileName = this.sanitizeFileNameForPosix(fileName);
            val virtualFile: VirtualFile? =
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(sanitizedFileName))
            if (virtualFile == null) {
                logger.warn("handleForceReloadFileByPath: File not found: $fileName (sanitized: $sanitizedFileName)")
                return@invokeLater
            }
            VfsUtil.markDirtyAndRefresh(true, true, true, virtualFile)
        }
    }

    private fun openExternalUrl(url: String) {
        BrowserUtil.browse(url)
    }

    private fun logOut() {
        AccountManager.instance.logout()
        InferenceGlobalContext.instance.inferenceUri = null
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

        project.messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, listener)

        val ef = EditorFactory.getInstance()
        ef.eventMulticaster.addSelectionListener(selectionListener, this)

        project.messageBus.connect().subscribe(
            EditorColorsManager.TOPIC,
            EditorColorsListener {
            ApplicationManager.getApplication().invokeLater {
                if(!project.isDisposed) {
                    this@SharedChatPane.setLookAndFeel()
                }
            }
        })

        project.messageBus.connect().subscribe(ProjectManager.TOPIC, object: ProjectManagerListener {
            override fun projectOpened(project: Project) {
                this@SharedChatPane.sendCurrentProjectInfo(project)
            }
        })

        // ast and vecdb settings change
        project.messageBus.connect()
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

        editor.project.messageBus.connect(PluginState.instance)
            .subscribe(
                InferenceGlobalContextChangedNotifier.TOPIC,
                object : InferenceGlobalContextChangedNotifier {
                    override fun userInferenceUriChanged(newUrl: String?) {
                        this@SharedChatPane.sendUserConfig()
                    }
                })
        editor.project.messageBus
            .connect(PluginState.instance)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun apiKeyChanged(newApiKey: String?) {
                    this@SharedChatPane.sendUserConfig()
                }
            })

        editor.project.messageBus
            .connect(PluginState.instance)
            .subscribe(LSPProcessHolderChangedNotifier.TOPIC, object : LSPProcessHolderChangedNotifier {
                override fun lspIsActive(isActive: Boolean) {
                    this@SharedChatPane.sendUserConfig()
                }
            })

        ApplicationManager.getApplication().invokeLater {
            CoroutineScope(Dispatchers.Main).launch {
                FimCache.subscribe { data ->
                    this@SharedChatPane.sendFimData(data)
                }
            }
        }
    }

    private fun setLookAndFeel() {
        this.browser.setStyle()
        this.sendUserConfig()
    }

    private fun setupDropdownStateTracking() {
        val trackingScript = """
            window.refactDropdownTracker = {
                isOpen: false,
                lastCheck: 0,

                onDropdownOpen: function() {
                    if (!this.isOpen) {
                        this.isOpen = true;
                        window.postMessage({
                            type: 'ide/dropdownStateChanged',
                            payload: { isOpen: true }
                        }, '*');
                    }
                },

                onDropdownClose: function() {
                    if (this.isOpen) {
                        this.isOpen = false;
                        window.postMessage({
                            type: 'ide/dropdownStateChanged', 
                            payload: { isOpen: false }
                        }, '*');
                    }
                },

                checkDropdownState: function() {
                    const now = Date.now();
                    if (now - this.lastCheck < 50) return;
                    this.lastCheck = now;

                    const dropdownSelectors = [
                        '[role="menu"]',
                        '[role="listbox"]', 
                        '[role="combobox"][aria-expanded="true"]',
                        '.dropdown-menu:not(.hidden)',
                        '.menu:not(.hidden)',
                        '.popover:not(.hidden)',
                        '[data-state="open"]',
                        '[aria-hidden="false"][role="dialog"]',
                        '.visible[role="menu"]'
                    ];

                    let foundOpen = false;
                    for (const selector of dropdownSelectors) {
                        const elements = document.querySelectorAll(selector);
                        for (const el of elements) {
                            const rect = el.getBoundingClientRect();
                            const isVisible = rect.width > 0 && rect.height > 0 && 
                                            el.offsetParent !== null &&
                                            getComputedStyle(el).display !== 'none' &&
                                            getComputedStyle(el).visibility !== 'hidden';
                            if (isVisible) {
                                foundOpen = true;
                                break;
                            }
                        }
                        if (foundOpen) break;
                    }

                    if (foundOpen && !this.isOpen) {
                        this.onDropdownOpen();
                    } else if (!foundOpen && this.isOpen) {
                        this.onDropdownClose();
                    }
                }
            };

            const observer = new MutationObserver(function(mutations) {
                let shouldCheck = false;
                mutations.forEach(function(mutation) {
                    if (mutation.type === 'attributes' || mutation.type === 'childList') {
                        shouldCheck = true;
                    }
                });
                if (shouldCheck) {
                    setTimeout(() => window.refactDropdownTracker.checkDropdownState(), 10);
                }
            });

            observer.observe(document.body, {
                attributes: true,
                childList: true,
                subtree: true,
                attributeFilter: ['class', 'style', 'aria-hidden', 'data-state', 'aria-expanded']
            });

            document.addEventListener('click', function(e) {
                setTimeout(() => window.refactDropdownTracker.checkDropdownState(), 50);
            }, true);

            document.addEventListener('focusin', function(e) {
                setTimeout(() => window.refactDropdownTracker.checkDropdownState(), 50);
            }, true);

            document.addEventListener('focusout', function(e) {
                setTimeout(() => window.refactDropdownTracker.checkDropdownState(), 100);
            }, true);

            setInterval(() => window.refactDropdownTracker.checkDropdownState(), 100);

            setTimeout(() => {
                window.refactDropdownTracker.checkDropdownState();
            }, 500);
        """.trimIndent()

        val setupAlarm = Alarm(this)
        setupAlarm.addRequest({
            try {
                browser.webView.cefBrowser.executeJavaScript(trackingScript, null, 0)
                logger.info("Enhanced dropdown state tracking script injected")
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
        val sanitizedFileName = this.sanitizeFileNameForPosix(fileName)
        val file = File(sanitizedFileName)
        logger.warn("handleOpenFile: $fileName")
        invokeLater {
            val vf = VfsUtil.findFileByIoFile(file, true) ?: return@invokeLater
            val fileDescriptor = OpenFileDescriptor(project, vf)
            val editor = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
            logger.warn("handleOpenFile: $fileName found")
            line?.let {
                editor?.caretModel?.moveToLogicalPosition(LogicalPosition(line, 0))
            }
        }
    }

    private fun deleteFile(fileName: String) {
        logger.warn("deleteFile: $fileName")
        ApplicationManager.getApplication().invokeLater {
            LocalFileSystem.getInstance().findFileByPath(fileName)?.delete(this.project)
        }
    }

    private fun sanitizeFileNameForPosix(fileName: String): String {
        val patterns = listOf(
            Regex("""^\\\\\\\\\?\\.*""") to 5, // '\\\\?\\' prefix
            Regex("""^\\\\\?\\[^\\].*""") to 4,     // '\\?\' prefix
            Regex("""^\\\\\?\\\\.*""") to 5,     // '\\?\\' prefix
            Regex("""^\\\?\\.*""") to 3        // '\?\' prefix
        )

        for ((pattern, length) in patterns) {
            if (pattern.containsMatchIn(fileName))
                return fileName.substring(length)
        }

        return fileName
    }

    private fun openNewFile(fileName: String): File {
        val sanitizedFileName = this.sanitizeFileNameForPosix(fileName)
        val file = File(sanitizedFileName)
        if (!file.exists()) {
            file.createNewFile()
        }
        val fileSystem = StandardFileSystems.local()
        fileSystem.refresh(false)
        logger.warn("openNewFileWithContent: $fileName")

        return file
    }

    private fun setContent(fileName: String, content: String) {
        logger.warn("setContent: item.fileNameEdit = $fileName")
        ApplicationManager.getApplication().invokeLater {
            val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(fileName)
            if (file == null) {
                logger.warn("setContent: item.fileNameEdit = $fileName is null")
                return@invokeLater
            }

            FileDocumentManager.getInstance().getDocument(file)?.setText(content)
        }
    }

    private fun handlePatchApply(payload: Events.Patch.ApplyPayload) {
        payload.items.forEach { item ->
            if (item.fileNameAdd != null) {
                val fileName = this.sanitizeFileNameForPosix(item.fileNameAdd)
                logger.warn("handlePatchApply: item.fileNameAdd = $fileName")
                this.openNewFile(fileName)
                setContent(fileName, item.fileText)
            }

            if (item.fileNameDelete != null) {
                val fileName = this.sanitizeFileNameForPosix(item.fileNameDelete)
                logger.warn("handlePatchApply: item.fileNameDelete = $fileName")
                this.deleteFile(fileName)
            }

            if (item.fileNameEdit != null) {
                val fileName = this.sanitizeFileNameForPosix(item.fileNameEdit)
                logger.warn("handlePatchApply: item.fileNameEdit = $fileName")
                setContent(fileName, item.fileText)
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

    private fun handlePatchShow(payload: Events.Patch.ShowPayload) {
        payload.results.forEach { result ->
            if (result.fileNameAdd != null) {
                val sanitizedFileNameEdit = this.sanitizeFileNameForPosix(result.fileNameAdd)
                logger.warn("handlePatchShow: item.fileNameAdd = $sanitizedFileNameEdit")
                this.openNewFile(sanitizedFileNameEdit)
                showPatch(sanitizedFileNameEdit, result.fileText)
            }
            if (result.fileNameDelete != null) {
                logger.warn("handlePatchShow: item.fileNameDelete = ${this.sanitizeFileNameForPosix(result.fileNameDelete)}")
                this.deleteFile(this.sanitizeFileNameForPosix(result.fileNameDelete))
            }

            if (result.fileNameEdit != null) {
                val sanitizedFileNameEdit = this.sanitizeFileNameForPosix(result.fileNameEdit)
                logger.warn("handlePatchShow: item.fileNameEdit = $sanitizedFileNameEdit")
                showPatch(sanitizedFileNameEdit, result.fileText)
            }
        }
    }

    private fun handleAnimationStart(fileName: String) {
        synchronized(this) { // action thread
            val sanitizedFileName = this.sanitizeFileNameForPosix(fileName)
            if (animatedFiles.contains(sanitizedFileName)) return
            animatedFiles.add(sanitizedFileName)
            val file = ApplicationManager.getApplication().runReadAction<VirtualFile?> {
                LocalFileSystem.getInstance().findFileByPath(sanitizedFileName)
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
                            animatedFiles.contains(sanitizedFileName)
                        }
                    }
                }
            }
        }
    }

    private fun handleAnimationStop(fileName: String) {
        synchronized(this) {
            animatedFiles.remove(fileName)
            val sanitizedFileName = this.sanitizeFileNameForPosix(fileName)
            ApplicationManager.getApplication().invokeLater {
                val virtualFile = LocalFileSystem.getInstance().findFileByPath(sanitizedFileName)
                virtualFile?.refresh(true, false)
            }
        }
    }

    private fun handleToolCall(payload: Events.IdeAction.ToolCallPayload) {
        when (val toolCall = payload.toolCall) {
            is TextDocToolCall.CreateTextDocToolCall -> {
                val path = this.sanitizeFileNameForPosix(toolCall.function.arguments.path)
                val content = payload.edit.fileAfter
                createAndSetFileContent(path, content, payload.chatId, toolCall.id)
            }
            is TextDocToolCall.UpdateTextDocToolCall -> {
                val path = this.sanitizeFileNameForPosix(toolCall.function.arguments.path)
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true); },
                    {_, _, _ ->  handleFileAction(toolCall.id, payload.chatId, false); }
                )
            }
            is TextDocToolCall.ReplaceTextDocToolCall -> {
                val path = this.sanitizeFileNameForPosix(toolCall.function.arguments.path)
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true); },
                    {_, _, _ ->  handleFileAction(toolCall.id, payload.chatId, false); }
                )
            }
            is TextDocToolCall.UpdateRegexTextDocToolCall -> {
                val path = this.sanitizeFileNameForPosix(toolCall.function.arguments.path)
                showPatch(
                    path,
                    payload.edit.fileAfter,
                    { _, _, _ -> handleFileAction(toolCall.id, payload.chatId, true); },
                    {_, _, _ ->  handleFileAction(toolCall.id, payload.chatId, false); }
                )
            }
            else -> {
                // Apply the edit to a file with diff
            }
        }
    }


    private fun writeContentToVirtualFile(virtualFile: VirtualFile, content: String) {
        return ApplicationManager.getApplication().runWriteAction {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
            if (document != null) {
                document.setText(content)
            }
        }
    }
    private fun openVirtualFileInIde(virtualFile: VirtualFile) {
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFile(virtualFile, true)
    }

    private fun createAndSetFileContent(path: String, content: String, chatId: String, toolCallId: String) {
        ApplicationManager.getApplication().invokeLater {
            try {
                val file = File(path)
                file.parentFile.mkdirs()
                file.createNewFile()
                val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                if (virtualFile != null) {
                    writeContentToVirtualFile(virtualFile, content)
                    openVirtualFileInIde(virtualFile)
                    handleFileAction(toolCallId, chatId, true)
                } else {
                    handleFileAction(toolCallId, chatId, false)
                }
            } catch (e: Exception) {
                logger.error("Error creating or setting file content", e)
                handleFileAction(toolCallId, chatId, false)
            }
        }
    }


    private fun handleFileAction(toolCallId: String, chatId: String, saved: Boolean) {
        println("handleFileAciton")
        val actionPayload = Events.IdeAction.ToolCallResponsePayload(toolCallId, chatId, saved)
        val action = Events.IdeAction.ToolCallResponse(actionPayload)
        println(action);
        postMessage(action)
    }

    private suspend fun handleEvent(event: Events.FromChat) {
//        logger.warn("${event.toString()} ${event.payload.toString()}")
        when (event) {
            is Events.Editor.PasteDiff -> this.handlePasteDiff(event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.content)
            is Events.OpenSettings -> this.handleOpenSettings()
            is Events.Setup.SetupHost -> this.handleSetupHost(event.host)
            is Events.Setup.OpenExternalUrl -> this.openExternalUrl(event.url)
            is Events.Setup.LogOut -> this.logOut()
            is Events.Fim.Request -> this.handleFimRequest()
            is Events.OpenHotKeys -> this.handleOpenHotKeys()
            is Events.OpenFile -> this.handleOpenFile(event.payload.filePath, event.payload.line)
            is Events.Patch.Apply -> this.handlePatchApply(event.payload)
            is Events.Patch.Show -> this.handlePatchShow(event.payload)
            is Events.Animation.Start -> this.handleAnimationStart(event.fileName)
            is Events.Animation.Stop -> this.handleAnimationStop(event.fileName)
            is Events.IsChatStreaming -> {
                isChatStreaming = event.payload as Boolean
            }

            is Events.ChatPageChange -> {
                currentPage = event.payload.toString()
            }

            is Events.IdeAction.ToolCall -> {
                this.handleToolCall(event.payload)
            }
            is Events.Editor.ForceReloadFileByPath -> {
                this.handleForceReloadFileByPath(event.path)
            }

            is Events.Editor.ForceReloadProjectTreeFiles -> {
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

            else -> Unit
        }
    }

    private val browser by lazy {
        ChatWebView(
            this.editor
        ) { event ->
            runBlocking {
                handleEvent(event)
            }
        }
    }

    val webView by lazy {
//        System.setProperty("ide.browser.jcef.log.level", "info")
        browser.webView
    }

    private fun postMessage(message: Events.ToChat<*>?) {
        synchronized(this) {
            if (message != null) {
                messageQuery.add(message)
            }
        }
    }

    override fun dispose() {
        webView.dispose()
        scheduler.shutdownNow()
        workerFuture?.cancel(true)
        workerScheduler.shutdownNow()
        Disposer.dispose(this)
    }

}

