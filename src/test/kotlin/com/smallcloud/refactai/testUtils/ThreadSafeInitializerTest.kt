package com.smallcloud.refactai.testUtils

import com.smallcloud.refactai.utils.ThreadSafeInitializer
import org.junit.Test
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for ThreadSafeInitializer to verify proper state management and thread safety.
 */
class ThreadSafeInitializerTest {
    
    @Test
    fun testBasicStateTransitions() {
        val initializer = ThreadSafeInitializer()
        
        // Initial state
        assertEquals("Should start in NOT_STARTED state", 
                    ThreadSafeInitializer.State.NOT_STARTED, initializer.getCurrentState())
        assertFalse("Should not be ready initially", initializer.isReady())
        assertFalse("Should not be disposed initially", initializer.isDisposedOrDisposing())
        
        // Transition to initializing
        assertTrue("Should transition to INITIALIZING", 
                  initializer.transitionTo(ThreadSafeInitializer.State.NOT_STARTED, 
                                         ThreadSafeInitializer.State.INITIALIZING))
        
        // Transition to JS bridge ready
        assertTrue("Should transition to JS_BRIDGE_READY", 
                  initializer.transitionTo(ThreadSafeInitializer.State.INITIALIZING, 
                                         ThreadSafeInitializer.State.JS_BRIDGE_READY))
        
        // Transition to React initializing
        assertTrue("Should transition to REACT_INITIALIZING", 
                  initializer.transitionTo(ThreadSafeInitializer.State.JS_BRIDGE_READY, 
                                         ThreadSafeInitializer.State.REACT_INITIALIZING))
        
        // Transition to fully ready
        assertTrue("Should transition to FULLY_READY", 
                  initializer.transitionTo(ThreadSafeInitializer.State.REACT_INITIALIZING, 
                                         ThreadSafeInitializer.State.FULLY_READY))
        
        assertTrue("Should be ready", initializer.isReady())
        assertTrue("Should be initialization complete", initializer.isInitializationComplete())
        
        // Start disposal
        assertTrue("Should start disposal", initializer.startDisposal())
        assertEquals("Should be in DISPOSING state", 
                    ThreadSafeInitializer.State.DISPOSING, initializer.getCurrentState())
        
        // Complete disposal
        assertTrue("Should transition to DISPOSED", 
                  initializer.transitionTo(ThreadSafeInitializer.State.DISPOSING, 
                                         ThreadSafeInitializer.State.DISPOSED))
        
        assertTrue("Should be disposed", initializer.isDisposedOrDisposing())
    }
    
    @Test
    fun testInvalidStateTransitions() {
        val initializer = ThreadSafeInitializer()
        
        // Verify initial state
        assertEquals("Should start in NOT_STARTED", 
                    ThreadSafeInitializer.State.NOT_STARTED, initializer.getCurrentState())
        
        // Try invalid transition - skip multiple states
        val result1 = initializer.transitionTo(ThreadSafeInitializer.State.NOT_STARTED, 
                                              ThreadSafeInitializer.State.FULLY_READY)
        assertFalse("Should reject invalid transition", result1)
        
        // State should remain unchanged
        assertEquals("State should remain NOT_STARTED", 
                    ThreadSafeInitializer.State.NOT_STARTED, initializer.getCurrentState())
        
        // Try another invalid transition - wrong expected state
        val result2 = initializer.transitionTo(ThreadSafeInitializer.State.INITIALIZING, 
                                              ThreadSafeInitializer.State.JS_BRIDGE_READY)
        assertFalse("Should reject transition with wrong expected state", result2)
        
        // State should still remain unchanged
        assertEquals("State should still remain NOT_STARTED", 
                    ThreadSafeInitializer.State.NOT_STARTED, initializer.getCurrentState())
    }
    
    @Test
    fun testConcurrentStateTransitions() {
        val initializer = ThreadSafeInitializer()
        val successCount = AtomicInteger(0)
        val failureCount = AtomicInteger(0)
        val latch = CountDownLatch(5)
        
        // Try concurrent transitions from NOT_STARTED to INITIALIZING
        repeat(5) {
            Thread {
                try {
                    if (initializer.transitionTo(ThreadSafeInitializer.State.NOT_STARTED, 
                                               ThreadSafeInitializer.State.INITIALIZING)) {
                        successCount.incrementAndGet()
                    } else {
                        failureCount.incrementAndGet()
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        assertTrue("All threads should complete", latch.await(5, TimeUnit.SECONDS))
        assertEquals("Only one transition should succeed", 1, successCount.get())
        assertEquals("Four transitions should fail", 4, failureCount.get())
        assertEquals("Should be in INITIALIZING state", 
                    ThreadSafeInitializer.State.INITIALIZING, initializer.getCurrentState())
    }
    
    @Test
    fun testWaitForInitialization() {
        val initializer = ThreadSafeInitializer()
        
        // Start initialization in background
        Thread {
            Thread.sleep(100)
            initializer.transitionTo(ThreadSafeInitializer.State.NOT_STARTED, 
                                   ThreadSafeInitializer.State.INITIALIZING)
            Thread.sleep(100)
            initializer.transitionTo(ThreadSafeInitializer.State.INITIALIZING, 
                                   ThreadSafeInitializer.State.JS_BRIDGE_READY)
            Thread.sleep(100)
            initializer.transitionTo(ThreadSafeInitializer.State.JS_BRIDGE_READY, 
                                   ThreadSafeInitializer.State.REACT_INITIALIZING)
            Thread.sleep(100)
            initializer.transitionTo(ThreadSafeInitializer.State.REACT_INITIALIZING, 
                                   ThreadSafeInitializer.State.FULLY_READY)
        }.start()
        
        // Wait for initialization
        assertTrue("Should complete initialization", initializer.waitForInitialization(1000))
        assertTrue("Should be ready", initializer.isReady())
    }
    
    @Test
    fun testWaitForInitializationTimeout() {
        val initializer = ThreadSafeInitializer()
        
        // Don't start initialization - should timeout
        assertFalse("Should timeout", initializer.waitForInitialization(100))
        assertFalse("Should not be ready", initializer.isReady())
    }
    
    @Test
    fun testMarkFailed() {
        val initializer = ThreadSafeInitializer()
        val testException = Exception("Test error")
        
        initializer.markFailed(testException)
        
        assertEquals("Should be in FAILED state", 
                    ThreadSafeInitializer.State.FAILED, initializer.getCurrentState())
        assertTrue("Should be initialization complete", initializer.isInitializationComplete())
        assertFalse("Should not be ready", initializer.isReady())
        assertEquals("Should store error", testException, initializer.getInitializationError())
        
        // Wait should return false for failed initialization
        assertFalse("Wait should return false for failed init", initializer.waitForInitialization(100))
    }
    
    @Test
    fun testMultipleDisposalAttempts() {
        val initializer = ThreadSafeInitializer()
        
        // First disposal should succeed
        assertTrue("First disposal should succeed", initializer.startDisposal())
        assertEquals("Should be in DISPOSING state", 
                    ThreadSafeInitializer.State.DISPOSING, initializer.getCurrentState())
        
        // Second disposal should fail
        assertFalse("Second disposal should fail", initializer.startDisposal())
        assertEquals("Should remain in DISPOSING state", 
                    ThreadSafeInitializer.State.DISPOSING, initializer.getCurrentState())
    }
    
    @Test
    fun testForceDisposed() {
        val initializer = ThreadSafeInitializer()
        
        initializer.forceDisposed()
        
        assertEquals("Should be in DISPOSED state", 
                    ThreadSafeInitializer.State.DISPOSED, initializer.getCurrentState())
        assertTrue("Should be disposed", initializer.isDisposedOrDisposing())
        assertTrue("Should be initialization complete", initializer.isInitializationComplete())
        
        // Wait should return immediately
        assertTrue("Wait should return immediately", initializer.waitForDisposal(100))
    }
    
    @Test
    fun testWaitForDisposal() {
        val initializer = ThreadSafeInitializer()
        
        // Start disposal in background
        Thread {
            Thread.sleep(100)
            initializer.startDisposal()
            Thread.sleep(100)
            initializer.transitionTo(ThreadSafeInitializer.State.DISPOSING, 
                                   ThreadSafeInitializer.State.DISPOSED)
        }.start()
        
        // Wait for disposal
        assertTrue("Should complete disposal", initializer.waitForDisposal(500))
        assertTrue("Should be disposed", initializer.isDisposedOrDisposing())
    }
}
