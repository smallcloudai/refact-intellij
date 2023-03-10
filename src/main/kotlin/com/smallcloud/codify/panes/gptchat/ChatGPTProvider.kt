package com.smallcloud.codify.panes.gptchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.Resources.defaultChatUrlSuffix
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.io.AsyncConnection
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.InferenceGlobalContext.codifyInferenceUri
import com.smallcloud.codify.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.codify.panes.gptchat.structs.ChatGPTRequest
import com.smallcloud.codify.panes.gptchat.structs.ParsedText
import com.smallcloud.codify.panes.gptchat.ui.MessageComponent
import com.smallcloud.codify.panes.gptchat.utils.MsgBuilder
import com.smallcloud.codify.panes.gptchat.utils.md2html
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import com.smallcloud.codify.io.ConnectionManager.Companion.instance as ConnectionManager

class ChatGPTProvider(private val pane: ChatGPTPane) : ActionListener, KeyListener {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "CodifyChatProviderScheduler", 1
    )
    private val streamScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "CodifyChatProviderStreamScheduler", 1
    )

    private var connection: AsyncConnection? = null

    init {
        codifyInferenceUri?.let {
            connection = ConnectionManager.getAsyncConnection(it.resolve(defaultChatUrlSuffix))
        }
        ApplicationManager.getApplication().messageBus
                .connect(PluginState.instance)
                .subscribe(
                        InferenceGlobalContextChangedNotifier.TOPIC,
                        object : InferenceGlobalContextChangedNotifier {
                            override fun inferenceUriChanged(newUrl: URI?) {
                                connection = newUrl?.let { ConnectionManager.getAsyncConnection(it.resolve(defaultChatUrlSuffix)) }
                            }
                        }
                )
    }

    override fun actionPerformed(e: ActionEvent) {
        try {
            doActionPerformed()
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    fun doActionPerformed(msg: String? = null, selectedText: String? = null) {
        if (connection == null) return
        var text: String = msg ?: pane.searchTextArea.textArea.text
        LOG.info("ChatGPT Search: $text")
        if (text.isEmpty()) {
            return
        }
        pane.searchTextArea.textArea.text = ""

        State.instance.pushQuestion(text)
        if (selectedText != null) {
            State.instance.pushCode(selectedText)
            text += "\n\n```\n$selectedText\n```\n"
        }

        pane.add(MessageComponent(md2html(text), true))
        val message = MessageComponent(listOf(ParsedText(null, "...", false)), false)
        pane.add(message)
        val req = ChatGPTRequest(codifyInferenceUri!!.resolve(defaultChatUrlSuffix),
            AccountManager.apiKey, State.instance.conversations)
        scheduler.submit {
            process(req, message)
        }
    }

    private fun process(req: ChatGPTRequest, message: MessageComponent) {
        pane.aroundRequest(true)
        try {
            val reqStr = MsgBuilder.build(req.conversation)
            var stop = false
            connection!!.post(req.uri,
                    reqStr,
                    mapOf("Authorization" to "Bearer ${req.token}"),
                    dataReceived = { response ->
                        fun parse(response: String?): String? {
                            val gson = Gson()
                            if (response == "[DONE]") return ""
                            val obj = gson.fromJson(response, JsonObject::class.java)
                            if (stop) return ""
                            if (obj.has("finish_reason") && obj.get("finish_reason").asString == "stop") {
                                stop = true
                            }
                            return obj.get("delta").asString
                        }

                        var line = parse(response)
                        if (line == null) {
                            line = "error"
                        } else if (line.isEmpty()) {
                            return@post
                        }
                        streamScheduler.submit {
                            for (s in line.chunked(1)) {
                                State.instance.pushAnswer(s)
                                val lastAnswer = State.instance.lastAnswer()
                                ApplicationManager.getApplication().invokeLater {
                                    message.setContent(md2html(lastAnswer))
                                    pane.scrollToBottom()
                                }
                                Thread.sleep(2)
                            }
                        }
                    },
                    dataReceiveEnded = {
                        pane.aroundRequest(false)
                    },errorDataReceived = {}
            ).also {
                var requestFuture: Future<*>? = null
                try {
                    requestFuture = it.get()
                    requestFuture.get()
                } catch (e: InterruptedException) {
//                    handleInterruptedException(requestFuture)
                } catch (e: InterruptedIOException) {
//                    handleInterruptedException(requestFuture)
                } catch (e: ExecutionException) {
                    requestFuture?.cancel(true)
//                    catchNetExceptions(e.cause)
//                    lastStatistic?.let {
//                        lastStatistic = null
//                    }
                } catch (e: Exception) {
                    InferenceGlobalContext.status = ConnectionStatus.ERROR
                    InferenceGlobalContext.lastErrorMsg = e.message
//                    cancelOrClose()
//                    lastStatistic?.let {
//                        lastStatistic = null
//                    }
//                    logger.warn("Exception while completion request processing", e)
                }
            }
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
//                MyNotifier.notifyError(
//                    DataFactory.getInstance().getProject(),
//                    ChatGPTBundle.message("notify.timeout.error.title"),
//                    ChatGPTBundle.message("notify.timeout.error.text")
//                )
            pane.aroundRequest(false)
        } catch (ex: Exception) {
            ex.printStackTrace()
            pane.aroundRequest(false)
            throw RuntimeException(ex)
        }
    }

    override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && !e.isControlDown && !e.isShiftDown) {
            e.consume()
            doActionPerformed()
        }
    }
    override fun keyReleased(e: KeyEvent) {}
    override fun keyTyped(e: KeyEvent) {}

    companion object {
        private val LOG: Logger = Logger.getInstance(ChatGPTProvider::class.java)
    }
}