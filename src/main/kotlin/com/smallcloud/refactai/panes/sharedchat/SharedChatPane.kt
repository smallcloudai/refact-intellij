package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ide.BrowserUtil
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.SelectionEvent
import com.intellij.openapi.editor.event.SelectionListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.FileInfoPayload
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.AppSettingsConfigurable
import com.smallcloud.refactai.settings.Host
import org.jetbrains.annotations.NotNull
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JPanel
import javax.swing.UIManager


class SharedChatPane(val project: Project) : JPanel(), Disposable {

    var id: String? = null;

    private fun getLanguage(fm: FileEditorManager): Language? {
        val editor = fm.selectedTextEditor
        val language = editor?.document?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(it)?.language
        }

        return language
    }

    // TODO: id isn't part of the payload
    private fun sendSelectedSnippet(id: String) {
        this.getSelectedSnippet { snippet ->
            val payload = Editor.SetSnippetPayload(id, snippet)
            val message = Editor.SetSnippetToChat(payload)
            this.postMessage(message)
        }
    }

    // TODO: id isn't needed anymore
    private fun sendUserConfig(id: String) {
        val hasAst = AppSettingsState.instance.astIsEnabled
        val hasVecdb = AppSettingsState.instance.vecdbIsEnabled
        val features = Events.Config.Features(hasAst, hasVecdb)
        val isDarkMode = UIUtil.isUnderDarcula()
        val mode = if (isDarkMode) "dark" else "light"
        val themeProps = Events.Config.ThemeProps(mode)
        val message = Events.Config.Update(id, features, themeProps)
        this.postMessage(message)
    }

    private fun getSelectedSnippet(cb: (Events.Editor.Snippet) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor
                val file = fileEditorManager.selectedFiles[0]
                val path = file.path
                val name = file.name
                val language = this.getLanguage(fileEditorManager)?.id
                val caretModel = editor?.caretModel

                val selection = caretModel?.currentCaret?.selectionRange
                val range = TextRange(selection?.startOffset ?: 0, selection?.endOffset ?: 0)

                val code = editor?.document?.getText(range)
                if (language == null || code == null) {
                    cb(Events.Editor.Snippet())
                } else {
                    val snippet = Events.Editor.Snippet(language, code, path, name)
                    cb(snippet)
                }
            }
        }
    }

    private fun getActiveFileInfo(cb: (Events.ActiveFile.FileInfo) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if (!project.isDisposed && FileEditorManager.getInstance(project).selectedFiles.isNotEmpty()) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor

                val cursor = editor?.caretModel?.offset
                val virtualFile = fileEditorManager.selectedFiles[0]
                val filePath = virtualFile.path
                val fileName = virtualFile.name

                val selection = editor?.caretModel?.currentCaret?.selectionRange
                val range = TextRange(selection?.startOffset ?: 0, selection?.endOffset ?: 0)

                val code = editor?.document?.getText(range)

                val canPaste = selection != null && !selection.isEmpty

                val fileInfo = Events.ActiveFile.FileInfo(
                    fileName,
                    filePath,
                    canPaste,
                    cursor = cursor,
                    line1 = selection?.startOffset,
                    line2 = selection?.endOffset,
                    content = code,
                )
                cb(fileInfo)

            } else {
                val fileInfo = Events.ActiveFile.FileInfo()
                cb(fileInfo)
            }
        }
    }


    // TODO: id isn't part of the payload
    private fun sendActiveFileInfo(id: String) {
        this.getActiveFileInfo { file ->
            val payload = FileInfoPayload(id, file)
            val message = ActiveFileToChat(payload)
            this.postMessage(message)
        }
    }

    private fun handleSetupHost(host: Host) {
        val accountManager = AccountManager.instance;
        val settings = AppSettingsState.instance
        when (host) {
            is Host.CloudHost -> {
                accountManager.apiKey = host.apiKey;
                settings.userInferenceUri = "refact";
                ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin(true)
            }
            is Host.Enterprise -> {
                accountManager.apiKey = host.apiKey
                settings.userInferenceUri = host.endpointAddress
            }
            is Host.SelfHost -> {
                accountManager.apiKey = "any-key-will-work"
                settings.userInferenceUri = host.endpointAddress
            }
        }
    }

    private fun openExternalUrl(url: String) {
        BrowserUtil.browse(url)
    }

    private fun logOut() {
        AccountManager.instance.logout()
    }

    private fun handlePaste(id: String, content: String) {
        ApplicationManager.getApplication().invokeLater {
            val editor = FileEditorManager.getInstance(project).selectedTextEditor
            val selection = editor?.caretModel?.currentCaret?.selectionRange
            if (selection != null) {
                WriteCommandAction.runWriteCommandAction(project) {
                    editor.document.replaceString(selection.startOffset, selection.endOffset, content)
                }
            }
        }
    }

    private fun handleNewFile(id: String, content: String) {
        // TODO: file type?
        val vf = LightVirtualFile("Untitled", content)

        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
        }
    }


    private fun addEventListeners(id: String) {
        println("Adding ide event listeners")
        val listener: FileEditorManagerListener = object : FileEditorManagerListener {
            override fun fileOpened(@NotNull source: FileEditorManager, @NotNull file: VirtualFile) {
                this@SharedChatPane.sendActiveFileInfo(id)
            }

            override fun fileClosed(@NotNull source: FileEditorManager, @NotNull file: VirtualFile) {
                this@SharedChatPane.sendActiveFileInfo(id)
            }

            override fun selectionChanged(@NotNull event: FileEditorManagerEvent) {
                this@SharedChatPane.sendActiveFileInfo(id)
                this@SharedChatPane.sendSelectedSnippet(id)
            }

        }

        val selectionListener = object : SelectionListener {
            override fun selectionChanged(event: SelectionEvent) {
                this@SharedChatPane.sendActiveFileInfo(id)
                this@SharedChatPane.sendSelectedSnippet(id)
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
                    println("ast changed to: $newValue")
                    this@SharedChatPane.sendUserConfig(id)
                }

                override fun vecdbFlagChanged(newValue: Boolean) {
                    println("vecdb changed to: $newValue")
                    this@SharedChatPane.sendUserConfig(id)
                }
            })
    }

    private fun setLookAndFeel() {
        this.browser.setStyle()
        if (this.id != null) {
            this.sendUserConfig(this.id!!)
        }
    }

    private val uiChangeListener = PropertyChangeListener { event ->
        if (event.propertyName == "lookAndFeel") {
            this.setLookAndFeel()
        }
    }


    // TODO: add the event listeners without waiting for ready message
    private fun handleReadyMessage(id: String) {
        this.id = id;
        // active file info can bee added in the intaial state
        this.sendActiveFileInfo(id)
        this.sendSelectedSnippet(id)
        this.addEventListeners(id)
        this.sendUserConfig(id)
        // this.maybeRestore(id)
    }


    private fun handleOpenSettings(id: String) {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AppSettingsConfigurable::class.java)
        }
    }

    private fun handleFimRequest() {
        // TODO: get fill in the middle data from last completion.
        val message = Events.Fim.Error("not setup yet")
        postMessage(message)
    }

    // TODO: handleOpenHotKeys

    private fun handleOpenHotKeys() {
        // TODO: handle open hotkey
    }

    private fun handleOpenFile(fileName: String, line: Int?) {
        // TODO: handle opening file
    }

    private fun handleEvent(event: Events.FromChat) {
        println("Event received: $event")
        when (event) {
            is Events.Editor.Paste -> this.handlePaste(event.id, event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.id, event.content)
            is Events.OpenSettings -> this.handleOpenSettings(event.id)
            is Events.Setup.SetupHost -> this.handleSetupHost(event.host)
            is Events.Setup.OpenExternalUrl -> this.openExternalUrl(event.url)
            is Events.Setup.LogOut -> this.logOut()
            is Events.Fim.Request -> this.handleFimRequest()
            is Events.OpenHotKeys -> this.handleOpenHotKeys()
            is Events.OpenFile -> this.handleOpenFile(event.payload.fileName, event.payload.line)

            else -> Unit
        }
    }

    private val browser by lazy {
        ChatWebView { event ->
            this.handleEvent(event)
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
}

