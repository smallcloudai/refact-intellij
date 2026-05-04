package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Test that demonstrates the "Already disposed" issue in LSPProcessHolder.
 * This reproduces the specific AlreadyDisposedException from GitHub issue #155.
 */
class LSPProcessHolderTest : BasePlatformTestCase() {

    class TestLspProccessHolder(project: Project) : LSPProcessHolder(project) {

        // Latch to control test execution flow
        private val latch = CountDownLatch(1)

        // Method to simulate the race condition that causes the issue in GitHub #155
        fun simulateRaceConditionWithScheduledTask(makeProjectDisposed: () -> Unit): AlreadyDisposedException? {
            var caughtException: AlreadyDisposedException? = null
            
            // Schedule a task that will set capabilities (similar to what happens in startProcess())
            val future = AppExecutorUtil.getAppScheduledExecutorService().submit {
                try {
                    latch.await(1, TimeUnit.SECONDS)
                    capabilities = LSPCapabilities(cloudName = "test-cloud")
                } catch (e: Exception) {
                    if (e is AlreadyDisposedException) {
                        caughtException = e
                    }
                    println("Exception in scheduled task: ${e.javaClass.name}: ${e.message}")
                }
            }

            makeProjectDisposed()
            latch.countDown()
            future.get(2, TimeUnit.SECONDS)
            
            return caughtException
        }
        
        // Override startProcess to reproduce the exact call stack from the issue
        fun simulateStartProcess() {
            // This simulates the call to setCapabilities from startProcess() in LSPProcessHolder
            capabilities = LSPCapabilities(cloudName = "test-cloud")
        }

        val startProcessCalls = AtomicInteger(0)
        private val startProcessEntered = CountDownLatch(1)
        private val releaseStartProcess = CountDownLatch(1)

        override fun startProcess() {
            startProcessCalls.incrementAndGet()
            startProcessEntered.countDown()
            releaseStartProcess.await(2, TimeUnit.SECONDS)
            isWorking = true
            val field = LSPProcessHolder::class.java.getDeclaredField("lastConfig")
            field.isAccessible = true
            field.set(this, LSPConfig(port = 1))
        }

        fun waitUntilStartProcessEntered(): Boolean = startProcessEntered.await(2, TimeUnit.SECONDS)

        fun releaseStartProcess() {
            releaseStartProcess.countDown()
        }

        override fun ensureStartedAsync(reason: String) {
            // no-op for unit test
        }

        override fun settingsChanged(reason: String) {
            // no-op for unit test
        }

        fun ensureStartedBlockingForTest(reason: String) {
            ensureStartedBlocking(reason)
        }
    }

    @Test
    fun testAlreadyDisposedException() {
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

        
        // Make project disposed and try to access messageBus in a scheduled task
        val exception = holder.simulateRaceConditionWithScheduledTask {
            `when`(mockProject.isDisposed).thenReturn(true)
            // When project is disposed, accessing messageBus should throw AlreadyDisposedException
            // But with the fix, we never access messageBus when project is disposed
            `when`(mockProject.messageBus).thenThrow(
                AlreadyDisposedException("Already disposed")
            )
        }
        
        // With the fix, no exception should be thrown
        assertNull("With the fix, no AlreadyDisposedException should be thrown", exception)
        // Verify that the capabilities were still set correctly
        assertEquals("test-cloud", holder.capabilities.cloudName)
    }

    @Test
    fun testConcurrentBlockingEnsureStartedOnlyStartsOneProcess() {
        val mockProject = mock(Project::class.java)
        val mockMessageBus = mock(MessageBus::class.java)
        val mockPublisher = mock(LSPProcessHolderChangedNotifier::class.java)

        `when`(mockProject.isDisposed).thenReturn(false)
        `when`(mockProject.messageBus).thenReturn(mockMessageBus)
        `when`(mockMessageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)).thenReturn(mockPublisher)

        val holder = TestLspProccessHolder(mockProject)
        val executor = Executors.newFixedThreadPool(3)
        try {
            val futures = (1..3).map {
                executor.submit {
                    holder.ensureStartedBlockingForTest("concurrent-$it")
                }
            }

            assertTrue(holder.waitUntilStartProcessEntered())
            holder.releaseStartProcess()
            futures.forEach { it.get(3, TimeUnit.SECONDS) }

            assertEquals(1, holder.startProcessCalls.get())
        } finally {
            executor.shutdownNow()
            holder.dispose()
        }
    }

    @Test
    fun testPendingLifecycleFlagDefaultsFalse() {
        val mockProject = mock(Project::class.java)
        val mockMessageBus = mock(MessageBus::class.java)
        val mockPublisher = mock(LSPProcessHolderChangedNotifier::class.java)

        `when`(mockProject.isDisposed).thenReturn(false)
        `when`(mockProject.messageBus).thenReturn(mockMessageBus)
        `when`(mockMessageBus.syncPublisher(LSPProcessHolderChangedNotifier.TOPIC)).thenReturn(mockPublisher)

        val holder = TestLspProccessHolder(mockProject)
        assertFalse(holder.hasPendingLifecycleWork())
    }

}
