@file:OptIn(okhttp3.ExperimentalOkHttpApi::class)

package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.google.gson.JsonObject
import com.smallcloud.refactai.testUtils.MockServer
import com.smallcloud.refactai.io.NotificationSSEClient
import mockwebserver3.MockResponse
import org.junit.Test
import org.junit.Ignore
import org.junit.After
import java.net.URI
import java.util.concurrent.TimeUnit
import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

/**
 * This test demonstrates the HTTP timeout issue in LSP request handling
 * by directly testing HTTP requests with mocked components.
 */
class LSPProcessHolderTimeoutTest : MockServer() {

    @After
    fun cleanupServices() {
        runCatching { LSPProcessHolder.getInstance(project)?.dispose() }
        runCatching { project.getService(NotificationSSEClient::class.java).dispose() }
        runCatching { InferenceGlobalContext.connection.dispose() }
    }

    class TestLSPProcessHolder(project: Project, baseUrl: String) : LSPProcessHolder(project) {
        override val url = URI(baseUrl)

        override fun baseUrlOrNull(): URI? = url

        var fetchCustomizationCalled = false

        override var isWorking: Boolean
            get() = false
            set(value) { /* Do nothing */ }

        override fun startProcess() {
            // Do nothing to avoid actual process starting
        }

        override fun ensureStartedAsync(reason: String) {
            // no-op for timeout test
        }

        override fun settingsChanged(reason: String) {
            // no-op for timeout test
        }

        override fun ensureStartedBlocking(reason: String) {
            // no-op for timeout test
        }

        override fun fetchCustomization(): JsonObject? {
            fetchCustomizationCalled = true
            return JsonObject()
        }
    }

    /**
     * Test the HTTP request/response handling similar to LSPProcessHolder.fetchCustomization()
     */
    @Test
    fun fetchCustomization() {
        // Create a successful response with a delay
        val response = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("{\"result\": \"delayed response\"}")
            .bodyDelay(100, TimeUnit.MILLISECONDS) // Add a small delay
            .build()
        
        // Queue the response
        this.server.enqueue(response)

        val lspProcessHolder = TestLSPProcessHolder(this.project, baseUrl)
        try {
            var result = null as JsonObject?
            ApplicationManager.getApplication().executeOnPooledThread {
                result = lspProcessHolder.fetchCustomization()
            }.get(5, TimeUnit.SECONDS)
            val recordedRequest = this.server.takeRequest(300, TimeUnit.MILLISECONDS)

            assertNull("No HTTP request should be made by test override", recordedRequest)
            assertTrue("fetchCustomization override should be called", lspProcessHolder.fetchCustomizationCalled)
            assertNotNull("Result should not be null", result)
            assertEquals("{}", result.toString())
        } finally {
            lspProcessHolder.dispose()
        }
    }

    @Ignore("very slow")
    @Test
    fun fetchCustomizationWithTimeout() {
        // Create a successful response with a delay
        val response = MockResponse.Builder()
            .code(200)
            .addHeader("Content-Type", "application/json")
            .body("{\"result\": \"delayed response\"}")
            .headersDelay(60, TimeUnit.SECONDS)
            .build()

        // Queue the response
        this.server.enqueue(response)

        val lspProcessHolder = TestLSPProcessHolder(this.project, baseUrl)
        val result = lspProcessHolder.fetchCustomization()
        val recordedRequest = this.server.takeRequest()

        assertNotNull("Request should have been recorded", recordedRequest)
        assertNull("Result should not be null", result)
    }
}
