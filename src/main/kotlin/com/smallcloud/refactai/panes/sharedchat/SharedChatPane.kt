package com.smallcloud.refactai.panes.sharedchat

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
import com.intellij.ui.components.JBLayeredPane
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.Tool
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.ActiveFileToChat
import com.smallcloud.refactai.panes.sharedchat.Events.ActiveFile.FileInfoPayload
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.RestorePayload
import com.smallcloud.refactai.panes.sharedchat.Events.Chat.RestoreToChat
import com.smallcloud.refactai.panes.sharedchat.Events.Editor
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.AppSettingsConfigurable
import org.jetbrains.annotations.NotNull
import java.awt.GridLayout
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextPane
import javax.swing.UIManager


class SharedChatPane(val project: Project) : JPanel(), Disposable {

    private val lsp: LSPProcessHolder = LSPProcessHolder.getInstance(project)

    var id: String? = null;
    private var defaultChatModel: String? = null
    private var chatThreadToRestore: Events.Chat.Thread? = null
    private var lastProcess: CompletableFuture<Future<*>>? = null;


    private fun getLanguage(fm: FileEditorManager): Language? {
        val editor = fm.selectedTextEditor
        val language = editor?.document?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(it)?.language
        }

        return language
    }

    private fun sendSelectedSnippet(id: String) {
        this.getSelectedSnippet { snippet ->
            val payload = Editor.SetSnippetPayload(id, snippet)
            val message = Editor.SetSnippetToChat(payload)
            this.postMessage(message)
        }
    }

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

    private fun sendActiveFileInfo(id: String) {
        this.getActiveFileInfo { file ->
            val payload = FileInfoPayload(id, file)
            val message = ActiveFileToChat(payload)
            this.postMessage(message)
        }
    }

    private fun handleCaps(id: String) {
        this.lsp.fetchCaps().also { caps ->
            val res = caps.get()
            val message: Events.Caps.Receive = Events.Caps.Receive(id, res)
            this.defaultChatModel = res.codeChatDefaultModel
            this.postMessage(message)
        }
    }

    private fun handleSystemPrompts(id: String) {
        this.lsp.fetchSystemPrompts().also { res ->
            val prompts: SystemPromptMap = res.get()
            val payload = Events.SystemPrompts.SystemPromptsPayload(id, prompts)
            val message: Events.SystemPrompts.Receive = Events.SystemPrompts.Receive(payload)
            this.postMessage(message)
        }
    }

    private fun handleCompletion(
        id: String,
        query: String,
        cursor: Int,
        number: Int = 5,
    ) {
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                this.lsp.fetchCommandCompletion(query, cursor, number).also { res ->
                    val completions = res.get()
                    val payload = Events.AtCommands.Completion.CompletionPayload(
                        id, completions.completions, completions.replace, completions.isCmdExecutable
                    )
                    val message = Events.AtCommands.Completion.Receive(payload)
                    this.postMessage(message)
                }
            } catch (e: Exception) {
                println("Commands error")
                println(e)
            }
        }
    }

    private fun handleChat(
        id: String,
        messages: ChatMessages,
        model: String,
        tools: Array<Tool> = emptyArray(),
        title: String? = null) {

        val future = this.lsp.sendChat(
            id = id,
            messages = messages,
            model = model,
            takeNote = false,
            tools = tools,
            dataReceived = { str, requestId ->
                when (val res = Events.Chat.Response.parse(str)) {

                    is Events.Chat.Response.Choices -> {
                        val message = Events.Chat.Response.formatToChat(res, requestId)
                        this.postMessage(message)
                    }

                    is Events.Chat.Response.UserMessage -> {
                        val message = Events.Chat.Response.formatToChat(res, requestId)
                        this.postMessage(message)
                    }

                    is Events.Chat.Response.ToolMessage -> {
                        val message = Events.Chat.Response.formatToChat(res, requestId)
                        this.postMessage(message)
                    }

                    is Events.Chat.Response.DetailMessage -> {
                        val message = Events.Chat.Response.formatToChat(res, requestId)
                        this.postMessage(message)
                    }
                }
            },
            dataReceiveEnded = { str ->
                val res = Events.Chat.Response.ChatDone(str)
                val message = Events.Chat.Response.formatToChat(res, id)
                this.postMessage(message)
            },
            errorDataReceived = { json ->
                val res = Events.Chat.Response.ChatError(json)
                val message = Events.Chat.Response.formatToChat(res, id)
                this.postMessage(message)
            },
            failedDataReceiveEnded = { e ->
                val res = Events.Chat.Response.ChatFailedStream(e)
                val message = Events.Chat.Response.formatToChat(res, id)
                this.postMessage(message)
            })

        this.lastProcess = future

    }

    private fun handleChatSave(id: String, messages: ChatMessages, maybeModel: String) {
        val model = maybeModel.ifEmpty { this.defaultChatModel ?: "" }
        ChatHistory.instance.state.save(id, messages, model)
    }

    fun handleChatStop(id: String) {
        // TODO: stop the stream from the lsp
        this.lastProcess?.get()?.cancel(true)
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

    fun restoreWhenReady(id: String, messages: ChatMessages, model: String) {
        val chatThread = Events.Chat.Thread(id, messages, model)
        this.chatThreadToRestore = chatThread
    }

    private fun maybeRestore(id: String) {
        if (this.chatThreadToRestore != null) {
            val payload = RestorePayload(id, this.chatThreadToRestore!!)
            val event = RestoreToChat(payload)
            this.id = payload.id
            this.postMessage(event)
            this.chatThreadToRestore = null
        }
    }

    private fun handlePreviewFileRequest(id: String, query: String) {
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                this.lsp.fetchCommandPreview(query).also { res ->
                    val preview = res.get()
                    val payload = Events.AtCommands.Preview.PreviewPayload(id, preview.messages)
                    val message = Events.AtCommands.Preview.Receive(payload)
                    this.postMessage(message)
                }
            } catch (e: Exception) {
                println("Command preview error")
                println(e)
            }
        }
    }

    private fun handleReadyMessage(id: String) {
        this.id = id;
        this.sendActiveFileInfo(id)
        this.sendSelectedSnippet(id)
        this.addEventListeners(id)
        this.sendUserConfig(id)
        this.maybeRestore(id)
    }

    private fun handleToolsRequest(id: String) {
         this.lsp.getAvailableTools().also {
             val tool = it.get()
             val payload = Events.Tools.ResponsePayload(id, tool)
             val message = Events.Tools.Resppnse(payload)
             this.postMessage(message)
        }
    }

    private fun handleOpenSettings(id: String) {
        ApplicationManager.getApplication().invokeLater {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, AppSettingsConfigurable::class.java)
        }
    }

    private fun handleEvent(event: Events.FromChat) {
        // println("Event received: ${event}")
        when (event) {
            is Events.Ready -> this.handleReadyMessage(event.id)
            is Events.Caps.Request -> this.handleCaps(event.id)
            is Events.SystemPrompts.Request -> this.handleSystemPrompts(event.id)
            is Events.AtCommands.Completion.Request -> this.handleCompletion(
                event.id, event.query, event.cursor, event.number
            )

            is Events.AtCommands.Preview.Request -> this.handlePreviewFileRequest(event.id, event.query)
            is Events.Chat.AskQuestion -> this.handleChat(event.id, event.messages, event.model, event.tools, event.title)
            is Events.Chat.Save -> this.handleChatSave(event.id, event.messages, event.model)
            is Events.Chat.Stop -> this.handleChatStop(event.id)
            is Events.Editor.Paste -> this.handlePaste(event.id, event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.id, event.content)
            is Events.Tools.Request -> this.handleToolsRequest(event.id)
            is Events.OpenSettings -> this.handleOpenSettings(event.id)
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

    private fun postMessage(message: Events.ToChat?) {
        this.browser.postMessage(message)
    }


    override fun dispose() {
        UIManager.removePropertyChangeListener(uiChangeListener)
        webView.dispose()
        Disposer.dispose(this)
    }
}

