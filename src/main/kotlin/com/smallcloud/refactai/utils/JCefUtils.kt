package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.cef.browser.CefBrowser

private val logger = Logger.getInstance("com.smallcloud.refactai.utils.JCefUtils")

/**
 * Checks if JCEF can start
 */
fun isJcefCanStart(): Boolean {
    return try {
        JBCefApp.isSupported() && JBCefApp.isStarted()
        JBCefApp.isSupported()
    } catch (_: Exception) {
        false
    }
}

/**
 * Safely executes JavaScript in a JCEF browser
 *
 * @param browser The JBCefBrowser instance
 * @param script The JavaScript code to execute
 * @param scope The coroutine scope to use for execution
 */
fun safeExecuteJavaScript(

    browser: JBCefBrowser?,
    cefBrowser: CefBrowser?,
    script: String,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    if (!isBrowserInitialized(browser)) {
        logger.warn("Cannot execute JavaScript: JCEF browser not initialized")
        return
    }

    println("safeExecuteJavaScript")
    println(script)

    scope.launch {
        try {
            withContext(Dispatchers.Default) {
                try {
                    if (cefBrowser != null) {
                        cefBrowser.executeJavaScript(script, cefBrowser.url, 0)
                    } else {
                        logger.warn("Cannot execute JavaScript: CefBrowser is null")
                    }
                } catch (e: IllegalStateException) {
                    logger.warn("Failed to execute JavaScript: ${e.message}")
                } catch (e: Exception) {
                    if (e !is CancellationException) {
                        logger.warn("Error executing JavaScript: ${e.message}", e)
                    }
                }
            }

        } catch (e: Exception) {
            if (e !is CancellationException) {
                logger.warn("JavaScript execution timed out or was cancelled: ${e.message}")
            }
        }
    }
}

/**
 * Checks if a JCEF browser is properly initialized and ready for JavaScript execution
 *
 * @param browser The JBCefBrowser instance to check
 * @return True if the browser is initialized, false otherwise
 */
fun isBrowserInitialized(browser: JBCefBrowser?): Boolean {
    if (browser == null) return false
    
    return try {
        browser.cefBrowser != null && 
            browser.jbCefClient != null && 
            !browser.isDisposed
    } catch (e: Exception) {
        logger.warn("Error checking browser initialization state: ${e.message}")
        false
    }
}

/**
 * Safely posts a message to the browser
 *
 * @param browser The CefBrowser instance
 * @param message The message to post
 * @return True if the message was posted successfully, false otherwise
 */
fun safePostMessage(browser: CefBrowser?, message: String): Boolean {
    println("safePostMessage")
    println(message)
    if (browser == null) {
        logger.warn("Cannot post message: CefBrowser is null")
        return false
    }
    
    return try {
        val script = """window.postMessage($message, "*");"""
        browser.executeJavaScript(script, browser.url, 0)
        true
    } catch (e: IllegalStateException) {
        logger.warn("Failed to post message: ${e.message}")
        false
    } catch (e: Exception) {
        logger.warn("Error posting message: ${e.message}", e)
        false
    }
}