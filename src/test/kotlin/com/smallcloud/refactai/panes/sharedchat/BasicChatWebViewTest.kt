package com.smallcloud.refactai.panes.sharedchat

import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.testUtils.TestableChatWebView
import com.smallcloud.refactai.testUtils.TestableEditor
import org.junit.Test
import org.junit.Assert.*

/**
 * Basic tests for ChatWebView using testable mock implementation.
 * These tests validate core functionality without requiring JCEF initialization.
 */
class BasicChatWebViewTest : LightPlatform4TestCase() {
    
    @Test
    fun testChatWebViewCanBeCreated() {
        // This test validates that ChatWebView can be created without crashing
        // Using testable implementation that doesn't require JCEF
        try {
            val testableEditor = TestableEditor(project)
            val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
            
            // Wait for initialization
            assertTrue("ChatWebView should initialize", chatWebView.waitForInitialization())
            
            // Basic assertions
            assertNotNull("ChatWebView should be created", chatWebView)
            assertNotNull("Component should be available", chatWebView.getComponent())
            assertTrue("Component should be valid", chatWebView.isComponentValid())
            
            // Test basic operations
            chatWebView.setStyle()
            chatWebView.postMessage("test message")
            
            // Verify operations were tracked
            assertEquals("Should have 1 message", 1, chatWebView.messageCount.get())
            assertEquals("Should have 1 style update", 1, chatWebView.styleUpdateCount.get())
            
            // Dispose cleanly
            chatWebView.dispose()
            
            // Verify disposal
            assertTrue("Should dispose properly", chatWebView.waitForDisposal())
            assertTrue("Should be disposed", chatWebView.isDisposed)
            
        } catch (e: Exception) {
            fail("ChatWebView creation should not fail: ${e.message}")
        }
    }
    
    @Test
    fun testChatWebViewDisposalBasic() {
        // This test validates basic disposal functionality
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        // Wait for initialization
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        // Verify initial state
        assertFalse("Should not be disposed initially", chatWebView.isDisposed)
        assertTrue("Component should be valid initially", chatWebView.isComponentValid())
        
        // Dispose
        chatWebView.dispose()
        
        // Verify disposal
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        assertTrue("Should be disposed after dispose()", chatWebView.isDisposed)
    }
    
    @Test
    fun testMultipleDisposalCallsBasic() {
        // This test validates that multiple dispose calls don't crash
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        // Wait for initialization
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        // Multiple dispose calls should not crash
        chatWebView.dispose()
        chatWebView.dispose()
        chatWebView.dispose()
        
        // Wait for disposal to complete
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        
        // Should still be disposed
        assertTrue("Should remain disposed", chatWebView.isDisposed)
    }
    
    @Test
    fun testBasicMessagePosting() {
        // This test validates basic message posting functionality
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        // Wait for initialization
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        // Should not crash when posting messages
        try {
            chatWebView.postMessage("test message 1")
            chatWebView.postMessage("""{"type": "test", "payload": {"data": "test"}}""")
            chatWebView.setStyle()
            
            // Verify operations were tracked
            assertEquals("Should have 2 messages", 2, chatWebView.messageCount.get())
            assertEquals("Should have 1 style update", 1, chatWebView.styleUpdateCount.get())
            
        } catch (e: Exception) {
            fail("Message posting should not fail: ${e.message}")
        }
        
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testComponentAccess() {
        // This test validates component access
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        // Wait for initialization
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        val component = chatWebView.getComponent()
        assertNotNull("Component should not be null", component)
        assertTrue("Component should be valid", chatWebView.isComponentValid())
        
        chatWebView.dispose()
        
        // Wait for disposal to complete
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        
        // Component behavior after disposal - in mock it stays valid but chatWebView is disposed
        assertTrue("ChatWebView should be disposed", chatWebView.isDisposed)
    }
}
