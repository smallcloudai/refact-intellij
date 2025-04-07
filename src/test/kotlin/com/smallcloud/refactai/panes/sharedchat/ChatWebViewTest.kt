package com.smallcloud.refactai.panes.sharedchat

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatform4TestCase
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.panes.sharedchat.Events
import org.junit.Test
import org.junit.Assert
import org.mockito.Mockito
import org.mockito.ArgumentMatchers.any
import org.mockito.MockedStatic

/**
 * Test for ChatWebView to verify it handles race conditions properly.
 * This test specifically checks that the ChatWebView can handle a situation where
 * setStyle() is called before the browser is fully initialized, and then the
 * component is disposed while JavaScript might still be executing.
 */
class ChatWebViewTest: LightPlatform4TestCase() {
    private lateinit var mockProject: Project
    private lateinit var mockEditor: Editor
    private lateinit var mockLafManager: LafManager
    private lateinit var mockTheme: UIThemeLookAndFeelInfo
    private lateinit var mockLafManagerStatic: MockedStatic<LafManager>
    
    override fun setUp() {
        super.setUp()
        mockProject = Mockito.mock(Project::class.java)
        mockEditor = Mockito.mock(Editor::class.java)
        mockLafManager = Mockito.mock(LafManager::class.java)
        mockTheme = Mockito.mock(UIThemeLookAndFeelInfo::class.java)
        
        // Mock the LafManager.getInstance() static method
        mockLafManagerStatic = Mockito.mockStatic(LafManager::class.java)
        mockLafManagerStatic.`when`<LafManager> { LafManager.getInstance() }.thenReturn(mockLafManager)
        
        // Mock the currentUIThemeLookAndFeel property
        Mockito.`when`(mockLafManager.currentUIThemeLookAndFeel).thenReturn(mockTheme)
        
        // Mock the isDark property
        Mockito.`when`(mockTheme.isDark).thenReturn(true)
        
        // Mock the necessary methods of the Editor class
        Mockito.`when`(mockEditor.project).thenReturn(mockProject)
        
        // Create a mock configuration
        val mockConfig = Mockito.mock(Events.Config.UpdatePayload::class.java)
        Mockito.`when`(mockEditor.getUserConfig()).thenReturn(mockConfig)
    }
    
    override fun tearDown() {
        // Close the static mock to prevent memory leaks
        mockLafManagerStatic.close()
        super.tearDown()
    }
    
    @Test
    fun testBrowserInitializationRaceCondition() {
        // Create a ChatWebView instance with the mocked editor
        val chatWebView = ChatWebView(mockEditor) { /* message handler */ }

        Mockito.`when`(mockLafManager.currentUIThemeLookAndFeel).thenReturn(null)

        try {
            chatWebView.setStyle()
        } catch (exception: Exception) {
            Assert.fail("Exception should not have been thrown")
        }

        // Force disposal while JavaScript might still be executing
        Thread.sleep(100) // Small delay to ensure the coroutine has started
        chatWebView.dispose()
    }
}
