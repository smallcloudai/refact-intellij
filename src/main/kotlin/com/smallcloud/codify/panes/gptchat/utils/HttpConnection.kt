package com.smallcloud.codify.panes.gptchat.utils

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.panes.gptchat.ChatGPTPane
import com.smallcloud.codify.panes.gptchat.structs.ChatGPTRequest
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import com.smallcloud.codify.panes.gptchat.State.Companion.instance as State

class HttpConnection {
    val CONVERSATION_URL = URL("https://inference.smallcloud.ai/chat-v1/completions")

    val EMPTY_RESPONSE = """
        # Ops
        It looks like something went wrong, no data was response.
        """.trimIndent()

    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "CodifyGPTChatScheduler", 2
    )


    fun post(req: ChatGPTRequest, panel: ChatGPTPane, doStream: Boolean = true) {
        val connection: HttpURLConnection = req.url.openConnection() as HttpURLConnection
        connection.setRequestMethod("POST")
        connection.connectTimeout = 50000
        connection.readTimeout = 50000
        connection.doOutput = true
        connection.useCaches = false

        connection.setRequestProperty("Content-Type", "application/json")
        if (!req.token.isNullOrEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + req.token)
        }

        val reqStr = MsgBuilder.build(req.conversation, doStream)

        connection.outputStream.write(reqStr.toByteArray());
        connection.connect()

        val responseCode = connection.responseCode
        val responseMessage = connection.responseMessage

//        if (responseCode != 200) {
//            LOG.error("ChatGPT Response error, responseCode={}, responseMessage={}",
//                responseCode, responseMessage);
//            if (responseCode == 401) {
//                title = ChatGPTBundle.message("notify.token_expired.error.title");
//                text = ChatGPTBundle.message("notify.token_expired.error.text");
//            } else if (responseCode == 429) {
//                title = ChatGPTBundle.message("notify.too_many_request.error.title");
//                text = ChatGPTBundle.message("notify.too_many_request.error.text");
//            }
//            MyNotifier.notifyError(DataFactory.getInstance().getProject(),
//                title, text);
//            panel.aroundRequest(false);
//            throw new Exception("Failed to connect to SSE server");
//        }

        val reader = BufferedReader(
            InputStreamReader(connection.inputStream)
        )
        scheduler.submit {
            var line: String?
            try {
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) {
                        continue
                    }

                    // Dispatch to parse
                    line = parse(line, doStream)
                    if (line == null) {
                        line = EMPTY_RESPONSE
                    } else if (line!!.isEmpty()) {
                        continue
                    }
                    State.pushAnswer(line!!)
                    val localConversations = State.buildConversations()
                    if (doStream) {
                        ApplicationManager.getApplication().invokeAndWait{
                            panel.showContent(localConversations)
                        }
                    }
                }
                if (!doStream) {
                    ApplicationManager.getApplication().invokeLater {
                        panel.showContent()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
//                LOG.error(
//                    "ChatGPT Request exception: " +
//                            "url:{}, params:{}, data:{}, errorMsg{}:", params.getUrl(),
//                    line, params.getData(), e.message
//                )
            } finally {
                try {
                    reader.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                connection.disconnect()
                panel.aroundRequest(false)
            }
        }
    }
    companion object {
        val instance = HttpConnection()
    }
}