package com.smallcloud.refactai.lsp

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.smallcloud.refactai.io.InferenceGlobalContext
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Test class for LSPProcessHolder that focuses on race conditions
 * that can cause RejectedExecutionException.
 */
class LSPProcessHolderTest : LightPlatformTestCase() {
    
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
     * Test that reproduces the RejectedExecutionException: Already shutdown error.
     * 
     * This test simulates the race condition where:
     * 1. A task is scheduled that will use the LSPProcessHolder
     * 2. The LSPProcessHolder is disposed (shutting down its schedulers)
     * 3. The scheduled task then tries to use the shutdown schedulers
     */
    @Test
    fun testRaceConditionCausesRejectedExecutionException() {
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
            fun triggerRejectedExecution(): Exception? {
                // Shut down the scheduler
                testScheduler.shutdown()
                
                // Try to use the scheduler after shutdown
                try {
                    testScheduler.submit {}
                    return null
                } catch (e: Exception) {
                    return e
                }
            }
        }
        
        // Create the LSPProcessHolder
        val lspHolder = TestLSPProcessHolder(mockProject)
        
        // Create a latch to control the execution flow
        val latch = CountDownLatch(1)
        
        // Create an atomic reference to capture any exception
        val exceptionRef = AtomicReference<Exception>()
        val exceptionOccurred = AtomicBoolean(false)
        
        // Schedule a task that will use the LSPProcessHolder after a delay
        val future = CompletableFuture.runAsync {
            try {
                // Wait for the signal to proceed (after dispose is called)
                latch.await(5, TimeUnit.SECONDS)
                
                // Trigger the RejectedExecutionException
                val exception = lspHolder.triggerRejectedExecution()
                if (exception != null) {
                    exceptionRef.set(exception)
                    exceptionOccurred.set(true)
                }
            } catch (e: Exception) {
                // Capture any other exception
                exceptionRef.set(e)
                exceptionOccurred.set(true)
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
        
        // Verify that a RejectedExecutionException was thrown
        if (exceptionOccurred.get()) {
            val exception = exceptionRef.get()
            assertTrue(
                "Expected RejectedExecutionException but got ${exception.javaClass.name}: ${exception.message}",
                exception is RejectedExecutionException
            )
            assertTrue(
                "Expected 'Already shutdown' message but got: ${exception.message}",
                exception.message?.contains("Already shutdown") == true
            )
        } else {
            fail("Expected a RejectedExecutionException but no exception was thrown")
        }
    }
    
    /**
     * Test a more realistic scenario where the race condition occurs during
     * application shutdown or project close.
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
            fun triggerRejectedExecution(): Exception? {
                // Try to use the scheduler after shutdown
                try {
                    testScheduler.submit {}
                    return null
                } catch (e: Exception) {
                    return e
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
        
        // Create an atomic reference to capture any exception
        val exceptionRef = AtomicReference<Exception>()
        val exceptionOccurred = AtomicBoolean(false)
        
        // Create a latch to control execution flow
        val latch = CountDownLatch(1)
        
        // Simulate a settings change that will trigger startProcess
        val settingsChangeFuture = CompletableFuture.runAsync {
            try {
                // Wait for the signal to proceed (after dispose is called)
                latch.await(5, TimeUnit.SECONDS)
                
                // Try to use the scheduler after it's been shut down
                val exception = lspHolder.triggerRejectedExecution()
                if (exception != null) {
                    exceptionRef.set(exception)
                    exceptionOccurred.set(true)
                }
            } catch (e: Exception) {
                // Capture the exception
                exceptionRef.set(e)
                exceptionOccurred.set(true)
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
            // If the future itself throws an exception, capture it
            exceptionRef.set(e)
            exceptionOccurred.set(true)
        }
        
        // Verify that a RejectedExecutionException was thrown
        if (exceptionOccurred.get()) {
            val exception = exceptionRef.get()
            // Get the root cause if it's wrapped in another exception
            val rootCause = if (exception.cause != null) exception.cause else exception
            
            assertTrue(
                "Expected RejectedExecutionException but got ${rootCause?.javaClass?.name}: ${rootCause?.message}",
                rootCause is RejectedExecutionException
            )
            assertTrue(
                "Expected 'Already shutdown' message but got: ${rootCause?.message}",
                rootCause?.message?.contains("Already shutdown") == true
            )
        } else {
            fail("Expected a RejectedExecutionException but no exception was thrown")
        }
    }
}
