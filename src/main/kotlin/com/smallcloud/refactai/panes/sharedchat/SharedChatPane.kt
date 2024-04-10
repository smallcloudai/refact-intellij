package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefClient
import com.intellij.ui.jcef.JBCefJSQuery
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.panes.sharedchat.events.*
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent


class SharedChatPane (val project: Project) {
    private val jsPoolSize = "200"
    private val lsp: LSPProcessHolder = LSPProcessHolder()

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", jsPoolSize)
    }

    private fun handleCaps(id: String) {
        // Note: I'll need to look in how to do async effectively in kotlin
        this.lsp.fetchCaps().also { caps ->
            // Handle errors?
            val message: Events.Caps.Receive = Events.Caps.Receive(id, caps.get())
            println("handleCaps: message: $message")
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
        // TODO: send the chat to the lsp and stream back the response
        println("handleChat: id: $id, messages: $messages, model: $model, title: $title")
        val gson = GsonBuilder()
            .registerTypeAdapter(Events.Chat.Response.ResponsePayload::class.java, Events.Chat.ResponseDeserializer())
            .registerTypeAdapter(Delta::class.java, DeltaDeserializer())
            .create()

        this.lsp.sendChat(
            id,
            messages,
            model,
            dataReceived = {str, requestId ->
//                println("chat_request_received")
//                println("str: $str")
                val res = gson.fromJson(str, Events.Chat.Response.ResponsePayload::class.java)

                // println("res: $res, id: $requestId")

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
    }

    private fun handleChatSave(id: String, messages: ChatMessages, model: String, title: String? = null) {
        // TODO: save the chat
        println("handleChatSave: id: $id, messages: $messages, model: $model, title: $title")
    }

    private fun handleChatStop(id: String) {
        // TODO: stop the chat
        println("handleChatStop: id: $id")
    }

    private fun handlePaste(id: String, content: String) {
        // TODO: paste the content
        println("handlePaste: id: $id, content: $content")
    }

    private fun handleNewFile(id: String, content: String) {
        // TODO: file type?
        val vf = LightVirtualFile("Untitled", content)

        val fileDescriptor = OpenFileDescriptor(project, vf)

        ApplicationManager.getApplication().invokeLater {
            val e = FileEditorManager.getInstance(project).openTextEditor(fileDescriptor, true)
        }
    }

    private fun handleEvent(event: Events.FromChat) {

        when (event) {
            is Events.Caps.Request -> this.handleCaps(event.id)
            is Events.SystemPrompts.Request -> this.handleSystemPrompts(event.id)
            is Events.AtCommands.Completion.Request -> this.handleCompletion(event.id, event.query, event.cursor, event.number, event.trigger)
            is Events.Chat.AskQuestion -> this.handleChat(event.id, event.messages, event.model, event.title)
            is Events.Chat.Save -> this.handleChatSave(event.id, event.messages, event.model, event.title)
            is Events.Chat.Stop -> this.handleChatStop(event.id)
            is Events.Editor.Paste -> this.handlePaste(event.id, event.content)
            is Events.Editor.NewFile -> this.handleNewFile(event.id, event.content)
            // copy?
            else -> Unit
        }
    }

    val html = """
        <!doctype html>
        <html lang="en" class="light">
           <head>
               <title>Refact.ai</title>
               <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/refact-chat-js@0.1/dist/chat/style.css">
           </head>
           <body style="height:100%; padding:0px; margin: 0px;">
               <div id="refact-chat" style="height:100%;"></div>
           </body>
           <script type="module">
               import * as refactChatJs from 'https://cdn.jsdelivr.net/npm/refact-chat-js@0.1/+esm'

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
                     features: {
                       vecdb: false,
                       ast: false,
                     }
                   };
                   refactChatJs.render(element, options);
               };

           </script>
        </html>
    """.trimIndent()

    val webView by lazy {
        val browser = JBCefBrowser()
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            jsPoolSize,
        )
        browser.loadHTML(html)

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)

        myJSQueryOpenInBrowser.addHandler { msg ->
            println("myJSQueryOpenInBrowser: msg: $msg")
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

//        val devTools = browser.cefBrowser.devTools
//        val devToolsBrowser = JBCefBrowser.createBuilder()
//            .setCefBrowser(devTools)
//            .setClient(browser.jbCefClient)
//            .build();
//
//        devToolsBrowser.openDevtools()
//
//

        browser
    }

    private fun postMessage(message: Events.ToChat) {
        val json = Events.stringify(message)
        this.postMessage(json)
    }

    private fun postMessage(message: String) {
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

    fun getComponent(): JComponent {
        return webView.component
    }
}