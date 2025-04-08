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
 * Test for SharedChatPane to verify the fix for the deadlock issue with file system refresh under read lock
 */
class SharedChatPaneTest : BasePlatformTestCase() {
    private val logger = Logger.getInstance(SharedChatPaneTest::class.java)
    private lateinit var tempDir: File
    private lateinit var tempFile: File
    
    override fun setUp() {
        super.setUp()
        // Create a temporary directory and file for testing
        tempDir = createTempDirectory()
        tempFile = File(tempDir, "test-file.txt")
        tempFile.writeText("Test content")
    }
    
    override fun tearDown() {
        try {
            // Clean up
            tempFile.delete()
            tempDir.delete()
        } finally {
            super.tearDown()
        }
    }
    
    private fun createTempDirectory(): File {
        val tempDir = Files.createTempDirectory("refact-test").toFile()
        tempDir.deleteOnExit()
        return tempDir
    }

    /**
     * Test that verifies the fixed showPatch method in SharedChatPane
     * 
     * This test confirms that the method now properly separates the file refresh operation
     * from the file lookup, avoiding the deadlock issue reported in GitHub issue #192.
     */
    @Test
    fun testFixedShowPatchMethod() {
        val filePath = tempFile.absolutePath
        
        // Create a SharedChatPane instance
        val sharedChatPane = SharedChatPane(project)
        
        // Create a latch to wait for the operation to complete
        val latch = CountDownLatch(1)
        val exceptionRef = AtomicReference<Throwable>()
        val fileRef = AtomicReference<VirtualFile>()
        
        // Execute the test in a background thread
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // We need to wrap this in a ReadAction to simulate the conditions
                // under which the error used to occur
                ReadAction.run<Throwable> {
                    // Call the fixed showPatch method
                    // This should no longer throw an exception about performing synchronous refresh under read lock
                    sharedChatPane.showPatch(
                        filePath,
                        "Test content",
                        null,
                        null
                    )
                    
                    // Verify that the file can be found after the refresh
                    val file = LocalFileSystem.getInstance().findFileByPath(filePath)
                    fileRef.set(file)
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
        
        // Verify that we didn't get an exception
        assertNull("Should not have received an exception: ${exceptionRef.get()?.message}", exceptionRef.get())
        
        // Verify that the file was found
        assertNotNull("File should be found", fileRef.get())
    }
}
