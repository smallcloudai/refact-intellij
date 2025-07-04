package com.smallcloud.refactai.testUtils

import com.intellij.openapi.Disposable
import com.smallcloud.refactai.panes.sharedchat.Events
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
    
    private val initializationLatch = CountDownLatch(1)
    private val disposalLatch = CountDownLatch(1)
    private val _isDisposed = AtomicBoolean(false)
    private val isInitialized = AtomicBoolean(false)
    
    // Mock component instead of real browser
    private val mockComponent = JPanel()
    private val componentValid = AtomicBoolean(true)
    
    // Test tracking properties
    var messageCount = AtomicInteger(0)
    var styleUpdateCount = AtomicInteger(0)
    
    // Public properties for tests
    val isDisposed: Boolean get() = _isDisposed.get()
    
    init {
        // Simulate initialization in background thread
        Thread {
            try {
                Thread.sleep(100) // Simulate initialization time
                isInitialized.set(true)
                initializationLatch.countDown()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
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
        if (_isDisposed.get()) {
            throw IllegalStateException("ChatWebView is disposed")
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
        if (_isDisposed.get()) {
            throw IllegalStateException("ChatWebView is disposed")
        }
        
        // Simulate string message posting
        // For testing, we just validate it's not empty
        if (message.isBlank()) {
            throw IllegalArgumentException("Message cannot be blank")
        }
        messageCount.incrementAndGet()
    }
    
    fun setStyle() {
        if (_isDisposed.get()) {
            throw IllegalStateException("ChatWebView is disposed")
        }
        
        // Simulate style setting
        // In real implementation, this would update browser theme
        styleUpdateCount.incrementAndGet()
    }
    
    // Test utility methods
    fun waitForInitialization(timeoutMs: Long = 5000): Boolean {
        return try {
            initializationLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
    
    fun waitForDisposal(timeoutMs: Long = 5000): Boolean {
        return try {
            disposalLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            false
        }
    }
    
    fun isInitialized(): Boolean = isInitialized.get()
    

    
    // Simulate receiving a message from browser (for testing)
    fun simulateMessageFromBrowser(message: String) {
        if (_isDisposed.get()) return
        
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
        if (_isDisposed.compareAndSet(false, true)) {
            // Mark component as invalid
            componentValid.set(false)
            
            // Simulate disposal cleanup
            Thread {
                try {
                    Thread.sleep(50) // Simulate cleanup time
                    disposalLatch.countDown()
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    disposalLatch.countDown()
                }
            }.start()
        }
    }
}
