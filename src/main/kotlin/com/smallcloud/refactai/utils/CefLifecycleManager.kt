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
    
    /**
     * Initializes CEF if not already initialized.
     * Must be called before any browser creation.
     */
    fun initIfNeeded() {
        synchronized(lock) {
            if (cefApp == null) {
                logger.info("Initializing CEF with optimized settings")
                
                // Set system properties before CEF initialization
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
    
    /**
     * Creates a new browser instance with proper lifecycle management.
     * @param url The URL to load
     * @param offscreen true for OSR (Linux), false for native rendering
     * @return The created CefBrowser instance
     */
    fun createBrowser(url: String, offscreen: Boolean): CefBrowser {
        initIfNeeded()
        
        synchronized(lock) {
            if (cefClient == null) {
                throw IllegalStateException("CEF client not initialized")
            }
            
            try {
                val browser = cefClient!!.createBrowser(url, offscreen, false)
                browsers.add(browser)
                logger.info("Created browser for URL: $url (OSR: $offscreen). Total browsers: ${browsers.size}")
                return browser
            } catch (e: Exception) {
                logger.error("Failed to create browser for URL: $url", e)
                throw e
            }
        }
    }
    
    /**
     * Registers an existing browser for lifecycle management.
     * Useful for browsers created outside the lifecycle manager.
     */
    fun registerBrowser(browser: CefBrowser) {
        synchronized(lock) {
            browsers.add(browser)
            logger.info("Registered existing browser. Total browsers: ${browsers.size}")
        }
    }
    
    /**
     * Properly closes and releases a browser instance.
     * If this was the last browser, considers tearing down CEF entirely.
     * @param browser The browser to release
     */
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
    
    /**
     * Forces cleanup of all browsers and CEF resources.
     * Should only be called during application shutdown.
     */
    fun forceCleanup() {
        synchronized(lock) {
            logger.info("Force cleanup requested. Closing ${browsers.size} browsers")
            
            // Close all remaining browsers
            browsers.toList().forEach { browser ->
                try {
                    browser.close(true)
                } catch (e: Exception) {
                    logger.warn("Error force-closing browser", e)
                }
            }
            browsers.clear()
            
            cleanupCef()
        }
    }
    
    /**
     * Gets the current number of active browsers.
     * Useful for monitoring and testing.
     */
    fun getActiveBrowserCount(): Int {
        synchronized(lock) {
            return browsers.size
        }
    }
    
    /**
     * Checks if CEF is currently initialized.
     */
    fun isInitialized(): Boolean {
        synchronized(lock) {
            return cefApp != null && cefClient != null
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
