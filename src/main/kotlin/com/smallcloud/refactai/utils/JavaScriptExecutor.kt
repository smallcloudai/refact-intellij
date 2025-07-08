package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
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
        Thread(runnable, "JS-Executor-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
        }
    }
    private val pendingExecutions = AtomicInteger(0)
    private var disposed = false
    
    companion object {
        private val threadCounter = AtomicInteger(0)
    }
    
    /**
     * Executes JavaScript with timeout protection and browser state management.
     * @param script The JavaScript code to execute
     * @param description Optional description for logging
     * @return CompletableFuture that completes when execution finishes
     */
    fun executeJavaScript(script: String, description: String = "script"): CompletableFuture<Void> {
        if (disposed) {
            return CompletableFuture<Void>().apply {
                completeExceptionally(IllegalStateException("JavaScriptExecutor is disposed"))
            }
        }
        
        val cefBrowser = browser.cefBrowser
        if (cefBrowser == null || !BrowserStateManager.isSafeForJavaScript(cefBrowser)) {
            return CompletableFuture<Void>().apply {
                completeExceptionally(IllegalStateException("Browser is not safe for JavaScript execution"))
            }
        }
        
        pendingExecutions.incrementAndGet()
        
        return CompletableFuture.runAsync({
            try {
                if (!BrowserStateManager.markExecutingJavaScript(cefBrowser)) {
                    throw IllegalStateException("Cannot execute JavaScript: browser state conflict")
                }
                
                executeWithTimeout(script, timeoutMs, description)
            } catch (e: Exception) {
                logger.warn("JavaScript execution failed for $description", e)
                throw e
            } finally {
                BrowserStateManager.markJavaScriptComplete(cefBrowser)
                pendingExecutions.decrementAndGet()
            }
        }, executor)
    }
    
    /**
     * Executes multiple JavaScript statements as a single batch.
     * More efficient than individual executions for multiple operations.
     */
    fun executeBatch(scripts: List<String>, description: String = "batch"): CompletableFuture<Void> {
        if (scripts.isEmpty()) {
            return CompletableFuture.completedFuture(null)
        }
        
        val combinedScript = scripts.joinToString(";\n") { it.trimEnd(';') }
        return executeJavaScript(combinedScript, "$description (${scripts.size} statements)")
    }
    
    /**
     * Executes JavaScript with immediate return (fire-and-forget).
     * Useful for non-critical operations where you don't need to wait.
     */
    fun executeAsync(script: String, description: String = "async-script") {
        executeJavaScript(script, description).exceptionally { throwable ->
            logger.warn("Async JavaScript execution failed for $description", throwable)
            null
        }
    }
    
    /**
     * Executes JavaScript synchronously with timeout.
     * Blocks the calling thread until completion or timeout.
     * WARNING: Use sparingly to avoid EDT blocking.
     */
    fun executeSync(script: String, description: String = "sync-script"): Boolean {
        if (disposed) {
            logger.warn("Cannot execute sync JavaScript: executor is disposed")
            return false
        }
        
        // Check if we're on EDT and warn
        if (com.intellij.openapi.application.ApplicationManager.getApplication().isDispatchThread) {
            logger.warn("Synchronous JavaScript execution on EDT for $description - this may cause UI freezes")
        }
        
        return try {
            executeJavaScript(script, description).get(timeoutMs, TimeUnit.MILLISECONDS)
            true
        } catch (e: TimeoutException) {
            logger.warn("JavaScript execution timed out for $description")
            false
        } catch (e: Exception) {
            logger.warn("JavaScript execution failed for $description", e)
            false
        }
    }
    
    /**
     * Creates a reusable script template for better performance.
     * Useful for scripts that are executed repeatedly with different parameters.
     */
    fun createTemplate(template: String): JavaScriptTemplate {
        return JavaScriptTemplate(template, this)
    }
    
    private fun executeWithTimeout(script: String, timeoutMs: Long, description: String) {
        try {
            if (browser.isDisposed || browser.cefBrowser == null) {
                throw IllegalStateException("Browser is disposed or null")
            }
            
            val cefBrowser = browser.cefBrowser
            
            // Use non-blocking execution - CEF handles this asynchronously
            cefBrowser.executeJavaScript(script, cefBrowser.url, 0)
            
            logger.debug("JavaScript execution queued for $description")
            
        } catch (e: Exception) {
            logger.warn("CEF JavaScript execution failed for $description", e)
            throw e
        }
    }
    
    /**
     * Gets the number of pending JavaScript executions.
     */
    fun getPendingExecutionCount(): Int = pendingExecutions.get()
    
    /**
     * Checks if the executor is currently busy.
     */
    fun isBusy(): Boolean = pendingExecutions.get() > 0
    
    /**
     * Waits for all pending executions to complete.
     * @param timeoutMs Maximum time to wait
     * @return true if all executions completed, false if timed out
     */
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
        
        // Wait briefly for pending executions
        awaitCompletion(2000L)
        
        // Shutdown executor
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
    /**
     * Executes the template with the given parameters.
     * Parameters are substituted using String.format() syntax.
     */
    fun execute(vararg params: Any, description: String = "template"): CompletableFuture<Void> {
        val script = String.format(template, *params)
        return executor.executeJavaScript(script, description)
    }
    
    /**
     * Executes the template synchronously.
     */
    fun executeSync(vararg params: Any, description: String = "template-sync"): Boolean {
        val script = String.format(template, *params)
        return executor.executeSync(script, description)
    }
}
