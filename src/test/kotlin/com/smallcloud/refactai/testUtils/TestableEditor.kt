package com.smallcloud.refactai.testUtils

import com.intellij.openapi.project.Project
import com.smallcloud.refactai.panes.sharedchat.Events
import java.util.concurrent.CompletableFuture

/**
 * Testable version of Editor that mimics the real Editor API.
 * Used for unit testing ChatWebView functionality.
 */
class TestableEditor(val project: Project) {
    
    private var mockUserConfig = Events.Config.UpdatePayload(
        features = Events.Config.Features(
            ast = true,
            vecdb = true,
            knowledge = false
        ),
        themeProps = Events.Config.ThemeProps("dark"),
        lspPort = 8001,
        apiKey = "test-key",
        addressURL = "http://test.com",
        keyBindings = Events.Config.KeyBindings("Ctrl+Space")
    )
    
    private var mockActiveFile = Events.ActiveFile.FileInfo(
        name = "test.kt",
        path = "/test/test.kt",
        canPaste = true,
        cursor = 0,
        line1 = 1,
        line2 = 5,
        content = "// Test file content"
    )
    
    private var mockSelectedSnippet: Events.Editor.Snippet? = Events.Editor.Snippet(
        language = "kotlin",
        code = "test content",
        path = "/test/test.kt",
        basename = "test.kt"
    )
    
    fun getUserConfig(): Events.Config.UpdatePayload {
        return mockUserConfig
    }
    
    fun getActiveFileInfo(callback: (Events.ActiveFile.FileInfo) -> Unit) {
        // Simulate async behavior
        CompletableFuture.runAsync {
            Thread.sleep(10) // Small delay to simulate real async behavior
            callback(mockActiveFile)
        }
    }
    
    fun getSelectedSnippet(callback: (Events.Editor.Snippet?) -> Unit) {
        // Simulate async behavior
        CompletableFuture.runAsync {
            Thread.sleep(10) // Small delay to simulate real async behavior
            callback(mockSelectedSnippet)
        }
    }
    
    // Test utility methods
    fun setMockUserConfig(config: Events.Config.UpdatePayload) {
        mockUserConfig = config
    }
    
    fun setMockActiveFile(file: Events.ActiveFile.FileInfo) {
        mockActiveFile = file
    }
    
    fun setMockSelectedSnippet(snippet: Events.Editor.Snippet?) {
        mockSelectedSnippet = snippet
    }
}
