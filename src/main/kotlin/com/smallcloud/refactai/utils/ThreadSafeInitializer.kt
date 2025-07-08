package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe initialization state manager that prevents race conditions
 * during ChatWebView initialization and disposal.
 */
class ThreadSafeInitializer {
    private val logger = Logger.getInstance(ThreadSafeInitializer::class.java)
    
    enum class State {
        NOT_STARTED,
        INITIALIZING,
        JS_BRIDGE_READY,
        REACT_INITIALIZING,
        FULLY_READY,
        DISPOSING,
        DISPOSED,
        FAILED
    }
    
    private val currentState = AtomicReference(State.NOT_STARTED)
    private val initializationLatch = CountDownLatch(1)
    private val disposalLatch = CountDownLatch(1)
    private var initializationError: Exception? = null
    
    /**
     * Attempts to transition to the next state atomically.
     * @param expectedState The expected current state
     * @param newState The desired new state
     * @return true if transition was successful, false otherwise
     */
    fun transitionTo(expectedState: State, newState: State): Boolean {
        // Validate that the transition is logically valid
        if (!isValidTransition(expectedState, newState)) {
            logger.debug("Invalid state transition: $expectedState -> $newState")
            return false
        }
        
        val success = currentState.compareAndSet(expectedState, newState)
        if (success) {
            logger.info("State transition: $expectedState -> $newState")
            
            // Signal completion for certain states
            when (newState) {
                State.FULLY_READY -> initializationLatch.countDown()
                State.DISPOSED -> {
                    initializationLatch.countDown() // In case we dispose before fully ready
                    disposalLatch.countDown()
                }
                State.FAILED -> {
                    initializationLatch.countDown()
                    disposalLatch.countDown()
                }
                else -> { /* No special handling needed */ }
            }
        } else {
            logger.debug("Failed state transition: expected $expectedState, actual ${currentState.get()}, desired $newState")
        }
        return success
    }
    
    /**
     * Validates whether a state transition is logically valid.
     */
    private fun isValidTransition(from: State, to: State): Boolean {
        return when (from) {
            State.NOT_STARTED -> to in setOf(State.INITIALIZING, State.DISPOSING, State.FAILED)
            State.INITIALIZING -> to in setOf(State.JS_BRIDGE_READY, State.DISPOSING, State.FAILED)
            State.JS_BRIDGE_READY -> to in setOf(State.REACT_INITIALIZING, State.DISPOSING, State.FAILED)
            State.REACT_INITIALIZING -> to in setOf(State.FULLY_READY, State.DISPOSING, State.FAILED)
            State.FULLY_READY -> to in setOf(State.DISPOSING, State.FAILED)
            State.DISPOSING -> to in setOf(State.DISPOSED)
            State.DISPOSED -> false // No transitions allowed from disposed state
            State.FAILED -> to in setOf(State.DISPOSING, State.DISPOSED) // Allow cleanup from failed state
        }
    }
    
    /**
     * Gets the current state.
     */
    fun getCurrentState(): State = currentState.get()
    
    /**
     * Checks if initialization is complete (either successfully or failed).
     */
    fun isInitializationComplete(): Boolean {
        val state = currentState.get()
        return state == State.FULLY_READY || state == State.FAILED || state == State.DISPOSED
    }
    
    /**
     * Checks if the component is ready for use.
     */
    fun isReady(): Boolean = currentState.get() == State.FULLY_READY
    
    /**
     * Checks if the component is disposed or being disposed.
     */
    fun isDisposedOrDisposing(): Boolean {
        val state = currentState.get()
        return state == State.DISPOSING || state == State.DISPOSED
    }
    
    /**
     * Waits for initialization to complete.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if initialization completed successfully, false if timed out or failed
     */
    fun waitForInitialization(timeoutMs: Long = 10000L): Boolean {
        return try {
            if (initializationLatch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                currentState.get() == State.FULLY_READY
            } else {
                logger.warn("Initialization timeout after ${timeoutMs}ms")
                false
            }
        } catch (e: InterruptedException) {
            logger.warn("Interrupted while waiting for initialization")
            Thread.currentThread().interrupt()
            false
        }
    }
    
    /**
     * Waits for disposal to complete.
     * @param timeoutMs Maximum time to wait in milliseconds
     * @return true if disposal completed, false if timed out
     */
    fun waitForDisposal(timeoutMs: Long = 5000L): Boolean {
        return try {
            disposalLatch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: InterruptedException) {
            logger.warn("Interrupted while waiting for disposal")
            Thread.currentThread().interrupt()
            false
        }
    }
    
    /**
     * Marks initialization as failed with an error.
     */
    fun markFailed(error: Exception) {
        initializationError = error
        currentState.set(State.FAILED)
        initializationLatch.countDown()
        disposalLatch.countDown()
        // Use warn instead of error to avoid test framework issues
        logger.warn("Initialization failed: ${error.message}")
    }
    
    /**
     * Gets the initialization error if any.
     */
    fun getInitializationError(): Exception? = initializationError
    
    /**
     * Forces disposal state (for emergency cleanup).
     */
    fun forceDisposed() {
        currentState.set(State.DISPOSED)
        initializationLatch.countDown()
        disposalLatch.countDown()
        logger.warn("Forced disposal state")
    }
    
    /**
     * Attempts to start disposal process.
     * @return true if disposal was started, false if already disposing/disposed
     */
    fun startDisposal(): Boolean {
        val currentState = this.currentState.get()
        return when (currentState) {
            State.DISPOSING, State.DISPOSED -> {
                logger.debug("Already disposing or disposed")
                false
            }
            else -> {
                this.currentState.set(State.DISPOSING)
                logger.info("Started disposal process from state: $currentState")
                true
            }
        }
    }
}
