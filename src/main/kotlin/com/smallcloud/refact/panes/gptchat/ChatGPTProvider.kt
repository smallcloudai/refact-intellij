package com.smallcloud.refact.panes.gptchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refact.PluginState
import com.smallcloud.refact.Resources.defaultChatUrlSuffix
import com.smallcloud.refact.account.AccountManager
import com.smallcloud.refact.io.AsyncConnection
import com.smallcloud.refact.io.ConnectionStatus
import com.smallcloud.refact.io.InferenceGlobalContext
import com.smallcloud.refact.io.InferenceGlobalContext.codifyInferenceUri
import com.smallcloud.refact.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refact.panes.gptchat.structs.ChatGPTRequest
import com.smallcloud.refact.panes.gptchat.structs.ParsedText
import com.smallcloud.refact.panes.gptchat.ui.MessageComponent
import com.smallcloud.refact.panes.gptchat.utils.MsgBuilder
import com.smallcloud.refact.panes.gptchat.utils.md2html
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URI
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refact.io.ConnectionManager.Companion.instance as ConnectionManager

class ChatGPTProvider : ActionListener {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "CodifyChatProviderScheduler", 1
    )
    private val streamScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "CodifyChatProviderStreamScheduler", 1
    )

    private var connection: AsyncConnection? = null
    private var processTask: Future<*>? = null

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
            doActionPerformed(e.source as ChatGPTPane)
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    fun doActionPerformed(pane: ChatGPTPane, msg: String? = null, selectedText: String? = null) {
        if (connection == null) return
        var text: String = msg ?: pane.searchTextArea.textArea.text
        LOG.info("ChatGPT Search: $text")
        if (text.isEmpty()) {
            return
        }
        pane.searchTextArea.textArea.text = ""

        pane.state.pushQuestion(text)
        if (selectedText != null) {
            pane.state.pushCode(selectedText)
            text += "\n\n```\n$selectedText\n```\n"
        }

        pane.add(MessageComponent(md2html(text), true))
        val message = MessageComponent(listOf(ParsedText("...", "...", false)), false)
        pane.add(message)
        val req = ChatGPTRequest(codifyInferenceUri!!.resolve(defaultChatUrlSuffix),
            AccountManager.apiKey, pane.state.conversations)
        processTask = scheduler.submit {
            process(pane, req, message)
        }
    }

    private fun process(pane: ChatGPTPane, req: ChatGPTRequest, message: MessageComponent) {
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
                                pane.state.pushAnswer(s)
                                val lastAnswer = pane.state.lastAnswer()
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
                    handleInterruptedException(requestFuture)
                } catch (e: InterruptedIOException) {
                    handleInterruptedException(requestFuture)
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
    fun cancelOrClose() {
        try {
            processTask?.cancel(true)
            processTask?.get(1, TimeUnit.SECONDS)
        } catch (_: CancellationException) {
        } finally {
            if (InferenceGlobalContext.status != ConnectionStatus.DISCONNECTED) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
//            completionInProgress = false
            processTask = null
//            completionLayout?.dispose()
//            completionLayout = null
        }
    }


    private fun handleInterruptedException(requestFuture: Future<*>?) {
        InferenceGlobalContext.status = ConnectionStatus.CONNECTED
        requestFuture?.cancel(true)
        LOG.debug("lastReqJob abort")
    }
    companion object {
        private val LOG: Logger = Logger.getInstance(ChatGPTProvider::class.java)
    }
}