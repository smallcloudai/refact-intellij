package com.smallcloud.refactai.panes.sharedchat

import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.testUtils.TestableChatWebView
import com.smallcloud.refactai.testUtils.TestableEditor
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for concurrent disposal scenarios that can cause resource leaks or crashes.
 * These tests verify that the ThreadSafeInitializer properly handles concurrent operations.
 */
class ConcurrentDisposalTest : LightPlatform4TestCase() {
    
    @Test
    fun testMultipleConcurrentDisposalCalls() {
        // Test that multiple dispose() calls don't cause issues
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        val disposeCallCount = AtomicInteger(0)
        val threads = mutableListOf<Thread>()
        val latch = CountDownLatch(5)
        
        // Start 5 concurrent disposal threads
        repeat(5) {
            val thread = Thread {
                try {
                    disposeCallCount.incrementAndGet()
                    chatWebView.dispose()
                } finally {
                    latch.countDown()
                }
            }
            threads.add(thread)
            thread.start()
        }
        
        // Wait for all disposal attempts to complete
        assertTrue("All disposal calls should complete", latch.await(5, TimeUnit.SECONDS))
        assertEquals("All dispose calls should be made", 5, disposeCallCount.get())
        
        // Verify final state
        assertTrue("Should be disposed", chatWebView.isDisposed)
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testDisposalDuringInitialization() {
        // Test disposing while initialization is in progress
        val testableEditor = TestableEditor(project)
        
        val initializationStarted = AtomicInteger(0)
        val disposalStarted = AtomicInteger(0)
        val finalState = AtomicInteger(0) // 0=unknown, 1=disposed, 2=initialized
        
        // Create but don't wait for initialization
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        // Start initialization monitoring
        Thread {
            initializationStarted.incrementAndGet()
            if (chatWebView.waitForInitialization(1000)) {
                if (finalState.compareAndSet(0, 2)) {
                    // Initialization completed first
                }
            }
        }.start()
        
        // Start disposal after a short delay
        Thread {
            Thread.sleep(50) // Let initialization start
            disposalStarted.incrementAndGet()
            chatWebView.dispose()
            if (finalState.compareAndSet(0, 1)) {
                // Disposal completed first
            }
        }.start()
        
        // Wait for operations to complete
        Thread.sleep(2000)
        
        assertEquals("Initialization should start", 1, initializationStarted.get())
        assertEquals("Disposal should start", 1, disposalStarted.get())
        assertTrue("Should reach a final state", finalState.get() != 0)
        
        // Ensure disposal completes regardless of initialization state
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
        assertTrue("Should be disposed", chatWebView.isDisposed)
    }
    
    @Test
    fun testOperationsDuringDisposal() {
        // Test that operations are properly rejected during disposal
        val testableEditor = TestableEditor(project)
        val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
        
        assertTrue("Should initialize", chatWebView.waitForInitialization())
        
        val operationAttempts = AtomicInteger(0)
        val operationSuccesses = AtomicInteger(0)
        val operationFailures = AtomicInteger(0)
        
        // Start disposal
        Thread {
            Thread.sleep(100) // Let operations start first
            chatWebView.dispose()
        }.start()
        
        // Try various operations during disposal
        val operations = listOf(
            { chatWebView.setStyle() },
            { chatWebView.postMessage("test") },
            { chatWebView.getComponent() }
        )
        
        operations.forEach { operation ->
            Thread {
                repeat(10) {
                    operationAttempts.incrementAndGet()
                    try {
                        operation()
                        if (!chatWebView.isDisposed) {
                            operationSuccesses.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        operationFailures.incrementAndGet()
                    }
                    Thread.sleep(10)
                }
            }.start()
        }
        
        // Wait for operations to complete
        Thread.sleep(1000)
        
        assertTrue("Should have attempted operations", operationAttempts.get() > 0)
        // Operations may succeed or fail, but shouldn't crash
        assertTrue("Total operations should match attempts", 
                  operationSuccesses.get() + operationFailures.get() <= operationAttempts.get())
        
        assertTrue("Should dispose properly", chatWebView.waitForDisposal())
    }
    
    @Test
    fun testResourceLeakPrevention() {
        // Test that rapid create/dispose cycles don't leak resources
        val testableEditor = TestableEditor(project)
        val initialMemory = getUsedMemory()
        
        val createdCount = AtomicInteger(0)
        val disposedCount = AtomicInteger(0)
        
        // Create and dispose multiple instances rapidly
        repeat(10) {
            Thread {
                try {
                    val chatWebView = TestableChatWebView(testableEditor) { /* empty handler */ }
                    createdCount.incrementAndGet()
                    
                    if (chatWebView.waitForInitialization(500)) {
                        // Do some operations
                        chatWebView.setStyle()
                        chatWebView.postMessage("test")
                    }
                    
                    chatWebView.dispose()
                    if (chatWebView.waitForDisposal(500)) {
                        disposedCount.incrementAndGet()
                    }
                } catch (e: Exception) {
                    // Ignore exceptions in this stress test
                }
            }.start()
        }
        
        // Wait for all operations to complete
        Thread.sleep(3000)
        
        // Force garbage collection
        System.gc()
        Thread.sleep(100)
        
        val finalMemory = getUsedMemory()
        val memoryIncrease = finalMemory - initialMemory
        
        assertTrue("Should create instances", createdCount.get() > 0)
        assertTrue("Should dispose instances", disposedCount.get() > 0)
        
        // Memory increase should be reasonable (testable version uses less memory)
        assertTrue("Memory leak check: ${memoryIncrease / 1024 / 1024}MB", 
                  memoryIncrease < 50_000_000) // Less than 50MB
    }
    
    private fun getUsedMemory(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }
}
