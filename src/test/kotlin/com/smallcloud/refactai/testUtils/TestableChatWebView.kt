package com.smallcloud.refactai.testUtils

import com.intellij.openapi.Disposable
import com.smallcloud.refactai.panes.sharedchat.Events
import com.smallcloud.refactai.utils.ThreadSafeInitializer
import com.smallcloud.refactai.utils.BrowserStateManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Testable version of ChatWebView that works without JCEF initialization.
 * Used for unit testing the core ChatWebView functionality.
 */
class TestableChatWebView(
    private val editor: TestableEditor,
    private val messageHandler: (event: Events.FromChat) -> Unit
) : Disposable {
    
    // Use the same thread-safe initializer as the real implementation
    private val initializer = ThreadSafeInitializer()
    private val _isDisposed = AtomicBoolean(false)
    
    // Mock browser identifier for state management integration
    // We'll use a simple object that can be used as a key in the BrowserStateManager
    private val mockBrowserId = "test-browser-${hashCode()}"
    
    // Mock component instead of real browser
    private val mockComponent = JPanel()
    private val componentValid = AtomicBoolean(true)
    
    // Test tracking properties
    var messageCount = AtomicInteger(0)
    var styleUpdateCount = AtomicInteger(0)
    private val jsExecutionActive = AtomicBoolean(false)
    
    // Public properties for tests
    val isDisposed: Boolean get() = _isDisposed.get()
    
    init {
        // For testing, we'll simulate browser state management without actual CEF integration
        
        // Simulate initialization in background thread
        Thread {
            try {
                // Simulate the same initialization sequence as real ChatWebView
                initializer.transitionTo(ThreadSafeInitializer.State.NOT_STARTED, 
                                       ThreadSafeInitializer.State.INITIALIZING)
                Thread.sleep(50) // Simulate DOM loading
                
                initializer.transitionTo(ThreadSafeInitializer.State.INITIALIZING, 
                                       ThreadSafeInitializer.State.JS_BRIDGE_READY)
                Thread.sleep(50) // Simulate JS bridge setup
                
                initializer.transitionTo(ThreadSafeInitializer.State.JS_BRIDGE_READY, 
                                       ThreadSafeInitializer.State.REACT_INITIALIZING)
                Thread.sleep(50) // Simulate React initialization
                
                initializer.transitionTo(ThreadSafeInitializer.State.REACT_INITIALIZING, 
                                       ThreadSafeInitializer.State.FULLY_READY)
                
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                initializer.markFailed(e)
            } catch (e: Exception) {
                initializer.markFailed(e)
            }
        }.start()
    }
    
    fun getComponent(): JComponent {
        // Return the same component instance to maintain consistency
        return mockComponent
    }
    
    // Add method to check component validity for tests
    fun isComponentValid(): Boolean {
        return componentValid.get() && !_isDisposed.get()
    }
    
    fun postMessage(message: Events.ToChat<*>?) {
        if (initializer.isDisposedOrDisposing()) {
            throw IllegalStateException("ChatWebView is disposed")
        }
        
        // For testing, we'll simulate browser safety checks
        if (jsExecutionActive.get()) {
            throw IllegalStateException("Browser is not safe for operations")
        }
        
        // Simulate message posting
        if (message != null) {
            // In a real implementation, this would send to browser
            // For testing, we just validate the message format
            Events.stringify(message)
            messageCount.incrementAndGet()
        }
    }
    
    fun postMessage(message: String) {
        if (initializer.isDisposedOrDisposing()) {
            throw IllegalStateException("ChatWebView is disposed")
        }
        
        // For testing, we'll simulate browser safety checks
        if (jsExecutionActive.get()) {
            throw IllegalStateException("Browser is not safe for operations")
        }
        
        // Simulate string message posting
        // For testing, we just validate it's not empty
        if (message.isBlank()) {
            throw IllegalArgumentException("Message cannot be blank")
        }
        messageCount.incrementAndGet()
    }
    
    fun setStyle() {
        if (initializer.isDisposedOrDisposing()) {
            return // Silently ignore like the real implementation
        }
        
        // Simulate style setting
        // In real implementation, this would update browser theme
        styleUpdateCount.incrementAndGet()
    }
    
    // Test utility methods
    fun waitForInitialization(timeoutMs: Long = 5000): Boolean {
        return initializer.waitForInitialization(timeoutMs)
    }
    
    fun waitForDisposal(timeoutMs: Long = 5000): Boolean {
        return initializer.waitForDisposal(timeoutMs)
    }
    
    fun isInitialized(): Boolean = initializer.isReady()
    
    // Test-specific methods for simulating browser state
    fun simulateJavaScriptExecution(active: Boolean) {
        jsExecutionActive.set(active)
    }
    
    fun isJavaScriptExecuting(): Boolean = jsExecutionActive.get()
    

    
    // Simulate receiving a message from browser (for testing)
    fun simulateMessageFromBrowser(message: String) {
        if (initializer.isDisposedOrDisposing()) return
        
        try {
            val event = Events.parse(message)
            if (event != null) {
                messageHandler(event)
            }
        } catch (e: Exception) {
            // Ignore parsing errors in tests
        }
    }
    
    override fun dispose() {
        // Use the same disposal logic as the real implementation
        if (!initializer.startDisposal()) {
            return // Already disposing or disposed
        }
        
        if (_isDisposed.compareAndSet(false, true)) {
            // Mark component as invalid
            componentValid.set(false)
            
            // Simulate disposal cleanup
            Thread {
                try {
                    Thread.sleep(50) // Simulate cleanup time
                    
                    // Mark disposal complete
                    initializer.transitionTo(ThreadSafeInitializer.State.DISPOSING, 
                                           ThreadSafeInitializer.State.DISPOSED)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    initializer.forceDisposed()
                } catch (e: Exception) {
                    initializer.forceDisposed()
                }
            }.start()
        }
    }
}
