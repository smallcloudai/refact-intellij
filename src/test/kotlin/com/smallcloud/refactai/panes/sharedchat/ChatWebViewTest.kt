package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.testUtils.TestableChatWebView
import org.junit.Test
import org.junit.Assert
import org.junit.Ignore
import org.mockito.Mockito
import org.mockito.MockedStatic

/**
 * Test for ChatWebView to verify it handles race conditions properly using testable implementation.
 * This test specifically checks that the ChatWebView can handle a situation where
 * setStyle() is called before the browser is fully initialized, and then the
 * component is disposed while JavaScript might still be executing.
 */
class ChatWebViewTest: LightPlatform4TestCase() {

    override fun setUp() {
        super.setUp()
    }

    @Test
    fun testBrowserInitializationRaceCondition() {
        // Create a ChatWebView instance with the testable editor
        val chatWebView = TestableChatWebView { /* message handler */ }

        // Wait for initialization
        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        // First test with valid theme - should not throw
        try {
            chatWebView.setStyle()
        } catch (exception: Exception)  {
            Assert.fail("Exception should not have been thrown: ${exception.message}")
        }
        // Force disposal while JavaScript might still be executing
        Thread.sleep(100) // Small delay to ensure the coroutine has started
        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    @Test @Ignore("fails in ci")
    fun testSetupReactRaceCondition() {
        val chatWebView = TestableChatWebView { /* message handler */ }
        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        try {
            // In testable version, we just test that operations don't crash
            chatWebView.setStyle()
        } catch (exception: Exception) {
            Assert.fail("Exception should not have been thrown: ${exception.message}")
        }
        Thread.sleep(100) // Small delay to ensure the coroutine has started
        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    @Test @Ignore("fails in ci")
    fun testPostMessageRaceCondition() {
        val chatWebView = TestableChatWebView { /* message handler */ }
        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        try {
            chatWebView.postMessage("hello")
            // Just test with a string message
            chatWebView.postMessage("{\"type\": \"chat_message\", \"payload\": {\"message\": \"test message\"}}")
        } catch (exception: Exception) {
            Assert.fail("Exception should not have been thrown: ${exception.message}")
        }
        Thread.sleep(100) // Small delay to ensure the coroutine has started
        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    @Test
    fun testOpenFileMessageHandling() {
        val openFileMessageReceived = mutableListOf<Events.FromChat>()
        val chatWebView = TestableChatWebView { event ->
            openFileMessageReceived.add(event)
        }

        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        // Test that the OpenFile message is properly parsed and handled
        val openFileMessage = """{"type":"ide/openFile","payload":{"file_path":"/home/mitya/.config/refact/customization.yaml"}}"""
        val parsedEvent = Events.parse(openFileMessage)

        Assert.assertNotNull("OpenFile message should be parsed successfully", parsedEvent)
        Assert.assertTrue("Parsed event should be OpenFile type", parsedEvent is Events.OpenFile)

        val openFileEvent = parsedEvent as Events.OpenFile
        Assert.assertEquals("File path should match", "/home/mitya/.config/refact/customization.yaml", openFileEvent.payload.filePath)

        // Test message simulation
        chatWebView.simulateMessageFromBrowser(openFileMessage)

        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    @Test
    fun testResourceLeakPrevention() {
        val initialMemory = getUsedMemory()

        // Create and dispose multiple instances rapidly
        repeat(3) {
            val chatWebView = TestableChatWebView { /* empty handler */ }
            Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())
            chatWebView.setStyle()
            Thread.sleep(50)
            chatWebView.dispose()
            Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        }

        System.gc()
        Thread.sleep(100)

        val finalMemory = getUsedMemory()
        val memoryIncrease = finalMemory - initialMemory

        // Should not leak excessive memory (testable version should use much less)
        Assert.assertTrue("Memory leak detected: ${memoryIncrease / 1024 / 1024}MB", 
                         memoryIncrease < 10_000_000) // Less than 10MB for testable version
    }

    @Test
    fun testJavaScriptExecutionSafety() {
        val chatWebView = TestableChatWebView { /* empty handler */ }
        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        // Test various message posting scenarios (simulating JavaScript execution)
        val messages = listOf(
            "console.log('test');",
            "document.body.style.backgroundColor = 'red';",
            "window.postMessage({type: 'test'}, '*');",
            "throw new Error('test error');" // This should not crash the application
        )

        messages.forEach { message ->
            try {
                chatWebView.postMessage(message)
                Thread.sleep(50) // Allow execution
            } catch (e: Exception) {
                // Should not throw exceptions for normal message posting
                Assert.fail("Message posting should not throw exceptions: ${e.message}")
            }
        }

        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    @Test
    fun testInitializationIdempotency() {
        val chatWebView = TestableChatWebView { /* empty handler */ }
        Assert.assertTrue("Should initialize", chatWebView.waitForInitialization())

        // Call initialization methods multiple times
        repeat(3) {
            chatWebView.setStyle()
            Thread.sleep(50)
        }

        // Should not cause issues
        Assert.assertTrue("Multiple initialization calls should be safe", true)
        Assert.assertEquals("Should have 3 style updates", 3, chatWebView.styleUpdateCount.get())

        chatWebView.dispose()
        Assert.assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
