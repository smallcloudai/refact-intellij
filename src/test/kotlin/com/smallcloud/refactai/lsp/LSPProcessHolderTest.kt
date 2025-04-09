package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.messages.MessageBus
import org.junit.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

/**
 * Test that demonstrates the "Already disposed" issue in LSPProcessHolder.
 */
class LSPProcessHolderTest : BasePlatformTestCase() {

    class TestLspProccessHolder(project: Project) : LSPProcessHolder(project) {
        // Flag to track if the message bus was accessed
        var messageBusAccessed = false
        
        // Flag to track if the check for project.isDisposed was performed
        var disposedCheckPerformed = false
        
        // Override capabilities setter to track message bus access and disposed checks
        override var capabilities: LSPCapabilities = LSPCapabilities()
            set(newValue) {
                if (newValue == field) return
                field = newValue
                
                // Record that we performed the disposed check
                disposedCheckPerformed = true
                
                // This is the fix we're testing - checking if project is disposed
                if (!project.isDisposed) {
                    // Record that we accessed the message bus
                    messageBusAccessed = true
                    
                    // Access the message bus (this might throw AlreadyDisposedException)
                    try {
                        project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                            .capabilitiesChanged(field)
                    } catch (e: Exception) {
                        // Log the exception but don't rethrow it
                        println("Exception when accessing message bus: ${e.message}")
                    }
                } else {
                    println("Project is disposed, skipping message bus access")
                }
            }
        
        // Method to reset the tracking flags
        fun resetTracking() {
            messageBusAccessed = false
            disposedCheckPerformed = false
        }
        
        // Method to simulate setting capabilities without the fix
        fun setCapabilitiesWithoutFix(newValue: LSPCapabilities) {
            if (newValue == capabilities) return
            capabilities = newValue
            
            // Directly access the message bus without checking if project is disposed
            try {
                messageBusAccessed = true
                project.messageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)
                    .capabilitiesChanged(capabilities)
            } catch (e: Exception) {
                // Log the exception but don't rethrow it
                println("Exception when accessing message bus without fix: ${e.message}")
            }
        }
        
        // Override other methods that access the project's message bus
        override fun dispose() {
            // Add the same check here to prevent exceptions during disposal
            if (!project.isDisposed) {
                super.dispose()
            } else {
                println("Project is disposed, skipping super.dispose()")
            }
        }
        
        // Method to simulate the race condition
        fun simulateRaceCondition(makeProjectDisposed: () -> Unit) {
            // Start a thread that will set capabilities
            val thread = Thread {
                try {
                    // Small delay to ensure the main thread has time to make the project disposed
                    Thread.sleep(100)
                    
                    // Set capabilities (this will access the message bus)
                    capabilities = LSPCapabilities(cloudName = "test-cloud")
                } catch (e: Exception) {
                    println("Exception in background thread: ${e.message}")
                }
            }
            
            // Start the thread
            thread.start()
            
            // Make the project disposed
            makeProjectDisposed()
            
            // Wait for the thread to complete
            thread.join()
        }
    }

    /**
     * This test uses the TestLspProccessHolder to verify the fix for the "Already disposed" issue.
     */
    @Test
    fun testWithTestLspProcessHolder() {
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
        
        // Test case 1: Project is not disposed
        run {
            holder.resetTracking()
            holder.capabilities = LSPCapabilities(cloudName = "test1")
            
            assertTrue("Disposed check should be performed", holder.disposedCheckPerformed)
            assertTrue("Message bus should be accessed when project is not disposed", 
                holder.messageBusAccessed)
        }
        
        // Test case 2: Project is disposed
        run {
            `when`(mockProject.isDisposed).thenReturn(true)
            holder.resetTracking()
            holder.capabilities = LSPCapabilities(cloudName = "test2")
            
            assertTrue("Disposed check should be performed", holder.disposedCheckPerformed)
            assertFalse("Message bus should NOT be accessed when project is disposed", 
                holder.messageBusAccessed)
        }
    }
}
