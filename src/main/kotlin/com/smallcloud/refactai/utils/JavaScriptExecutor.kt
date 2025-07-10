package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowser
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optimized JavaScript executor with pooling, timeout handling, and batch execution support.
 * Provides better performance and reliability for JavaScript execution in ChatWebView.
 */
class JavaScriptExecutor(
    private val browser: JBCefBrowser,
    private val timeoutMs: Long = 5000L,
    private val poolSize: Int = 5
) : Disposable {

    private val logger = Logger.getInstance(JavaScriptExecutor::class.java)
    private val executor = Executors.newFixedThreadPool(poolSize) { runnable ->
        Thread(runnable, "SMC-JS-Executor-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private val pendingExecutions = AtomicInteger(0)
    private var disposed = false

    companion object {
        private val threadCounter = AtomicInteger(0)
    }

    fun executeJavaScript(script: String, description: String = "script"): CompletableFuture<Void> {
        if (disposed) {
            return CompletableFuture<Void>().apply {
                completeExceptionally(IllegalStateException("JavaScriptExecutor is disposed"))
            }
        }

        pendingExecutions.incrementAndGet()

        return CompletableFuture.runAsync({
            try {
                executeWithTimeout(script, timeoutMs, description)
            } catch (e: Exception) {
                logger.warn("JavaScript execution failed for $description", e)
                throw e
            } finally {
                pendingExecutions.decrementAndGet()
            }
        }, executor)
    }

    fun executeBatch(scripts: List<String>, description: String = "batch"): CompletableFuture<Void> {
        if (scripts.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }

        val combinedScript = scripts.joinToString(";\n") { it.trimEnd(';') }
        return executeJavaScript(combinedScript, "$description (${scripts.size} statements)")
    }

    fun executeAsync(script: String, description: String = "async-script") {
        executeJavaScript(script, description).exceptionally { throwable ->
            logger.warn("Async JavaScript execution failed for $description", throwable)
            null
        }
    }

    fun createTemplate(template: String): JavaScriptTemplate {
        return JavaScriptTemplate(template, this)
    }

    private fun executeWithTimeout(script: String, timeoutMs: Long, description: String) {
        val future = CompletableFuture.runAsync {
            try {
                if (!browser.isDisposed) {
                    browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
                } else {
                    throw IllegalStateException("Browser is disposed or null")
                }
            } catch (e: Exception) {
                logger.warn("CEF JavaScript execution failed for $description", e)
                throw e
            }
        }

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            future.cancel(true)
            throw RuntimeException("JavaScript execution timed out after ${timeoutMs}ms for $description")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    fun awaitCompletion(timeoutMs: Long = 10000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (pendingExecutions.get() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100)
        }
        return pendingExecutions.get() == 0
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        logger.info("Disposing JavaScriptExecutor with ${pendingExecutions.get()} pending executions")

        awaitCompletion(2000L)
        executor.shutdownNow()

        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("JavaScriptExecutor did not terminate gracefully")
            }
        } catch (e: InterruptedException) {
            logger.warn("Interrupted while waiting for JavaScriptExecutor termination")
        }

        logger.info("JavaScriptExecutor disposal completed")
    }
}

/**
 * Reusable JavaScript template for parameterized scripts.
 */
class JavaScriptTemplate(
    private val template: String,
    private val executor: JavaScriptExecutor
) {
    fun execute(vararg params: Any, description: String = "template"): CompletableFuture<Void> {
        val script = String.format(template, *params)
        return executor.executeJavaScript(script, description)
    }
}
