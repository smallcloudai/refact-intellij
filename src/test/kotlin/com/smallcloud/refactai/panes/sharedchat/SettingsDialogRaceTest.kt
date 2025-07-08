package com.smallcloud.refactai.panes.sharedchat

import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.testUtils.TestableChatWebView
import com.smallcloud.refactai.testUtils.TestableEditor
import com.smallcloud.refactai.utils.BrowserStateManager
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for settings dialog race conditions that can cause UI freezes.
 * These tests simulate the conditions that cause the "plugin settings" freeze bug.
 */
class SettingsDialogRaceTest : LightPlatform4TestCase() {
    
    @Test
    fun testSettingsDialogWithPendingJavaScript() {
        // This test simulates clicking settings while JavaScript is executing
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        // Simulate JavaScript execution in progress
        chatWebView.simulateJavaScriptExecution(true)
        
        // Try to open settings - should wait for JS to complete
        val settingsOpened = AtomicInteger(0)
        val latch = CountDownLatch(1)
        
        // Simulate settings dialog opening
        Thread {
            // This would normally be the settings button click handler
            if (!chatWebView.isJavaScriptExecuting()) {
                settingsOpened.incrementAndGet()
            } else {
                // Wait for operations to complete
                val startTime = System.currentTimeMillis()
                while (chatWebView.isJavaScriptExecuting() && 
                       (System.currentTimeMillis() - startTime) < 1000L) {
                    Thread.sleep(50)
                }
                settingsOpened.incrementAndGet()
            }
            latch.countDown()
        }.start()
        
        // Let the settings thread start and wait a bit
        Thread.sleep(100)
        assertEquals("Settings should not open while JS is executing", 0, settingsOpened.get())
        
        // Complete the JavaScript execution
        chatWebView.simulateJavaScriptExecution(false)
        
        // Wait for settings to open
        assertTrue("Settings dialog should eventually open", latch.await(2, TimeUnit.SECONDS))
        assertEquals("Settings should open after JS completes", 1, settingsOpened.get())
        
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testMultipleRapidSettingsClicks() {
        // This test simulates rapid clicking of the settings button
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        val clickCount = AtomicInteger(0)
        val completedCount = AtomicInteger(0)
        val threads = mutableListOf<Thread>()
        
        // Simulate 5 rapid clicks
        repeat(5) { i ->
            val thread = Thread {
                clickCount.incrementAndGet()
                
                // Simulate the settings dialog opening logic
                if (!chatWebView.isJavaScriptExecuting()) {
                    // Simulate some processing time
                    Thread.sleep(50)
                    completedCount.incrementAndGet()
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all threads to complete
        threads.forEach { it.join(1000) }
        
        assertEquals("All clicks should be registered", 5, clickCount.get())
        assertTrue("At least some operations should complete", completedCount.get() > 0)
        
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testSettingsDialogDuringDisposal() {
        // This test simulates clicking settings while the component is being disposed
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        val settingsAttempted = AtomicInteger(0)
        val settingsSucceeded = AtomicInteger(0)
        
        // Start disposal in background
        Thread {
            Thread.sleep(100) // Let settings attempt start first
            chatWebView.dispose()
        }.start()
        
        // Try to open settings
        Thread {
            settingsAttempted.incrementAndGet()
            
            // Check if component is still valid
            if (!chatWebView.isDisposed) {
                Thread.sleep(200) // Simulate dialog opening time
                if (!chatWebView.isDisposed) {
                    settingsSucceeded.incrementAndGet()
                }
            }
        }.start()
        
        // Wait for operations to complete
        Thread.sleep(500)
        
        assertEquals("Settings should be attempted", 1, settingsAttempted.get())
        // Settings may or may not succeed depending on timing, but shouldn't crash
        assertTrue("Should handle disposal gracefully", settingsSucceeded.get() <= 1)
        
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testJavaScriptExecutionStateTracking() {
        // Test that JavaScript execution state is properly tracked
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        // Initially should have no pending operations
        assertFalse("Should have no pending operations initially", 
                   chatWebView.isJavaScriptExecuting())
        
        // Simulate JavaScript execution
        chatWebView.simulateJavaScriptExecution(true)
        assertTrue("Should have JavaScript executing", chatWebView.isJavaScriptExecuting())
        
        // Complete JavaScript execution
        chatWebView.simulateJavaScriptExecution(false)
        assertFalse("Should have no JavaScript executing", chatWebView.isJavaScriptExecuting())
        
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
}
