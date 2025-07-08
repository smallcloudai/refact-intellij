package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles JavaScript to Kotlin message processing asynchronously to prevent blocking CEF I/O threads.
 * Messages are queued, parsed on a background thread, then dispatched on the EDT.
 */
class AsyncMessageHandler<T>(
    private val parser: (String) -> T?,
    private val dispatcher: (T) -> Unit,
    queueSize: Int = 1000
) : Disposable {

    private val logger = Logger.getInstance(AsyncMessageHandler::class.java)
    private val messageQueue = ArrayBlockingQueue<String>(queueSize)
    private val executor = Executors.newSingleThreadExecutor { runnable: Runnable ->
        Thread(runnable, "ChatWebView-AsyncMessageHandler").apply {
            isDaemon = true
        }
    }
    private val disposed = AtomicBoolean(false)

    init {
        startMessageProcessor()
    }

    /**
     * Offers a message for asynchronous processing.
     * @param rawMessage The raw message string to process
     * @return true if the message was queued, false if the queue is full
     */
    fun offerMessage(rawMessage: String): Boolean {
        if (disposed.get()) {
            logger.warn("Attempted to offer message to disposed handler")
            return false
        }

        val offered = messageQueue.offer(rawMessage)
        if (!offered) {
            logger.warn("Message queue full, dropping message: ${rawMessage.take(100)}...")
        }

        return offered
    }

    fun getQueueSize(): Int = messageQueue.size
    
    private fun startMessageProcessor() {
        executor.submit {
            logger.info("AsyncMessageHandler started")
            
            while (!disposed.get() && !Thread.currentThread().isInterrupted) {
                try {
                    // Take message from queue (blocks until available)
                    val rawMessage = messageQueue.take()

                    // Parse message on background thread
                    val parsedMessage = try {
                        parser(rawMessage)
                    } catch (e: Exception) {
                        logger.warn("Error parsing message: ${rawMessage.take(100)}...", e)
                        null
                    }

                    // Dispatch on EDT if parsing succeeded
                    if (parsedMessage != null) {
                        ApplicationManager.getApplication().invokeLater {
                            if (!disposed.get()) {
                                try {
                                    dispatcher(parsedMessage)
                                } catch (e: Exception) {
                                    logger.warn("Error dispatching parsed message", e)
                                }
                            }
                        }
                    }

                } catch (e: InterruptedException) {
                    logger.info("AsyncMessageHandler interrupted")
                    break
                } catch (e: Exception) {
                    logger.error("Unexpected error in message processor", e)
                }
            }

            logger.info("AsyncMessageHandler stopped")
        }
    }
    
    override fun dispose() {
        if (disposed.compareAndSet(false, true)) {
            logger.info("Disposing AsyncMessageHandler with ${messageQueue.size} pending messages")
            messageQueue.clear()
            executor.shutdownNow()
            logger.info("AsyncMessageHandler disposal completed")
        }
    }

    fun isDisposed(): Boolean = disposed.get()
}
