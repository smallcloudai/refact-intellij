package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import org.cef.browser.CefBrowser

/**
 * Tracks CEF browser instances for proper cleanup.
 * NOTE: JBCefBrowser manages its own CefClient internally - we only track browsers here.
 * CefApp is managed by the IDE and should NOT be disposed by plugins.
 */
object CefLifecycleManager {
    private val logger = Logger.getInstance(CefLifecycleManager::class.java)
    private val lock = Any()
    private val browsers = mutableSetOf<CefBrowser>()

    fun registerBrowser(browser: CefBrowser) {
        synchronized(lock) {
            browsers.add(browser)
            logger.debug("Registered browser. Total browsers: ${browsers.size}")
        }
    }

    fun releaseBrowser(browser: CefBrowser) {
        synchronized(lock) {
            val wasTracked = browsers.remove(browser)
            try {
                browser.close(true)
                if (wasTracked) {
                    logger.debug("Browser closed. Remaining browsers: ${browsers.size}")
                } else {
                    logger.debug("Released untracked browser")
                }
            } catch (e: Exception) {
                logger.warn("Error closing browser", e)
            }
        }
    }

    fun getActiveBrowserCount(): Int {
        synchronized(lock) {
            return browsers.size
        }
    }
}
