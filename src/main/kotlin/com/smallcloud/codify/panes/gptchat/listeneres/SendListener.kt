package com.smallcloud.codify.panes.gptchat.listeneres

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.panes.gptchat.ChatGPTPane
import com.smallcloud.codify.panes.gptchat.State
import com.smallcloud.codify.panes.gptchat.structs.ChatGPTRequest
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.Executors
import com.smallcloud.codify.panes.gptchat.utils.HttpConnection.Companion.instance as HttpConnection


class SendListener(private val pane: ChatGPTPane) : ActionListener, KeyListener {

    override fun actionPerformed(e: ActionEvent) {
        try {
            doActionPerformed()
        } catch (ex: IOException) {
            throw RuntimeException(ex)
        }
    }

    @Throws(IOException::class)
    fun doActionPerformed(msg: String? = null, selectedText: String? = null) {
//        val state: SettingsState = SettingsState.getInstance().getState()!!

        var text: String = msg ?: pane.searchTextArea.textArea.text
        LOG.info("ChatGPT Search: $text")
        if (text.isEmpty()) {
            return
        }

        State.instance.pushQuestion(text)
        if (selectedText != null) {
            State.instance.pushCode(selectedText)
        }
        pane.showContent()
        val req = ChatGPTRequest(HttpConnection.CONVERSATION_URL,
            AccountManager.apiKey, State.instance.conversations)

//        val builder = SseParamsBuilder()
//        // If the url type is official, required access token
//        if (state.urlType === SettingConfiguration.SettingURLType.OFFICIAL) {
//            val accessToken: String = Objects.requireNonNull(
//                SettingsState.getInstance()
//                    .getState()
//            ).getAccessToken()
//            if (accessToken == null || accessToken.isEmpty()) {
//                MyNotifier.notifyErrorWithAction(
//                    DataFactory.getInstance().getProject(),
//                    ChatGPTBundle.message("notify.config.title"),
//                    ChatGPTBundle.message("notify.config.text")
//                )
//                return
//            }
//            builder.buildUrl(HttpConnection.OFFICIAL_CONVERSATION_URL).buildToken(accessToken)
//                .buildData(OfficialBuilder.build(text)).buildQuestion(text)
        /*} else if (state.urlType === SettingConfiguration.SettingURLType.DEFAULT) {
            builder.buildUrl(HttpUtil.DEFAULT_CONVERSATION_URL).buildData(OfficialBuilder.build(text))
        } else if (state.urlType === SettingConfiguration.SettingURLType.CUSTOMIZE) {
            if (state.customizeUrl == null || state.customizeUrl.isEmpty()) {
                MyNotifier.notifyErrorWithAction(
                    DataFactory.getInstance().getProject(),
                    ChatGPTBundle.message("notify.config.title"),
                    ChatGPTBundle.message("notify.config.text")
                )
                return
            }
            builder.buildUrl(state.customizeUrl).buildData(OfficialBuilder.build(text))
        } else if (state.urlType === SettingConfiguration.SettingURLType.CLOUDFLARE) {
            if (state.cloudFlareUrl == null || state.cloudFlareUrl.isEmpty()) {
                MyNotifier.notifyErrorWithAction(
                    DataFactory.getInstance().getProject(),
                    ChatGPTBundle.message("notify.config.title"),
                    ChatGPTBundle.message("notify.config.text")
                )
                return
            }
            builder.buildUrl(state.cloudFlareUrl).buildData(CloudflareBuilder.build(text))
        }*/
        dispatch(req)
    }

    fun dispatch(req: ChatGPTRequest) {
        pane.aroundRequest(true)
        val executorService = Executors.newFixedThreadPool(2)
        executorService.submit {
            try {
                HttpConnection.post(req, pane, true)
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
        executorService.shutdown()
        if (!executorService.isShutdown) {
            executorService.shutdownNow()
        }
    }

    override fun keyTyped(e: KeyEvent) {}
    override fun keyPressed(e: KeyEvent) {
        if (e.keyCode == KeyEvent.VK_ENTER && !e.isControlDown && !e.isShiftDown) {
            e.consume()
            pane.button.doClick()
        }
    }

    override fun keyReleased(e: KeyEvent) {}

    companion object {
        private val LOG: Logger = Logger.getInstance(SendListener::class.java)
    }
}