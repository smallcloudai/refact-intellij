package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusFactory
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.net.URI
import java.net.http.HttpClient
import java.time.Duration
import java.util.concurrent.TimeUnit
import org.mockito.Mockito.`when`
import org.mockito.Mockito

/**
 * This test demonstrates the HTTP timeout issue in LSPProcessHolder.fetchCustomization()
 * by directly testing the method with mocked components.
 */
class LSPProcessHolderTimeoutTest : LightPlatformTestCase() {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var baseUrl: String

    override fun setUp() {
        super.setUp()
        mockWebServer = MockWebServer()
        mockWebServer.protocolNegotiationEnabled = true
        // println("protocals: ${mockWebServer.protocols}")
        mockWebServer.start()
        baseUrl = mockWebServer.url("/").toString()
    }

    override fun tearDown() {
         //  the mock server after each test
         mockWebServer.shutdown()
        super.tearDown()
    }

    /**
     * A test subclass of LSPProcessHolder that allows us to test the fetchCustomization method
     */
    class TestLSPProcessHolder(project: Project, baseUrl: String) : LSPProcessHolder(project) {
        // Override the url property to return a test URL
        override val url = URI(baseUrl)

        // Override isWorking to always return true for testing
        override var isWorking: Boolean
            get() = true
            set(value) { /* Do nothing */ }
            
        // Override other methods that might cause issues in testing
        override fun startProcess() {
            // Do nothing to avoid actual process starting
        }
    }
    
    fun testFetchCustomization() {
        // Create a successful response
        val response = MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody("{\"result\": \"delayed response\"}")
        
        // Queue the response
        mockWebServer.enqueue(response)
        
        // Create the test LSP process holder
        val lspProcessHolder = TestLSPProcessHolder(this.project, baseUrl)
        
        // Call fetchCustomization and verify it returns the expected result
        val result = lspProcessHolder.fetchCustomization()

        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        // Verify the result is not null and contains the expected data
        assertNotNull("Result should not be null", result)
        assertEquals("{\"result\":\"delayed response\"}", result.toString())
    }
}
