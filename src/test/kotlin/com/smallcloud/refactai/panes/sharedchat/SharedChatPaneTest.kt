package com.smallcloud.refactai.panes.sharedchat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Test for SharedChatPane to verify the deadlock issue with file system refresh under read lock
 */
class SharedChatPaneTest : BasePlatformTestCase() {
    private val logger = Logger.getInstance(SharedChatPaneTest::class.java)

    /**
     * Test that recreates the issue reported in GitHub issue #192
     * 
     * This test simulates the problematic code path in SharedChatPane.showPatch() method
     * where refreshAndFindFileByPath is called while holding a read lock.
     * 
     * The test should fail with an error about performing synchronous refresh under read lock.
     */
    @Test
    fun testDeadlockInShowPatch() {
        // Create a temporary directory and file for testing
        val tempDir = Files.createTempDirectory("refact-test")
        val tempFile = tempDir.resolve("test-file.txt").toFile()
        tempFile.writeText("Test content")
        val filePath = tempFile.absolutePath
        
        // Create a latch to wait for the operation to complete
        val latch = CountDownLatch(1)
        val exceptionRef = AtomicReference<Throwable>()
        
        // Execute the test in a background thread to simulate what happens in the actual code
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // Simulate the problematic code in SharedChatPane.showPatch
                // This is where the deadlock can occur - calling refreshAndFindFileByPath under read lock
                ReadAction.run<Throwable> {
                    logger.info("About to call refreshAndFindFileByPath under read lock")
                    
                    // Force a refresh to ensure we're testing the actual refresh behavior
                    // This should throw an exception about performing synchronous refresh under read lock
                    LocalFileSystem.getInstance().refresh(false)
                    
                    // This is the problematic call that can cause deadlocks
                    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
                    
                    logger.info("Called refreshAndFindFileByPath under read lock, file: $file")
                }
            } catch (e: Throwable) {
                logger.info("Caught exception: ${e.message}")
                exceptionRef.set(e)
            } finally {
                latch.countDown()
            }
        }
        
        // Wait for the operation to complete
        assertTrue("Test timed out", latch.await(5, TimeUnit.SECONDS))
        
        // We must get an exception about synchronous refresh under read lock
        val exception = exceptionRef.get()
        assertNotNull("Should have received an exception", exception)
        assertTrue(
            "Exception should be about synchronous refresh under read lock",
            exception.message?.contains("Do not perform a synchronous refresh under read lock") == true
        )
        
        // Clean up
        tempFile.delete()
        tempDir.toFile().delete()
    }

}
