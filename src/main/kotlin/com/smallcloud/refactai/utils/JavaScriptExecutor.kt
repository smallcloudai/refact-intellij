package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowser
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simplified JavaScript executor for ChatWebView.
 * JCEF's executeJavaScript is thread-safe and internally serializes calls.
 */
class JavaScriptExecutor(
    private val browser: JBCefBrowser,
    private val timeoutMs: Long = 5000L,
    poolSize: Int = 3
) : Disposable {

    private val logger = Logger.getInstance(JavaScriptExecutor::class.java)
    private val executor = Executors.newFixedThreadPool(poolSize) { runnable ->
        Thread(runnable, "SMC-JS-Executor-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private val pendingExecutions = AtomicInteger(0)
    @Volatile
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
                if (browser.isDisposed) {
                    throw IllegalStateException("Browser is disposed")
                }
                // JCEF's executeJavaScript is thread-safe - no nested future needed
                browser.cefBrowser.executeJavaScript(script, browser.cefBrowser.url, 0)
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

    fun awaitCompletion(timeoutMs: Long = 10000L): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        var waited = 0L
        while (pendingExecutions.get() > 0 && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(50)
                waited += 50
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        if (waited > 0 && pendingExecutions.get() > 0) {
            logger.debug("Waited ${waited}ms, still ${pendingExecutions.get()} pending")
        }
        return pendingExecutions.get() == 0
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        logger.info("Disposing JavaScriptExecutor with ${pendingExecutions.get()} pending executions")

        executor.shutdown()
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                executor.shutdownNow()
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("JavaScriptExecutor did not terminate gracefully")
                }
            }
        } catch (e: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
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
