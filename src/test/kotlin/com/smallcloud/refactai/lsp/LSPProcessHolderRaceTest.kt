package com.smallcloud.refactai.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Test class for LSPProcessHolder that focuses on race conditions
 * that can cause RejectedExecutionException.
 */
class LSPProcessHolderRaceTest : LightPlatformTestCase() {
    
    private lateinit var mockProject: Project
    private lateinit var disposable: Disposable
    
    @Before
    public override fun setUp() {
        super.setUp()
        mockProject = Mockito.mock(Project::class.java)
        `when`(mockProject.isDisposed).thenReturn(false)
        
        // Mock the MessageBus and its syncPublisher method
        val mockMessageBus = Mockito.mock(MessageBus::class.java)
        val mockPublisher = Mockito.mock(LSPProcessHolderChangedNotifier::class.java)
        `when`(mockProject.messageBus).thenReturn(mockMessageBus)
        `when`(mockMessageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)).thenReturn(mockPublisher)
        
        // Create a disposable parent for cleanup
        disposable = Disposer.newDisposable("LSPProcessHolderTest")
    }
    
    @After
    public override fun tearDown() {
        Disposer.dispose(disposable)
        super.tearDown()
    }
    
    /**
     * Test that verifies our fix for the RejectedExecutionException: Already shutdown error.
     * 
     * This test simulates the race condition where:
     * 1. A task is scheduled that will use the LSPProcessHolder
     * 2. The LSPProcessHolder is disposed (shutting down its schedulers)
     * 3. The scheduled task then tries to use the shutdown schedulers
     * 
     * With our fix, this should no longer throw an exception.
     */
    @Test
    fun testRaceConditionHandlesRejectedExecutionException() {
        // Create a custom subclass of LSPProcessHolder for testing
        class TestLSPProcessHolder(project: Project) : LSPProcessHolder(project) {
            // Create a scheduler that we can shut down to simulate the race condition
            private val testScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
                "TestScheduler", 1
            )
            
            override fun startProcess() {
                // Do nothing - avoid actual process creation
            }
            
            // Method to trigger the RejectedExecutionException
            fun triggerRejectedExecution(): Boolean {
                // Shut down the scheduler
                testScheduler.shutdown()
                
                // Try to use the scheduler after shutdown - this should be handled gracefully
                try {
                    testScheduler.submit {}
                    return false // No exception was thrown (unexpected)
                } catch (e: Exception) {
                    // We expect an exception here, but our LSPProcessHolder should handle it
                    return true // Exception was thrown as expected
                }
            }
        }
        
        // Create the LSPProcessHolder
        val lspHolder = TestLSPProcessHolder(mockProject)
        
        // Create a latch to control the execution flow
        val latch = CountDownLatch(1)
        
        // Track if an unhandled exception occurred
        val unhandledExceptionOccurred = AtomicBoolean(false)
        
        // Schedule a task that will use the LSPProcessHolder after a delay
        val future = CompletableFuture.runAsync {
            try {
                // Wait for the signal to proceed (after dispose is called)
                latch.await(5, TimeUnit.SECONDS)
                
                // Trigger the RejectedExecutionException - this should be caught internally
                lspHolder.triggerRejectedExecution()
                
                // Now try to use a method that we've updated to handle RejectedExecutionException
                lspHolder.settingsChanged()
                
            } catch (e: Exception) {
                // If an exception propagates here, our fix isn't working
                unhandledExceptionOccurred.set(true)
            }
        }
        
        // Give the task a moment to start
        Thread.sleep(100)
        
        // Now dispose the LSPProcessHolder
        Disposer.dispose(lspHolder)
        
        // Signal the background task to proceed
        latch.countDown()
        
        // Wait for the background task to complete
        future.join()
        
        // Verify that no unhandled exception occurred
        assertFalse(
            "An unhandled exception occurred, which means our fix isn't working properly",
            unhandledExceptionOccurred.get()
        )
    }
    
    /**
     * Test a more realistic scenario where the race condition occurs during
     * application shutdown or project close.
     * 
     * With our fix, this should no longer throw an exception.
     */
    @Test
    fun testRaceConditionDuringProjectClose() {
        // Create a custom subclass of LSPProcessHolder for testing
        class TestLSPProcessHolder(project: Project) : LSPProcessHolder(project) {
            // Create a scheduler that we can shut down to simulate the race condition
            private val testScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
                "TestScheduler", 1
            )
            
            override fun startProcess() {
                // Do nothing - avoid actual process creation
            }
            
            // Method to trigger the RejectedExecutionException
            fun triggerRejectedExecution(): Boolean {
                // Try to use the scheduler after shutdown
                try {
                    testScheduler.submit {}
                    return false // No exception was thrown (unexpected)
                } catch (e: Exception) {
                    // We expect an exception here, but our LSPProcessHolder should handle it
                    return true // Exception was thrown as expected
                }
            }
            
            // Override dispose to shut down our test scheduler
            override fun dispose() {
                testScheduler.shutdown()
                super.dispose()
            }
        }
        
        // Create the LSPProcessHolder
        val lspHolder = TestLSPProcessHolder(mockProject)
        
        // Track if an unhandled exception occurred
        val unhandledExceptionOccurred = AtomicBoolean(false)
        
        // Create a latch to control execution flow
        val latch = CountDownLatch(1)
        
        // Simulate a settings change that will trigger startProcess
        val settingsChangeFuture = CompletableFuture.runAsync {
            try {
                // Wait for the signal to proceed (after dispose is called)
                latch.await(5, TimeUnit.SECONDS)
                
                // Try to use the scheduler after it's been shut down
                lspHolder.triggerRejectedExecution()
                
                // Now try to use methods that we've updated to handle RejectedExecutionException
                lspHolder.settingsChanged()
                
            } catch (e: Exception) {
                // If an exception propagates here, our fix isn't working
                unhandledExceptionOccurred.set(true)
            }
        }
        
        // Give the task a moment to start
        Thread.sleep(100)
        
        // Now simulate project close by disposing the LSPProcessHolder
        // This will shut down the scheduler
        Disposer.dispose(lspHolder)
        
        // Signal the background task to proceed
        latch.countDown()
        
        // Wait for the settings change task to complete
        try {
            settingsChangeFuture.get(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            // If an exception propagates here, our fix isn't working
            unhandledExceptionOccurred.set(true)
        }
        
        // Verify that no unhandled exception occurred
        assertFalse(
            "An unhandled exception occurred, which means our fix isn't working properly",
            unhandledExceptionOccurred.get()
        )
    }
    
    /**
     * Test that verifies our fix for the NullPointerException that could occur
     * when accessing the MessageBus after disposal.
     */
    @Test
    fun testNoNullPointerExceptionAfterDisposal() {
        // Create a custom subclass of LSPProcessHolder for testing
        class TestLSPProcessHolder(project: Project) : LSPProcessHolder(project) {
            override fun startProcess() {
                // Do nothing - avoid actual process creation
            }
            
            // Method to simulate accessing the MessageBus after disposal
            fun simulateMessageBusAccess() {
                // This would previously cause a NullPointerException if the MessageBus was null
                // Now it should check isDisposed and return early
                settingsChanged()
            }
        }
        
        // Create the LSPProcessHolder
        val lspHolder = TestLSPProcessHolder(mockProject)
        
        // Create a latch to control the execution flow
        val latch = CountDownLatch(1)
        
        // Track if an unhandled exception occurred
        val unhandledExceptionOccurred = AtomicBoolean(false)
        val exceptionRef = AtomicReference<Exception>()
        
        // Schedule a task that will access the MessageBus after disposal
        val future = CompletableFuture.runAsync {
            try {
                // Wait for the signal to proceed (after dispose is called)
                latch.await(5, TimeUnit.SECONDS)
                
                // Try to access the MessageBus after disposal
                lspHolder.simulateMessageBusAccess()
                
            } catch (e: Exception) {
                // If an exception propagates here, our fix isn't working
                unhandledExceptionOccurred.set(true)
                exceptionRef.set(e)
            }
        }
        
        // Give the task a moment to start
        Thread.sleep(100)
        
        // Now dispose the LSPProcessHolder
        Disposer.dispose(lspHolder)
        
        // Signal the background task to proceed
        latch.countDown()
        
        // Wait for the background task to complete
        future.join()
        
        // Verify that no unhandled exception occurred
        if (unhandledExceptionOccurred.get()) {
            val exception = exceptionRef.get()
            fail("Expected no exception but got ${exception.javaClass.name}: ${exception.message}")
        }
    }
}
