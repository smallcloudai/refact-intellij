package com.smallcloud.refactai.modes

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.CaretModel
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Test for ModeProvider to verify EDT thread safety
 */
class ModeProviderTest : BasePlatformTestCase() {

    /**
     * Test that verifies the EDT assertion error when calling onTextChange from a background thread
     */
    fun testEdtAssertionErrorInOnTextChange() {
        // Create mock objects
        val document = mock(Document::class.java)
        val caretModel = mock(CaretModel::class.java)
        val caret = mock(Caret::class.java)
        val project = mock(Project::class.java)
        val editor = mock(Editor::class.java)
        
        // Configure mocks
        `when`(editor.document).thenReturn(document)
        `when`(editor.caretModel).thenReturn(caretModel)
        `when`(caretModel.currentCaret).thenReturn(caret)
        `when`(editor.project).thenReturn(project)
        
        // Create a latch to wait for the background thread to complete
        val latch = CountDownLatch(1)
        val exceptionRef = AtomicReference<Throwable>()
        
        // Create a custom mode that will trigger the EDT assertion
        val customMode = object : Mode {
            override var needToRender: Boolean = true
            
            override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {}
            
            override fun onTextChange(event: DocumentEventExtra) {
                try {
                    // This will throw an exception if we're not on EDT
                    if (!ApplicationManager.getApplication().isDispatchThread) {
                        throw AssertionError("Not on EDT thread")
                    }
                } catch (e: Throwable) {
                    exceptionRef.set(e)
                } finally {
                    latch.countDown()
                }
            }
            
            override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: com.intellij.openapi.actionSystem.DataContext) {}
            
            override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: com.intellij.openapi.actionSystem.DataContext) {}
            
            override fun onCaretChange(event: com.intellij.openapi.editor.event.CaretEvent) {}
            
            override fun isInActiveState(): Boolean = true
            
            override fun show() {}
            
            override fun hide() {}
            
            override fun cleanup(editor: Editor) {}
        }
        
        // Create a ModeProvider with our custom mode
        val modeProvider = ModeProvider(editor, mutableMapOf(ModeType.Completion to customMode), customMode)
        
        // Call onTextChange from a background thread
        AppExecutorUtil.getAppScheduledExecutorService().submit {
            // Create a simple document event (we don't need a real one for this test)
            val documentEvent = null // We'll use null since we're just testing thread safety
            modeProvider.onTextChange(documentEvent, editor, false)
        }
        
        // Wait for the background thread to complete
        assertTrue("Test timed out", latch.await(2, TimeUnit.SECONDS))
        
        // Verify that we got an exception about not being on EDT
        assertNotNull("Should have received an exception", exceptionRef.get())
        assertTrue("Exception should be about EDT", 
            exceptionRef.get().message?.contains("EDT") == true || 
            exceptionRef.get().message?.contains("Dispatch") == true)
    }
}
