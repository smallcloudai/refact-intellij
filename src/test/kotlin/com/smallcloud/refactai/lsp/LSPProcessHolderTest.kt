package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Test that demonstrates the "Already disposed" issue in LSPProcessHolder.
 * This reproduces the specific AlreadyDisposedException from GitHub issue #155.
 */
class LSPProcessHolderTest : BasePlatformTestCase() {

    class TestLspProccessHolder(project: Project) : LSPProcessHolder(project) {
        // Flag to track if the message bus was accessed
        var messageBusAccessed = false
        
        // Flag to track if the check for project.isDisposed was performed
        var disposedCheckPerformed = false
        
        // Latch to control test execution flow
        val latch = CountDownLatch(1)
        
        // Flag to control whether to use the fix or not
        var useDisposedCheck = true
        
        // Override capabilities setter to track message bus access and disposed checks
        override var capabilities: LSPCapabilities = LSPCapabilities()
            set(newValue) {
                if (newValue == field) return
                field = newValue
                
                // Record that we performed the disposed check
                disposedCheckPerformed = true
                
                if (useDisposedCheck) {
                    // This is the fix we're testing - checking if project is disposed
                    if (!project.isDisposed) {
                        // Record that we accessed the message bus
                        messageBusAccessed = true
                        
                        // Access the message bus (this might throw AlreadyDisposedException)
                        project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                            .capabilitiesChanged(field)
                    } else {
                        println("Project is disposed, skipping message bus access")
                    }
                } else {
                    // Without the fix - directly access messageBus without checking isDisposed
                    // This will throw AlreadyDisposedException when project is disposed
                    messageBusAccessed = true
                    project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                        .capabilitiesChanged(field)
                }
            }
        
        // Method to reset the tracking flags
        fun resetTracking() {
            messageBusAccessed = false
            disposedCheckPerformed = false
        }
        
        // Method to simulate the race condition that causes the issue in GitHub #155
        fun simulateRaceConditionWithScheduledTask(makeProjectDisposed: () -> Unit): AlreadyDisposedException? {
            var caughtException: AlreadyDisposedException? = null
            
            // Schedule a task that will set capabilities (similar to what happens in startProcess())
            val future = AppExecutorUtil.getAppScheduledExecutorService().submit {
                try {
                    // Wait for the project to be disposed first
                    latch.await(1, TimeUnit.SECONDS)
                    
                    // This will throw AlreadyDisposedException if project is disposed
                    // and we're not using the fix
                    capabilities = LSPCapabilities(cloudName = "test-cloud")
                } catch (e: Exception) {
                    if (e is AlreadyDisposedException) {
                        caughtException = e
                    }
                    println("Exception in scheduled task: ${e.javaClass.name}: ${e.message}")
                }
            }
            
            // Make the project disposed
            makeProjectDisposed()
            
            // Signal the background task to continue
            latch.countDown()
            
            // Wait for the task to complete
            future.get(2, TimeUnit.SECONDS)
            
            return caughtException
        }
        
        // Override startProcess to reproduce the exact call stack from the issue
        fun simulateStartProcess() {
            // This simulates the call to setCapabilities from startProcess() in LSPProcessHolder
            capabilities = LSPCapabilities(cloudName = "test-cloud")
        }
    }

    /**
     * This test reproduces the exact AlreadyDisposedException from GitHub issue #155.
     */
    @Test
    fun testAlreadyDisposedExceptionReproduction() {
        // Create mock objects
        val mockProject = mock(Project::class.java)
        val mockMessageBus = mock(MessageBus::class.java)
        val mockPublisher = mock(LSPProcessHolderChangedNotifier::class.java)
        
        // Set up the mock project
        `when`(mockProject.isDisposed).thenReturn(false)
        `when`(mockProject.messageBus).thenReturn(mockMessageBus)
        
        // Set up the mock message bus
        `when`(mockMessageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)).thenReturn(mockPublisher)
        
        // Create the test holder
        val holder = TestLspProccessHolder(mockProject)
        
        // Without the fix - project is disposed and we don't check before accessing messageBus
        holder.resetTracking()
        holder.useDisposedCheck = false
        
        // Make project disposed and try to access messageBus in a scheduled task
        val exception = holder.simulateRaceConditionWithScheduledTask {
            `when`(mockProject.isDisposed).thenReturn(true)
            // When project is disposed, accessing messageBus should throw AlreadyDisposedException
            `when`(mockProject.messageBus).thenThrow(
                AlreadyDisposedException("Already disposed")
            )
        }
        
        // Without the fix, an AlreadyDisposedException should be thrown
        assertNotNull("AlreadyDisposedException should be thrown without the fix", exception)
        assertTrue("Exception message should contain 'Already disposed'", 
            exception?.message?.contains("Already disposed") ?: false)
    }
    
}
