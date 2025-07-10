package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import org.cef.CefApp
import org.cef.CefClient
import org.cef.browser.CefBrowser

/**
 * Centralized CEF lifecycle management to prevent resource leaks and ensure proper cleanup.
 * This singleton manages all CEF browser instances and handles proper initialization/disposal.
 */
object CefLifecycleManager {
    private val logger = Logger.getInstance(CefLifecycleManager::class.java)
    private val lock = Any()
    private var cefApp: CefApp? = null
    private var cefClient: CefClient? = null
    private val browsers = mutableSetOf<CefBrowser>()

    fun initIfNeeded() {
        synchronized(lock) {
            if (cefApp == null) {
                logger.info("Initializing CEF with optimized settings")

                System.setProperty("ide.browser.jcef.jsQueryPoolSize", "200")
                System.setProperty("ide.browser.jcef.gpu.disable", "false") // Enable GPU acceleration by default

                try {
                    cefApp = CefApp.getInstance()
                    cefClient = cefApp!!.createClient()
                    logger.info("CEF initialized successfully")
                } catch (e: Exception) {
                    logger.error("Failed to initialize CEF", e)
                    throw e
                }
            }
        }
    }

    fun registerBrowser(browser: CefBrowser) {
        synchronized(lock) {
            browsers.add(browser)
            logger.info("Registered existing browser. Total browsers: ${browsers.size}")
        }
    }

    fun releaseBrowser(browser: CefBrowser) {
        synchronized(lock) {
            if (browsers.remove(browser)) {
                try {
                    // Force close the browser
                    browser.close(true)
                    logger.info("Browser closed. Remaining browsers: ${browsers.size}")

                    // If this was the last browser, clean up CEF resources
                    if (browsers.isEmpty()) {
                        cleanupCef()
                    }
                } catch (e: Exception) {
                    logger.warn("Error closing browser", e)
                }
            } else {
                // Try to release untracked browser
                try {
                    browser.close(true)
                    logger.info("Released untracked browser")
                } catch (e: Exception) {
                    logger.warn("Error releasing untracked browser", e)
                }
            }
        }
    }

    fun getActiveBrowserCount(): Int {
        synchronized(lock) {
            return browsers.size
        }
    }

    private fun cleanupCef() {
        try {
            logger.info("Cleaning up CEF resources")

            cefClient?.dispose()
            cefApp?.dispose()

            cefClient = null
            cefApp = null

            logger.info("CEF cleanup completed")
        } catch (e: Exception) {
            logger.error("Error during CEF cleanup", e)
        }
    }
}
