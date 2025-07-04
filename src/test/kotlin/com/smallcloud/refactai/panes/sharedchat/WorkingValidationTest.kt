package com.smallcloud.refactai.panes.sharedchat

import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.testUtils.TestableEditor
import com.smallcloud.refactai.testUtils.TestableChatWebView
import com.smallcloud.refactai.utils.CefLifecycleManager
import com.smallcloud.refactai.utils.AsyncMessageHandler
import com.smallcloud.refactai.utils.JavaScriptExecutor
import com.smallcloud.refactai.utils.ThemeManager
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Working validation tests that actually test our improvements without requiring
 * full JCEF infrastructure.
 */
class WorkingValidationTest : LightPlatform4TestCase() {

    @Test
    fun testBasicChatWebViewFunctionality() {
        val testableEditor = TestableEditor(project)
        val messageCount = AtomicInteger(0)
        
        val chatWebView = TestableChatWebView(testableEditor) { 
            messageCount.incrementAndGet()
        }
        
        // Wait for initialization
        assertTrue("ChatWebView should initialize", chatWebView.waitForInitialization())
        
        // Test basic operations
        chatWebView.setStyle()
        chatWebView.postMessage("test message")
        
        // Wait for message processing
        Thread.sleep(200)
        
        // Verify functionality
        assertEquals("Should have 1 message", 1, chatWebView.messageCount.get())
        assertEquals("Should have 1 style update", 1, chatWebView.styleUpdateCount.get())
        assertTrue("Component should be valid", chatWebView.getComponent().isValid())
        
        // Test disposal
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        assertFalse("Component should be invalid after disposal", chatWebView.getComponent().isValid())
    }

    @Test
    fun testMemoryLeakPrevention() {
        val initialMemory = getUsedMemory()
        val testableEditor = TestableEditor(project)
        
        // Create multiple instances
        repeat(5) { i ->
            val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
            chatWebView.waitForInitialization()
            
            // Perform operations
            chatWebView.setStyle()
            chatWebView.postMessage("memory test $i")
            Thread.sleep(50)
            
            // Dispose
            chatWebView.dispose()
            chatWebView.waitForDisposal()
        }
        
        // Force garbage collection
        System.gc()
        Thread.sleep(500)
        System.gc()
        
        val finalMemory = getUsedMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        println("Memory test: ${memoryIncrease / 1024 / 1024}MB increase")
        
        // Should not leak excessive memory (less than 20MB for 5 instances)
        assertTrue("Memory leak detected: ${memoryIncrease / 1024 / 1024}MB", 
                  memoryIncrease < 20_000_000)
    }

    @Test
    fun testAsyncMessageHandling() {
        val processedMessages = mutableListOf<String>()
        val lock = Object()
        
        val handler = AsyncMessageHandler<String>(
            parser = { msg -> msg }, // Identity parser for test
            dispatcher = { msg ->
                synchronized(lock) {
                    processedMessages.add(msg)
                }
            }
        )
        
        // Send messages
        val messageCount = 10
        repeat(messageCount) { i ->
            assertTrue("Should accept message $i", handler.offerMessage("test message $i"))
        }
        
        // Wait for processing
        Thread.sleep(500)
        
        // Verify async processing
        assertTrue("Should process most messages", processedMessages.size >= messageCount * 0.8)
        
        handler.dispose()
    }

    @Test
    fun testCefLifecycleManager() {
        // Test browser count tracking
        val initialCount = CefLifecycleManager.getActiveBrowserCount()
        
        // Test initialization
        CefLifecycleManager.initIfNeeded()
        assertTrue("Should be initialized", CefLifecycleManager.isInitialized())
        
        // The count should not have changed just from initialization
        val countAfterInit = CefLifecycleManager.getActiveBrowserCount()
        assertEquals("Browser count should be stable", initialCount, countAfterInit)
        
        println("CefLifecycleManager test: initial=$initialCount, after_init=$countAfterInit")
    }

    @Test
    fun testThreadSafetyBasic() {
        val testableEditor = TestableEditor(project)
        val exceptions = mutableListOf<Exception>()
        val successCount = AtomicInteger(0)
        
        val chatWebView = TestableChatWebView(testableEditor) { 
            successCount.incrementAndGet()
        }
        
        chatWebView.waitForInitialization()
        
        val threadCount = 5
        val operationsPerThread = 10
        val latch = CountDownLatch(threadCount)
        
        // Perform concurrent operations
        repeat(threadCount) { threadId ->
            Thread {
                try {
                    repeat(operationsPerThread) { opId ->
                        when (opId % 3) {
                            0 -> chatWebView.setStyle()
                            1 -> chatWebView.postMessage("thread $threadId operation $opId")
                            2 -> chatWebView.postMessage("""{"type": "test", "payload": {"id": "$threadId-$opId"}}""")
                        }
                        Thread.sleep(5)
                    }
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        assertTrue("Threads should complete", latch.await(30, TimeUnit.SECONDS))
        
        // Wait for message processing
        Thread.sleep(1000)
        
        // Verify no exceptions occurred
        if (exceptions.isNotEmpty()) {
            fail("Thread safety issues: ${exceptions.map { it.message }}")
        }
        
        // Verify operations were processed
        assertTrue("Should process operations", chatWebView.messageCount.get() > 0)
        assertTrue("Should update styles", chatWebView.styleUpdateCount.get() > 0)
        
        chatWebView.dispose()
        chatWebView.waitForDisposal()
    }

    @Test
    fun testPerformanceBasic() {
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        chatWebView.waitForInitialization()
        
        val operationCount = 50
        val startTime = System.currentTimeMillis()
        
        // Perform operations
        repeat(operationCount) { i ->
            when (i % 3) {
                0 -> chatWebView.setStyle()
                1 -> chatWebView.postMessage("performance test $i")
                2 -> chatWebView.postMessage("""{"type": "perf", "payload": {"id": $i}}""")
            }
        }
        
        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val avgTimePerOperation = totalTime.toDouble() / operationCount
        
        println("Performance test: ${totalTime}ms total, ${avgTimePerOperation}ms avg")
        
        // Performance should be reasonable
        assertTrue("Operations too slow: ${avgTimePerOperation}ms per operation", 
                  avgTimePerOperation < 100) // 100ms per operation max
        
        chatWebView.dispose()
        chatWebView.waitForDisposal()
    }

    @Test
    fun testDisposalChainBasic() {
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        chatWebView.waitForInitialization()
        
        // Perform operations
        chatWebView.setStyle()
        chatWebView.postMessage("disposal test")
        Thread.sleep(100)
        
        // Test disposal
        assertFalse("Should not be disposed initially", chatWebView.isDisposed)
        
        chatWebView.dispose()
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        
        // Multiple disposal calls should be safe
        chatWebView.dispose()
        chatWebView.dispose()
        
        assertTrue("Should remain disposed", chatWebView.isDisposed)
    }

    @Test
    fun testResourceManagerComponents() {
        // Test AsyncMessageHandler
        val handler1 = AsyncMessageHandler<String>({ it }, { })
        assertFalse("Handler should not be disposed initially", handler1.isDisposed())
        assertEquals("Should have empty queue", 0, handler1.getQueueSize())
        
        handler1.dispose()
        assertTrue("Handler should be disposed", handler1.isDisposed())
        
        // Test that we can create multiple instances
        val handler2 = AsyncMessageHandler<String>({ it }, { })
        assertFalse("New handler should not be disposed", handler2.isDisposed())
        handler2.dispose()
    }

    @Test
    fun testConcurrentDisposal() {
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        chatWebView.waitForInitialization()
        
        val threadCount = 3
        val latch = CountDownLatch(threadCount)
        val exceptions = mutableListOf<Exception>()
        
        // Try to dispose from multiple threads
        repeat(threadCount) { threadId ->
            Thread {
                try {
                    Thread.sleep(threadId * 50L) // Stagger slightly
                    chatWebView.dispose()
                } catch (e: Exception) {
                    synchronized(exceptions) {
                        exceptions.add(e)
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        assertTrue("Disposal threads should complete", latch.await(5, TimeUnit.SECONDS))
        
        // Should not have exceptions
        if (exceptions.isNotEmpty()) {
            fail("Concurrent disposal failed: ${exceptions.map { it.message }}")
        }
        
        assertTrue("Should be disposed", chatWebView.waitForDisposal())
    }

    @Test
    fun testStabilityUnderLoad() {
        val testableEditor = TestableEditor(project)
        val processedCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)
        
        val chatWebView = TestableChatWebView(testableEditor) { event ->
            try {
                processedCount.incrementAndGet()
                // Simulate some processing
                Thread.sleep(1)
            } catch (e: Exception) {
                errorCount.incrementAndGet()
            }
        }
        
        chatWebView.waitForInitialization()
        
        val operationCount = 100
        
        // Generate load
        repeat(operationCount) { i ->
            when (i % 4) {
                0 -> chatWebView.setStyle()
                1 -> chatWebView.postMessage("load test $i")
                2 -> chatWebView.postMessage("""{"type": "load", "payload": {"id": $i}}""")
                3 -> {
                    // Rapid style updates
                    repeat(3) { chatWebView.setStyle() }
                }
            }
        }
        
        // Wait for processing
        Thread.sleep(2000)
        
        val processed = processedCount.get()
        val errors = errorCount.get()
        
        println("Stability test: $processed processed, $errors errors, ${chatWebView.messageCount.get()} messages sent")
        
        // System should remain stable
        assertFalse("Should not be disposed under load", chatWebView.isDisposed)
        assertTrue("Should process reasonable number of operations", processed > 0)
        assertTrue("Error rate should be low", errors < processed * 0.1) // Less than 10% errors
        
        chatWebView.dispose()
        chatWebView.waitForDisposal()
    }

    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
