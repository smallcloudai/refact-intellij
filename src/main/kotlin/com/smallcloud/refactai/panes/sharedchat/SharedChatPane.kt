package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.intellij.util.ui.UIUtil
import com.smallcloud.refactai.lsp.LSPProcessHolder
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.annotations.NotNull
import java.beans.PropertyChangeListener
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.UIManager


class SharedChatPane (val project: Project): JPanel(), Disposable {

    private val jsPoolSize = "200"
    private val lsp: LSPProcessHolder = LSPProcessHolder()

    var id: String? = null;
    var defaultChatModel: String? = null

    var chatThreadToRestore: Events.Chat.Thread? = null

    private var lastProcess: CompletableFuture<Future<*>>? = null;

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    private fun getLanguage(fm: FileEditorManager): Language? {
        val editor = fm.selectedTextEditor
        val language = editor?.document?.let {
            PsiDocumentManager.getInstance(project).getPsiFile(it)?.language
        }

        return language
    }

    private fun sendSelectedSnippet(id: String) {
        this.getSelectedSnippet { snippet ->
            if(snippet != null) {
                val type = EventNames.ToChat.SET_SELECTED_SNIPPET.value
                val payload = JsonObject()
                payload.addProperty("id", id)
                payload.add("snippet", Gson().toJsonTree(snippet))
                val messageObj = JsonObject()
                messageObj.addProperty("type", type)
                messageObj.add("payload", payload)
                val message = Gson().toJson(messageObj)
                this.postMessage(message)
            }
        }
    }

    private fun getSelectedSnippet(cb: (Events.Editor.Snippet?) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if(project.isDisposed == false) {
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
                    cb(null)
                } else {
                    val snippet = Events.Editor.Snippet(language, code, path, name)
                    cb(snippet)
                }
            }
        }
    }
    private fun getActiveFileInfo(cb: (Events.ActiveFile.FileInfo?) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            if(project.isDisposed == false) {
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
            }
        }
    }

    private fun sendActiveFileInfo(id: String) {

            this.getActiveFileInfo { file ->
                val type = EventNames.ToChat.ACTIVE_FILE_INFO.value
                val payload = JsonObject()
                payload.addProperty("id", id)


                if (file === null) {
                    val fileJson = JsonObject()
                    fileJson.addProperty("can_paste", false)
                    payload.add("file", fileJson)
                    val messageObj = JsonObject()
                    messageObj.addProperty("type", type)
                    messageObj.add("payload", payload)
                    val message = Gson().toJson(messageObj)
                    this.postMessage(message)
                }

                val fileJson = Gson().toJsonTree(file, Events.ActiveFile.FileInfo::class.java)
                payload.add("file", fileJson)
                val messageObj = JsonObject()
                messageObj.addProperty("type", type)
                messageObj.add("payload", payload)
                val message = Gson().toJson(messageObj)
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
            val message: Events.SystemPrompts.Receive = Events.SystemPrompts.Receive(id, prompts)
            this.postMessage(message)
        }
    }

    private fun handleCompletion(
        id: String,
        query: String,
        cursor: Int,
        number: Int,
        trigger: String?
    ) {
        try {
            this.lsp.fetchCommandCompletion(query, cursor, number, trigger).also { res ->
                val completions = res.get()
                val message = Events.AtCommands.Completion.Receive(id, completions)
                this.postMessage(message)
            }
        } catch (_: Exception) {}

        try {
            this.lsp.fetchCommandPreview(query).also { res ->
                val preview = res.get()
                val message = Events.AtCommands.Preview.Receive(id, preview)
                this.postMessage(message)
            }
        } catch(_: Exception) {}

    }

    private fun handleChat(id: String, messages: ChatMessages, model: String, title: String? = null) {
        val gson = GsonBuilder()
            .registerTypeAdapter(Events.Chat.Response.ResponsePayload::class.java, Events.Chat.ResponseDeserializer())
            .registerTypeAdapter(Delta::class.java, DeltaDeserializer())
            .create()

        val future = this.lsp.sendChat(
            id,
            messages,
            model,
            dataReceived = {str, requestId ->

                val res = gson.fromJson(str, Events.Chat.Response.ResponsePayload::class.java)

                when(res) {
                    is Events.Chat.Response.Choices -> {
                        val messageObj = JsonObject()
                        messageObj.addProperty("type", EventNames.ToChat.CHAT_RESPONSE.value)
                        val payloadObj = gson.toJsonTree(res, Events.Chat.Response.Choices::class.java)
                        payloadObj.asJsonObject.addProperty("id", requestId)
                        messageObj.add("payload", payloadObj)
                        val message = gson.toJson(messageObj)
                        this.postMessage(message)
                    }
                    is Events.Chat.Response.UserMessage -> {
                        val messageObj = JsonObject()
                        messageObj.addProperty("type", EventNames.ToChat.CHAT_RESPONSE.value)
                        val payloadObj = gson.toJsonTree(res, Events.Chat.Response.UserMessage::class.java)
                        payloadObj.asJsonObject.addProperty("id", requestId)
                        messageObj.add("payload", payloadObj)
                        val message = gson.toJson(messageObj)
                        println("Message For User")
                        println(message)
                        this.postMessage(message)
                    }
                }
            },
            dataReceiveEnded = { str ->
                println("chat_request_ended $str")
                val messageObj = JsonObject()
                messageObj.addProperty("type", EventNames.ToChat.DONE_STREAMING.value)
                val payloadObj = JsonObject()
                payloadObj.asJsonObject.addProperty("id", id)
                messageObj.add("payload", payloadObj)
                val message = gson.toJson(messageObj)
                this.postMessage(message)
            },
            errorDataReceived = { json ->
                val messageObj = JsonObject()
                messageObj.addProperty("type", EventNames.ToChat.ERROR_STREAMING.value)
                val payloadObj = JsonObject()
                payloadObj.asJsonObject.addProperty("id", id)
                // Maybe has detail property
                payloadObj.asJsonObject.addProperty("message", json.toString())
                messageObj.add("payload", payloadObj)
                val message = gson.toJson(messageObj)
                this.postMessage(message)
            },
            failedDataReceiveEnded = {e ->
                val messageObj = JsonObject()
                messageObj.addProperty("type", EventNames.ToChat.ERROR_STREAMING.value)
                val payloadObj = JsonObject()
                payloadObj.asJsonObject.addProperty("id", id)
                // Maybe has detail property
                payloadObj.asJsonObject.addProperty("message", e.toString())
                messageObj.add("payload", payloadObj)
                val message = gson.toJson(messageObj)
                this.postMessage(message)
            }
        )
        
        this.lastProcess = future

    }

    private fun handleChatSave(id: String, messages: ChatMessages, maybeModel: String) {
        val model = maybeModel.ifEmpty {this.defaultChatModel ?: ""}
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
                    editor.document.insertString(selection.startOffset, content)
                }
            }
        }
    }

    private fun handleNewFile(id: String, content: String) {
        // TODO: file type?
        val vf = LightVirtualFile("Untitled", content)

        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            val e = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
        }
    }

    private fun addEventListener(id: String) {
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

        // TODO: this is marked as to be depricated
        val ef = EditorFactory.getInstance()
        ef.eventMulticaster.addSelectionListener(selectionListener)

        UIManager.addPropertyChangeListener(uiChangeListener)
    }

    private val uiChangeListener = PropertyChangeListener { event ->
        if(event.propertyName == "lookAndFeel") {
            val isDark = UIUtil.isUnderDarcula()
            val className = if(isDark) {"vscode-dark"} else {"vscode-light"}
            val script = """
                    document.body.className = "$className";
                """.trimIndent()
            this.webView.cefBrowser.executeJavaScript(script, this.webView.cefBrowser.url, 0)
        }
    }

    fun restoreWhenReady(id: String, messages: ChatMessages, model: String) {
        val chatThread = Events.Chat.Thread(id, messages, model)
        this.chatThreadToRestore = chatThread
    }

    private fun maybeRestore(id: String) {
        if(this.chatThreadToRestore != null) {
            val event = JsonObject()
            event.addProperty("type", EventNames.ToChat.RESTORE_CHAT.value)
            val payload = JsonObject()
            payload.addProperty("id", id)
            val chat = JsonObject()
            chat.addProperty("id", this.chatThreadToRestore!!.id)
            chat.addProperty("model", this.chatThreadToRestore!!.model)
            chat.addProperty("title", this.chatThreadToRestore!!.title)
            val messages = JsonArray()
            ChatHistory.instance.gson.toJsonTree(this.chatThreadToRestore!!.messages).asJsonArray.forEach {
                val message = it.asJsonObject
                val role = message.get("role")
                val content = message.get("content")
                val arr = JsonArray()
                arr.add(role)
                arr.add(content)
                messages.add(arr)
            }
            chat.add("messages", messages)
            payload.add("chat", chat)
            event.add("payload", payload)
            val json = Gson().toJson(event)
            this.postMessage(json)
            this.id = this.chatThreadToRestore!!.id
            this.chatThreadToRestore = null
        }
    }

    private fun handleEvent(event: Events.FromChat) {

        when (event) {
            is Events.Ready -> {
                this.sendActiveFileInfo(event.id)
                this.addEventListener(event.id)
                this.id = event.id
                this.maybeRestore(event.id)
            }
            is Events.Caps.Request -> this.handleCaps(event.id)
            is Events.SystemPrompts.Request -> this.handleSystemPrompts(event.id)
            is Events.AtCommands.Completion.Request -> this.handleCompletion(event.id, event.query, event.cursor, event.number, event.trigger)
            is Events.Chat.AskQuestion -> this.handleChat(event.id, event.messages, event.model, event.title)
            is Events.Chat.Save -> this.handleChatSave(event.id, event.messages, event.model)
            is Events.Chat.Stop -> this.handleChatStop(event.id)
            is Events.Editor.Paste -> this.handlePaste(event.id, event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.id, event.content)
            else -> Unit
        }
    }

    // TODO: figure out how to detect dark mode
    private fun getHtml(): String {
        val isDarkMode = UIUtil.isUnderDarcula()
        val mode = if (isDarkMode) {"dark" } else { "light" }
        val bodyClass = if(isDarkMode) {"vscode-dark"} else {"vscode-light"}
        return """
        <!doctype html>
        <html lang="en" class="$mode">
           <head>
               <title>Refact.ai</title>
               <meta name="viewport" content="width=device-width, initial-scale=1.0">
               <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/refact-chat-js@0.2/dist/chat/style.css">
               <style>
                 html {
                    height: 100%;
                 }
                  
                 body {
                    margin: 0;
                    min-height: 100%;
                    height: 100%;
                    padding: 0px;
                    margin: 0px;
                 }

                 #refact-chat {
                   height: 100%;
                 }
                 
                 #refact-chat > div > div {
                   /* TODO: dvh isn't supported in jbcef 
                   *  needs at least chrome version 108 https://caniuse.com/viewport-unit-variants,
                   * check with window.navigator.userAgent
                   * tracked here https://youtrack.jetbrains.com/issue/JBR-6782/Update-CEF-to-version-122.1.9-jcef-242-dev
                   */
                   
                   height: 100vh;
                 }
                 
               </style>
           </head>
           <body class=$bodyClass>
               <div id="refact-chat"></div>
           </body>
           <script type="module">
               import * as refactChatJs from 'https://cdn.jsdelivr.net/npm/refact-chat-js@0.2/+esm'

               window.onload = function() {
                   console.log(refactChatJs);
                   const element = document.getElementById("refact-chat");
                   const options = {
                     host: "jetbrains",
                     tabbed: false,
                     themeProps: {
                       accentColor: "gray",
                       scaling: "90%",
                     },
                     // TODO: set the following options to true if the features are enabled
                     features: {
                       vecdb: true,
                       ast: true,
                     }
                   };
                   refactChatJs.render(element, options);
               };

           </script>
        </html>
    """.trimIndent()
    }

    val webView by lazy {
        val browser = JBCefBrowser()
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            jsPoolSize,
        )
        val html = this.getHtml()
        browser.loadHTML(html)

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)

        myJSQueryOpenInBrowser.addHandler { msg ->
            // println("myJSQueryOpenInBrowser: msg: $msg")
            // error with save messages
            val event = Events.parse(msg)
            if(event != null) { this.handleEvent(event) }
            null
        }

        var scriptAdded = false;

        browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if(!scriptAdded) {
                    println("adding script to  browser")
                    val script = """window.postIntellijMessage = function(event) {
                        const msg = JSON.stringify(event);
                        ${myJSQueryOpenInBrowser.inject("msg")}
                    }""".trimIndent()
                    browser.executeJavaScript(script, browser.url, 0);
                    scriptAdded = true;
                }
            }
        }, browser.cefBrowser)

        browser
    }

    private fun postMessage(message: Events.ToChat) {
        val json = Events.stringify(message)
        this.postMessage(json)
    }

    private fun postMessage(message: String) {
        // println("postMessage: $message")
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {

        return webView.component
    }

    override fun dispose() {
        UIManager.removePropertyChangeListener(uiChangeListener)
        webView.dispose()
        Disposer.dispose(this)
    }
}

