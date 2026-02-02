package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.LSPProcessHolderChangedNotifier
import com.smallcloud.refactai.notifications.emitChat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean

class NotificationSSEClient(private val project: Project) : Disposable {
    private val logger = Logger.getInstance(NotificationSSEClient::class.java)
    private val gson = Gson()
    private val isRunning = AtomicBoolean(false)
    private var connectionFuture: Future<*>? = null
    private val reconnectDelayMs = 5000L

    fun start() {
        if (isRunning.getAndSet(true)) return

        project.messageBus.connect(this).subscribe(
            LSPProcessHolderChangedNotifier.TOPIC,
            object : LSPProcessHolderChangedNotifier {
                override fun lspIsActive(isActive: Boolean) {
                    if (isActive) {
                        reconnect()
                    } else {
                        disconnect()
                    }
                }
            }
        )

        reconnect()
    }

    private fun reconnect() {
        connectionFuture?.cancel(true)
        connectionFuture = AppExecutorUtil.getAppExecutorService().submit {
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                try {
                    connect()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                } catch (e: Exception) {
                    logger.debug("SSE connection error: ${e.message}")
                }
                if (isRunning.get()) {
                    try {
                        Thread.sleep(reconnectDelayMs)
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
    }

    private fun connect() {
        val lspHolder = LSPProcessHolder.getInstance(project) ?: return
        val baseUrl = lspHolder.url
        if (baseUrl.toString().isEmpty()) {
            Thread.sleep(reconnectDelayMs)
            return
        }

        val sseUrl = baseUrl.resolve("/v1/sidebar/subscribe")
        logger.info("Connecting to SSE: $sseUrl")

        val connection = (sseUrl.toURL().openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "text/event-stream")
            setRequestProperty("Cache-Control", "no-cache")
            connectTimeout = 10000
            readTimeout = 0
        }

        try {
            connection.connect()
            if (connection.responseCode != 200) {
                logger.warn("SSE connection failed: ${connection.responseCode}")
                return
            }

            BufferedReader(InputStreamReader(connection.inputStream)).use { reader ->
                var currentLine: String? = reader.readLine()
                while (isRunning.get() && currentLine != null) {
                    if (currentLine.isNotEmpty() && !currentLine.startsWith(":")) {
                        if (currentLine.startsWith("data: ")) {
                            val data = currentLine.substring(6)
                            processEvent(data)
                        }
                    }
                    currentLine = reader.readLine()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun processEvent(data: String) {
        try {
            val json = gson.fromJson(data, JsonObject::class.java)
            val category = json.get("category")?.asString ?: return

            if (category == "notification") {
                val type = json.get("type")?.asString ?: return
                when (type) {
                    "task_done" -> {
                        val chatId = json.get("chat_id")?.asString ?: return
                        val summary = json.get("summary")?.asString ?: "Task completed"
                        emitChat(project, escapeHtml(summary), chatId)
                    }
                    "ask_questions" -> {
                        val chatId = json.get("chat_id")?.asString ?: return
                        val questions = json.getAsJsonArray("questions")
                        val count = questions?.size() ?: 0
                        val text = when (count) {
                            0 -> "your input"
                            1 -> "1 question"
                            else -> "$count questions"
                        }
                        emitChat(project, "AI needs $text to continue", chatId)
                    }
                }
            }
        } catch (e: Exception) {
            logger.debug("Failed to parse SSE event: ${e.message}")
        }
    }

    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }

    private fun disconnect() {
        connectionFuture?.cancel(true)
        connectionFuture = null
    }

    override fun dispose() {
        isRunning.set(false)
        disconnect()
    }
}
