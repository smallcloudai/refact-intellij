package com.smallcloud.refactai.panes.sharedchat

import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import org.junit.Test
import org.junit.Assert.*

/**
 * Basic tests for ChatWebView that work with the current implementation.
 * These tests establish our baseline before implementing the fixes.
 */
class BasicChatWebViewTest : LightPlatform4TestCase() {
    
    @Test
    fun testChatWebViewCanBeCreated() {
        // This test validates that ChatWebView can be created without crashing
        // It should pass with the current implementation
        try {
            val mockProject = project
            val editor = Editor(mockProject)
            val chatWebView = ChatWebView(editor) { /* empty handler */ }
            
            // Basic assertions
            assertNotNull("ChatWebView should be created", chatWebView)
            assertNotNull("Component should be available", chatWebView.getComponent())
            assertNotNull("WebView should be available", chatWebView.webView)
            
            // Test basic operations - give time for async initialization
            chatWebView.setStyle()
            chatWebView.postMessage("test message")
            
            // Wait for any pending operations
            Thread.sleep(500)
            
            // Dispose cleanly
            chatWebView.dispose()
            
            // Wait for disposal to complete
            Thread.sleep(200)
            
            // Verify disposal
            assertTrue("WebView should be disposed", chatWebView.webView.isDisposed)
            
        } catch (e: Exception) {
            fail("ChatWebView creation should not fail: ${e.message}")
        }
    }
    
    @Test
    fun testChatWebViewDisposalBasic() {
        // This test validates basic disposal functionality
        val mockProject = project
        val editor = Editor(mockProject)
        val chatWebView = ChatWebView(editor) { /* empty handler */ }
        
        // Wait for initialization
        Thread.sleep(200)
        
        // Verify initial state
        assertFalse("WebView should not be disposed initially", chatWebView.webView.isDisposed)
        
        // Dispose
        chatWebView.dispose()
        
        // Wait for disposal to complete (async operations need time)
        Thread.sleep(300)
        
        // Verify disposal
        assertTrue("WebView should be disposed after dispose()", chatWebView.webView.isDisposed)
    }
    
    @Test
    fun testMultipleDisposalCallsBasic() {
        // This test validates that multiple dispose calls don't crash
        val mockProject = project
        val editor = Editor(mockProject)
        val chatWebView = ChatWebView(editor) { /* empty handler */ }
        
        // Wait for initialization
        Thread.sleep(200)
        
        // Multiple dispose calls should not crash
        chatWebView.dispose()
        Thread.sleep(100) // Give time for first disposal
        chatWebView.dispose()
        Thread.sleep(100) // Give time for second disposal
        chatWebView.dispose()
        
        // Wait for all disposals to complete
        Thread.sleep(200)
        
        // Should still be disposed
        assertTrue("WebView should remain disposed", chatWebView.webView.isDisposed)
    }
    
    @Test
    fun testBasicMessagePosting() {
        // This test validates basic message posting functionality
        val mockProject = project
        val editor = Editor(mockProject)
        val chatWebView = ChatWebView(editor) { /* empty handler */ }
        
        // Should not crash when posting messages
        try {
            chatWebView.postMessage("test message 1")
            chatWebView.postMessage("""{"type": "test", "payload": {"data": "test"}}""")
            chatWebView.setStyle()
        } catch (e: Exception) {
            fail("Message posting should not fail: ${e.message}")
        }
        
        chatWebView.dispose()
    }
    
    @Test
    fun testComponentAccess() {
        // This test validates component access
        val mockProject = project
        val editor = Editor(mockProject)
        val chatWebView = ChatWebView(editor) { /* empty handler */ }
        
        // Wait for initialization
        Thread.sleep(200)
        
        val component = chatWebView.getComponent()
        assertNotNull("Component should not be null", component)
        assertTrue("Component should be valid", component.isValid)
        
        chatWebView.dispose()
        
        // Wait for disposal to complete
        Thread.sleep(300)
        
        // Component should be invalid after disposal
        assertFalse("Component should be invalid after disposal", component.isValid)
    }
}
