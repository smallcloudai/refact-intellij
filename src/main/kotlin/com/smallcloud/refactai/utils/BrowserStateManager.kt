package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import org.cef.browser.CefBrowser
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Manages browser state to prevent concurrent operations that can cause UI freezes.
 * This singleton tracks all browser instances and their current operations.
 */
object BrowserStateManager {
    private val logger = Logger.getInstance(BrowserStateManager::class.java)
    private val browserStates = ConcurrentHashMap<CefBrowser, BrowserState>()
    private val globalLock = ReentrantReadWriteLock()
    
    private data class BrowserState(
        val isExecutingJavaScript: AtomicBoolean = AtomicBoolean(false),
        val isDisposing: AtomicBoolean = AtomicBoolean(false),
        val pendingOperations: AtomicBoolean = AtomicBoolean(false)
    )
    
    /**
     * Registers a browser for state management.
     */
    fun registerBrowser(browser: CefBrowser) {
        globalLock.write {
            browserStates[browser] = BrowserState()
            logger.info("Registered browser for state management. Total: ${browserStates.size}")
        }
    }
    
    /**
     * Unregisters a browser from state management.
     */
    fun unregisterBrowser(browser: CefBrowser) {
        globalLock.write {
            browserStates.remove(browser)
            logger.info("Unregistered browser from state management. Remaining: ${browserStates.size}")
        }
    }
    
    /**
     * Checks if a browser is safe for JavaScript execution.
     * @param browser The browser to check
     * @return true if safe to execute JavaScript, false otherwise
     */
    fun isSafeForJavaScript(browser: CefBrowser): Boolean {
        return globalLock.read {
            val state = browserStates[browser] ?: return@read false
            !state.isDisposing.get() && !state.pendingOperations.get()
        }
    }
    
    /**
     * Marks a browser as executing JavaScript.
     * @param browser The browser
     * @return true if successfully marked, false if browser is not safe
     */
    fun markExecutingJavaScript(browser: CefBrowser): Boolean {
        return globalLock.read {
            val state = browserStates[browser] ?: return@read false
            if (state.isDisposing.get()) {
                logger.debug("Cannot execute JavaScript: browser is disposing")
                return@read false
            }
            state.isExecutingJavaScript.set(true)
            true
        }
    }
    
    /**
     * Marks a browser as finished executing JavaScript.
     */
    fun markJavaScriptComplete(browser: CefBrowser) {
        globalLock.read {
            browserStates[browser]?.isExecutingJavaScript?.set(false)
        }
    }
    
    /**
     * Marks a browser as disposing to prevent new operations.
     * @param browser The browser being disposed
     * @return true if successfully marked for disposal
     */
    fun markDisposing(browser: CefBrowser): Boolean {
        return globalLock.read {
            val state = browserStates[browser] ?: return@read false
            if (state.isDisposing.compareAndSet(false, true)) {
                logger.info("Marked browser as disposing")
                true
            } else {
                logger.debug("Browser already marked as disposing")
                false
            }
        }
    }
    
    /**
     * Waits for all JavaScript operations to complete on a browser.
     * @param browser The browser to wait for
     * @param timeoutMs Maximum time to wait
     * @return true if all operations completed, false if timed out
     */
    fun waitForJavaScriptCompletion(browser: CefBrowser, timeoutMs: Long = 2000L): Boolean {
        val state = globalLock.read { browserStates[browser] } ?: return true
        
        val startTime = System.currentTimeMillis()
        while (state.isExecutingJavaScript.get() && 
               (System.currentTimeMillis() - startTime) < timeoutMs) {
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        
        val completed = !state.isExecutingJavaScript.get()
        if (!completed) {
            logger.warn("JavaScript operations did not complete within ${timeoutMs}ms")
        }
        return completed
    }
    
    /**
     * Checks if any browser has pending operations.
     * Useful for determining if it's safe to open modal dialogs.
     */
    fun hasAnyPendingOperations(): Boolean {
        return globalLock.read {
            browserStates.values.any { state ->
                state.isExecutingJavaScript.get() || state.pendingOperations.get()
            }
        }
    }
    
    /**
     * Marks a browser as having pending operations.
     */
    fun markPendingOperations(browser: CefBrowser, hasPending: Boolean) {
        globalLock.read {
            browserStates[browser]?.pendingOperations?.set(hasPending)
        }
    }
    
    /**
     * Gets the current state summary for debugging.
     */
    fun getStateSummary(): String {
        return globalLock.read {
            buildString {
                appendLine("=== Browser State Summary ===")
                appendLine("Total browsers: ${browserStates.size}")
                browserStates.forEach { (browser, state) ->
                    appendLine("Browser ${browser.hashCode()}: " +
                              "JS=${state.isExecutingJavaScript.get()}, " +
                              "Disposing=${state.isDisposing.get()}, " +
                              "Pending=${state.pendingOperations.get()}")
                }
            }
        }
    }
    
    /**
     * Forces cleanup of all browser states (for emergency situations).
     */
    fun forceCleanup() {
        globalLock.write {
            logger.warn("Force cleanup of all browser states")
            browserStates.clear()
        }
    }
}
