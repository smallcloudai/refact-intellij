package com.smallcloud.refactai.panes.sharedchat

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.processTools.getResultStdoutStr
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.keymap.impl.ui.KeymapPanel
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
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
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.settings.AppSettingsConfigurable
import com.smallcloud.refactai.settings.Host
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.NotNull
import java.beans.PropertyChangeListener
import java.io.File
import javax.swing.JPanel
import javax.swing.UIManager


class SharedChatPane(val project: Project) : JPanel(), Disposable {
    private val logger = Logger.getInstance(SharedChatPane::class.java)
    private val editor = Editor(project)
    var id: String? = null;

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
                    return;
                }
                val fileName = out.lines().last()

                ApplicationManager.getApplication().invokeLater {
                    val virtualFile: VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(fileName))
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

    private fun handleNewFile(content: String) {
        val vf = LightVirtualFile("Untitled", content)
        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
        }
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

        UIManager.addPropertyChangeListener(uiChangeListener)

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

    private val uiChangeListener = PropertyChangeListener { event ->
        if (event.propertyName == "lookAndFeel") {
            this.setLookAndFeel()
        }
    }


    private fun handleOpenSettings() {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AppSettingsConfigurable::class.java)
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
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, KeymapPanel::class.java) {
                it.enableSearch("Refact.ai")
            }
        }
    }

    private fun handleOpenFile(fileName: String, line: Int?) {
        val file = File(fileName)
        val vf = VfsUtil.findFileByIoFile(file, true) ?: return

        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            val editor = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
            line?.let {
                editor?.caretModel?.moveToLogicalPosition(LogicalPosition(line, 0))
            }
        }

    }

    private fun handlePatchApply(payload: Events.Patch.ApplyPayload) {
        payload.items.forEach { item ->
            if (item.fileNameAdd != null) {
                val virtualFile = LightVirtualFile(item.fileNameAdd, item.fileText)
                val fileDescriptor = OpenFileDescriptor(project, virtualFile)
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
                }
            }

            if (item.fileNameEdit != null) {
                val file = LocalFileSystem.getInstance().findFileByPath(item.fileNameEdit)
                if(file != null) {
                    ApplicationManager.getApplication().invokeLater {
                        FileDocumentManager.getInstance().getDocument(file)?.setText(item.fileText)
                    }
                }
            }

            if (item.fileNameDelete != null) {
                LocalFileSystem.getInstance().findFileByPath(item.fileNameDelete)?.delete(this.project)
            }

        }
    }

    private fun handlePatchShow(payload: Events.Patch.ShowPayload) {
        payload.results.forEach { result ->
            if (result.fileNameAdd != null) {
                // Create a new file and open it
                val virtualFile = LightVirtualFile(result.fileNameAdd, result.fileText)
                val fileDescriptor = OpenFileDescriptor(project, virtualFile)
                ApplicationManager.getApplication().invokeLater {
                    FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
                }
            }

            if (result.fileNameEdit != null) {
                // Open the file and add the diff
                val file = LocalFileSystem.getInstance().findFileByPath(result.fileNameEdit)
                if (file != null) {
                    val fileDescriptor = OpenFileDescriptor(project, file)
                    ApplicationManager.getApplication().invokeLater {
                        val editor = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
                        editor?.selectionModel?.setSelection(0, editor.document.textLength)
                        if (editor != null) {
                            ModeProvider.getOrCreateModeProvider(editor).getDiffMode()
                                .actionPerformed(editor, result.fileText)
                        }
                    }
                }
            }


            if (result.fileNameDelete!= null) {
                // delete the file
                LocalFileSystem.getInstance().findFileByPath(result.fileNameDelete)?.delete(this.project)
            }
        }
    }

    private suspend fun handleEvent(event: Events.FromChat) {
        logger.info("Event received: $event")
        when (event) {
            is Events.Editor.PasteDiff -> this.handlePasteDiff(event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.content)
            is Events.OpenSettings -> this.handleOpenSettings()
            is Events.Setup.SetupHost -> this.handleSetupHost(event.host)
            is Events.Setup.OpenExternalUrl -> this.openExternalUrl(event.url)
            is Events.Setup.LogOut -> this.logOut()
            is Events.Fim.Request -> this.handleFimRequest()
            is Events.OpenHotKeys -> this.handleOpenHotKeys()
            is Events.OpenFile -> this.handleOpenFile(event.payload.fileName, event.payload.line)
            is Events.Patch.Apply -> this.handlePatchApply(event.payload)
            is Events.Patch.Show -> this.handlePatchShow(event.payload)

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
        browser.webView
    }

    private fun postMessage(message: Events.ToChat<*>?) {
        this.browser.postMessage(message)
    }


    override fun dispose() {
        UIManager.removePropertyChangeListener(uiChangeListener)
        webView.dispose()
        Disposer.dispose(this)
    }

    init {
        this.addEventListeners()
    }
}

