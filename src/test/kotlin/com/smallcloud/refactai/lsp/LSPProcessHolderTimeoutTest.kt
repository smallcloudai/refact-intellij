@file:OptIn(okhttp3.ExperimentalOkHttpApi::class)

package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.smallcloud.refactai.testUtils.MockServer
import mockwebserver3.MockResponse
import org.junit.Test
import org.junit.Ignore
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * This test demonstrates the HTTP timeout issue in LSP request handling
 * by directly testing HTTP requests with mocked components.
 */
class LSPProcessHolderTimeoutTest : MockServer() {

    class TestLSPProcessHolder(project: Project, baseUrl: String) : LSPProcessHolder(project) {
        override val url = URI(baseUrl)

        override var isWorking: Boolean
            get() = true
            set(value) { /* Do nothing */ }

        override fun startProcess() {
            // Do nothing to avoid actual process starting
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
        val result = lspProcessHolder.fetchCustomization()
        val recordedRequest = this.server.takeRequest(5, TimeUnit.SECONDS)

        assertNotNull("Request should have been recorded", recordedRequest)
        assertNotNull("Result should not be null", result)
        assertEquals("{\"result\":\"delayed response\"}", result.toString())
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
