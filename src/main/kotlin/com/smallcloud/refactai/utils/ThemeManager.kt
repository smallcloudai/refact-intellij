package com.smallcloud.refactai.utils

import com.intellij.ide.ui.LafManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ui.UIUtil
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Enhanced theme management with proper synchronization and debouncing.
 * Handles theme changes efficiently and prevents excessive updates.
 */
class ThemeManager(private val jsExecutor: JavaScriptExecutor) : Disposable {
    private val logger = Logger.getInstance(ThemeManager::class.java)
    private val debounceExecutor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "ThemeManager-Debouncer").apply { isDaemon = true }
    }
    private val pendingUpdate = AtomicReference<ScheduledFuture<*>>(null)
    private var disposed = false
    
    // Cache current theme state to avoid unnecessary updates
    private var lastThemeState: ThemeState? = null
    
    private data class ThemeState(
        val isDark: Boolean,
        val backgroundColor: String,
        val bodyClass: String
    )
    
    /**
     * Updates the theme with debouncing to prevent rapid successive calls.
     * @param forceUpdate If true, bypasses the debouncing mechanism
     */
    fun updateTheme(forceUpdate: Boolean = false) {
        if (disposed) return
        
        if (forceUpdate) {
            doUpdateTheme()
        } else {
            // Cancel any pending update
            pendingUpdate.get()?.cancel(false)
            
            // Schedule new update with debouncing
            val future = debounceExecutor.schedule({
                doUpdateTheme()
            }, 100, TimeUnit.MILLISECONDS)
            
            pendingUpdate.set(future)
        }
    }
    
    private fun doUpdateTheme() {
        if (disposed) return
        
        try {
            val currentTheme = getCurrentThemeState()
            
            // Skip update if theme hasn't changed
            if (currentTheme == lastThemeState) {
                logger.debug("Theme unchanged, skipping update")
                return
            }
            
            val script = createThemeUpdateScript(currentTheme)
            jsExecutor.executeAsync(script, "theme-update")
            
            lastThemeState = currentTheme
            logger.info("Theme updated: ${if (currentTheme.isDark) "dark" else "light"}")
            
        } catch (e: Exception) {
            logger.warn("Failed to update theme", e)
        }
    }
    
    private fun getCurrentThemeState(): ThemeState {
        val lafManager = LafManager.getInstance()
        val theme = lafManager?.currentUIThemeLookAndFeel
        val isDark = theme?.isDark ?: false
        
        val backgroundColor = UIUtil.getPanelBackground()
        val backgroundColorStr = "rgb(${backgroundColor.red}, ${backgroundColor.green}, ${backgroundColor.blue})"
        val bodyClass = if (isDark) "vscode-dark" else "vscode-light"
        
        return ThemeState(isDark, backgroundColorStr, bodyClass)
    }
    
    private fun createThemeUpdateScript(themeState: ThemeState): String {
        return """
            // Update body class and background
            document.body.className = "${themeState.bodyClass}";
            document.body.style.backgroundColor = "${themeState.backgroundColor}";
            
            // Notify React app of theme change if available
            if (typeof window.postMessage === 'function') {
                try {
                    window.postMessage({
                        type: 'theme-changed',
                        theme: '${if (themeState.isDark) "dark" else "light"}',
                        backgroundColor: '${themeState.backgroundColor}'
                    }, '*');
                } catch (e) {
                    console.warn('Failed to post theme change message:', e);
                }
            }
            
            // Apply theme to any existing UI elements
            const elements = document.querySelectorAll('[data-theme-aware]');
            elements.forEach(el => {
                el.setAttribute('data-theme', '${if (themeState.isDark) "dark" else "light"}');
            });
        """.trimIndent()
    }
    
    /**
     * Forces an immediate theme update without debouncing.
     */
    fun forceThemeUpdate() {
        updateTheme(forceUpdate = true)
    }
    
    /**
     * Gets the current theme state without triggering an update.
     */
    fun getCurrentTheme(): String {
        return if (getCurrentThemeState().isDark) "dark" else "light"
    }
    
    /**
     * Checks if the current theme is dark.
     */
    fun isDarkTheme(): Boolean {
        return getCurrentThemeState().isDark
    }
    
    /**
     * Waits for any pending theme updates to complete.
     */
    fun awaitPendingUpdates(timeoutMs: Long = 1000L): Boolean {
        val future = pendingUpdate.get()
        return if (future != null && !future.isDone) {
            try {
                future.get(timeoutMs, TimeUnit.MILLISECONDS)
                true
            } catch (e: Exception) {
                logger.warn("Timeout waiting for theme update", e)
                false
            }
        } else {
            true
        }
    }
    
    override fun dispose() {
        if (disposed) return
        disposed = true
        
        logger.info("Disposing ThemeManager")
        
        // Cancel any pending updates
        pendingUpdate.get()?.cancel(true)
        
        // Shutdown executor
        debounceExecutor.shutdownNow()
        
        try {
            if (!debounceExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.warn("ThemeManager executor did not terminate gracefully")
            }
        } catch (e: InterruptedException) {
            logger.warn("Interrupted while disposing ThemeManager")
        }
        
        logger.info("ThemeManager disposal completed")
    }
}
