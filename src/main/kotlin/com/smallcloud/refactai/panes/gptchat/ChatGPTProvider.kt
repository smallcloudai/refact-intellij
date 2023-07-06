package com.smallcloud.refactai.panes.gptchat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.Resources.defaultChatUrlSuffix
import com.smallcloud.refactai.Resources.defaultOldStyleChatUrlSuffix
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.panes.gptchat.structs.ChatGPTRequest
import com.smallcloud.refactai.panes.gptchat.structs.ParsedText
import com.smallcloud.refactai.panes.gptchat.ui.MessageComponent
import com.smallcloud.refactai.panes.gptchat.utils.MsgBuilder
import com.smallcloud.refactai.panes.gptchat.utils.makeAttachedFile
import com.smallcloud.refactai.panes.gptchat.utils.md2html
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.UsageStats
import com.smallcloud.refactai.struct.DeploymentMode
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.aitoolbox.LongthinkFunctionProvider.Companion.instance as LongthinkFunctionProvider
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class ChatGPTProvider : ActionListener {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCChatProviderScheduler", 1
    )

    private var streamScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCChatProviderStreamScheduler", 1
    )
    private var streamSchedulerTasks = mutableListOf<Future<*>>()

    private var processTask: Future<*>? = null
    private var canceled: Boolean = false
    private var cachedFile: String? = null

    private fun reconnect() {
        if (LongthinkFunctionProvider.allChats.isNotEmpty() && InferenceGlobalContext.canRequest()) {
            InferenceGlobalContext.inferenceUri?.let {
                InferenceGlobalContext.checkConnection(it.resolve(
                        if (InferenceGlobalContext.isNewChatStyle || InferenceGlobalContext.isSelfHosted)
                    defaultChatUrlSuffix else defaultOldStyleChatUrlSuffix))
            }
        }
    }

    init {
        reconnect()
        ApplicationManager.getApplication().messageBus
                .connect(PluginState.instance)
                .subscribe(
                        InferenceGlobalContextChangedNotifier.TOPIC,
                        object : InferenceGlobalContextChangedNotifier {
                            override fun deploymentModeChanged(newValue: DeploymentMode) {
                                reconnect()
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

    fun doActionPerformed(pane: ChatGPTPane, msg: String? = null,
                          selectedText: String? = null, editor: Editor? = null) {
        if (cachedFile == null && pane.searchTextArea.needToAttachFile && editor != null) {
            cachedFile = makeAttachedFile(editor)
        }
        var text: String = msg ?: pane.searchTextArea.textArea.text
        LOG.info("ChatGPT Search: $text")
        if (text.isEmpty()) {
            return
        }
        pane.searchTextArea.textArea.text = ""

        pane.state.pushQuestion(text)
        val lastAnswer = pane.state.last()

        fun isEmptyString(lines: String): Boolean {
            return lines.any { it.isLetterOrDigit() }
        }
        if (selectedText?.let { isEmptyString(it) } == true) {
            pane.state.pushCode(selectedText)
            text += "\n\n```\n$selectedText\n```\n"
        }

        pane.add(MessageComponent(md2html(text), true))
        val message = MessageComponent(listOf(ParsedText("...", "...", false)), false)
        val req = ChatGPTRequest(InferenceGlobalContext.inferenceUri!!.resolve(
                if (InferenceGlobalContext.isNewChatStyle || InferenceGlobalContext.isSelfHosted)
                    defaultChatUrlSuffix else defaultOldStyleChatUrlSuffix),
                AccountManager.apiKey, pane.getFullHistory())
        pane.add(message)
        processTask = scheduler.submit {
            process(pane, req, lastAnswer, message)
        }
    }

    private fun process(pane: ChatGPTPane, req: ChatGPTRequest, lastAnswer: State.QuestionAnswer, message: MessageComponent) {
        pane.sendingState = ChatGPTPane.SendingState.PENDING
        canceled = false
        try {
            val reqStr = MsgBuilder.build(req, pane.searchTextArea.selectedModel, cachedFile)
            var stop = false
            val stat = UsageStatistic("chat-tab", pane.searchTextArea.selectedModel.model ?: "")

            InferenceGlobalContext.connection.post(req.uri,
                    reqStr,
                    mapOf("Authorization" to "Bearer ${req.token}"),
                    dataReceived = { response ->
                        fun parse(response: String?): String? {
                            val gson = Gson()
                            val obj = gson.fromJson(response, JsonObject::class.java)
                            if (obj.has("metering_balance")) {
                                AccountManager.meteringBalance = obj.get("metering_balance").asInt
                            }
                            if (stop) return ""
                            return if (obj.has("choices")) {
                                val choice0 = obj.get("choices").asJsonArray[0].asJsonObject
                                if (choice0.has("finish_reason")
                                        && choice0.get("finish_reason").asString == "stop") {
                                    stop = true
                                }
                                choice0.get("delta").asString
                            } else {
                                obj.get("delta").asString
                            }
                        }

                        var line = parse(response)
                        if (line == null) {
                            line = "error"
                        } else if (line.isEmpty()) {
                            return@post
                        }
                        streamSchedulerTasks.add(streamScheduler.submit {
                            for (s in line.chunked(1)) {
                                lastAnswer.pushAnswer(s)
                                ApplicationManager.getApplication().invokeLater({
                                    message.setContent(md2html(lastAnswer.answer))
                                    pane.scrollToBottom()
                                }, {
                                    pane.sendingState == ChatGPTPane.SendingState.READY || canceled
                                })
                                Thread.sleep(2)
                            }
                        })
                    },
                    dataReceiveEnded = {
                        streamSchedulerTasks.add(streamScheduler.submit {
                            pane.sendingState = ChatGPTPane.SendingState.READY
                        })
                        UsageStats.instance.addStatistic(true, stat, req.uri.toString(), "")
                    },
                    errorDataReceived = {
                        ApplicationManager.getApplication().invokeLater {
                            message.setContent(listOf(ParsedText("error", "error", false, true)))
                            pane.scrollToBottom()
                        }
                        pane.sendingState = ChatGPTPane.SendingState.READY
                    },
                    stat = stat
            ).also {
                var requestFuture: Future<*>? = null
                try {
                    requestFuture = it.get()
                    requestFuture.get()
                } catch (e: InterruptedException) {
                    cancelStreamingTasks()
                    handleInterruptedException(requestFuture)
                    pane.sendingState = ChatGPTPane.SendingState.READY
                } catch (e: InterruptedIOException) {
                    cancelStreamingTasks()
                    handleInterruptedException(requestFuture)
                    pane.sendingState = ChatGPTPane.SendingState.READY
                } catch (e: ExecutionException) {
                    requestFuture?.cancel(true)
                    cancelStreamingTasks()
                    pane.sendingState = ChatGPTPane.SendingState.READY
                } catch (e: Exception) {
                    pane.sendingState = ChatGPTPane.SendingState.READY
                    InferenceGlobalContext.status = ConnectionStatus.ERROR
                    InferenceGlobalContext.lastErrorMsg = e.message
                }
            }
        } catch (e: SocketTimeoutException) {
            e.printStackTrace()
            pane.sendingState = ChatGPTPane.SendingState.READY
        } catch (ex: Exception) {
            ex.printStackTrace()
            cancelStreamingTasks()
            pane.sendingState = ChatGPTPane.SendingState.READY
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

    private fun cancelStreamingTasks() {
        canceled = true
        streamSchedulerTasks.forEach {
            it.cancel(true)
        }
        streamSchedulerTasks.clear()
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