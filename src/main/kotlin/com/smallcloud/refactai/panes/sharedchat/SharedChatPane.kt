package com.smallcloud.refactai.panes.sharedchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ui.jcef.*
import org.cef.browser.CefBrowser
import org.cef.handler.CefLoadHandlerAdapter
import javax.swing.JComponent


class SharedChatPane {
    private val JS_POOL_SIZE = "200"

    init {
        System.setProperty("ide.browser.jcef.jsQueryPoolSize", JS_POOL_SIZE)
    }

    val webView by lazy {
        // TODO: add a debugging window
        // TBD: should chats share a browser instance?
        val browser = JBCefBrowser()
        browser.jbCefClient.setProperty(
            JBCefClient.Properties.JS_QUERY_POOL_SIZE,
            JS_POOL_SIZE,
        )
        browser.loadURL("http://127.0.0.1:8001/webgui/chat.html")

        val myJSQueryOpenInBrowser = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
        myJSQueryOpenInBrowser.addHandler { msg ->
            println("event from chat")
            println(msg)
            val json = Gson().fromJson(msg, JsonObject::class.java)
            val type = json.get("type").asString
            val data = json.get("data").asJsonObject
            when (type) {
                // TODO: handle events from chat.html
                "chat-question-enter-hit" -> {

                }
                "open-new-file" -> {

                }
                "stop-clicked" -> {

                }
                "diff-paste-back" -> {

                }
            }

            null
        }

        browser.jbCefClient.addLoadHandler(object: CefLoadHandlerAdapter() {
            override fun onLoadingStateChange(
                browser: CefBrowser,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (!isLoading) {
                    // The page has finished loading
                    println("adding script to  browser")
                    val script = """window.postIntellijMessage = function(type, data) {
                        const msg = JSON.stringify({type, data});
                        ${myJSQueryOpenInBrowser.inject("msg")}
                    }""".trimIndent()
                    browser.executeJavaScript(script, browser.url, 0);

                    // populate the chat with some data.
                    val initData = """{"command":"chat-models-populate","chat_models":["gpt-3.5-turbo"],"chat_use_model":"gpt-3.5-turbo"}"""
                    // val initScript = """window.postMessage($initData, "*");"""
                    // browser.executeJavaScript(initScript, browser.url, 0)
                    postMessage(initData)

                }
            }
        }, browser.cefBrowser)

        browser
    }

    fun getComponent(): JComponent {
        return webView.component
    }
    fun setFireUpOptions(attachFile: String?) {
        val command =  "chat-set-fireup-options"
        val json = JsonObject()
        json.addProperty("command", command)
        if(attachFile != null) {
            json.addProperty("chat_attach_file", attachFile)
        }
        postMessage(json)
    }

    fun chatModelsPopulate(models: Set<String>) {
        val command = "chat-models-populate"
        val json = JsonObject()
        json.addProperty("command", command)
        val modelsJson = Gson().toJson(models)
        json.addProperty("chat_models", modelsJson)
        postMessage(json)
    }

    fun chatEndStreaming() {
        val command = "chat-end-streaming"
        val json = JsonObject()
        json.addProperty("command", command)
        postMessage(json)
    }

    fun chatErrorStreaming(backUserPhrase: String = "", errorMessage: String = "") {
        val command = "chat-error-streaming"
        val json = JsonObject()
        json.addProperty("command", command)
        json.addProperty("backup_user_phrase", backUserPhrase)
        json.addProperty("error_message", errorMessage)
        postMessage(json)
    }

    fun chatPostQuestion(question: String, messageBackup: List<Pair<String, String>>) {
        val command = "chat-post-question"
        val json = JsonObject()
        json.addProperty("command",command)
        val questionHtml = "" // TODO: figure out how to parse markdown
        json.addProperty("question_html", questionHtml)
        json.addProperty("question_ram", question)
        json.addProperty("message_backup", Gson().toJson(messageBackup))
        postMessage(json)
    }

    fun chatPostDecoration() {
        val command = "chat-post-decoration"
        val json = JsonObject()
        json.addProperty("command", command)
        postMessage(json)
    }

    fun chatPostAnswer(question: String, messageBackup: List<Pair<String, String>>) {
        val command = "chat-post-answer"
        val json = JsonObject()
        json.addProperty("command", command)
        val questionHtml = "" // TODO: figure out how to parse markdown
        json.addProperty("question_html", questionHtml)
        json.addProperty("question_ram", question)
        json.addProperty("message_backup", Gson().toJson(messageBackup))
        postMessage(json)
    }

    fun chatSetQuestionText(question: String) {
        val command = "chat-set-question-text"
        val json = JsonObject()
        json.addProperty("command", command)
        val value = mapOf<String, String>("question" to question)
        json.addProperty("value", Gson().toJson(value))
        postMessage(json)
    }

    fun clearChat() {
        val command = "clea-chat"
        val json = JsonObject()
        json.addProperty("command", command)
        postMessage(json)
    }

    fun noop() {
        val json = JsonObject()
        json.addProperty("command", "nop")
        postMessage(json)
    }

    fun postMessage(json: JsonObject) {
        val message = Gson().toJson(json)
        postMessage(message)
    }
    fun postMessage(message: String) {
        val script = """window.postMessage($message, "*");"""
        webView.cefBrowser.executeJavaScript(script, webView.cefBrowser.url, 0)
    }

}