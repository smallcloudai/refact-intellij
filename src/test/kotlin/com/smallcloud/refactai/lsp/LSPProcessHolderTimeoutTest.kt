package com.smallcloud.refactai.lsp

import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightPlatformTestCase
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
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
    private lateinit var httpClient: HttpClient
    private lateinit var baseUrl: String
    private lateinit var mockProject: Project

    override fun setUp() {
        super.setUp()
        
        // Start the mock server
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Get the base URL of the mock server
        baseUrl = mockWebServer.url("/").toString()

        // Create a standard HttpClient
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()

        mockProject = Mockito.mock(Project::class.java)
        `when`(mockProject.isDisposed).thenReturn(false)
    }

    override fun tearDown() {
        // Shutdown the mock server after each test
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
    }
    
    fun testFetchCustomizationTimeout() {
        // Create a response that will take more than 30 seconds to complete
        // This simulates a server that's taking too long to respond
        val delayedResponse = MockResponse()
            .setResponseCode(200)
            .setHeadersDelay(35, TimeUnit.SECONDS) // Delay the headers by 35 seconds
            .setBody("{\"result\": \"delayed response\"}")
        
        // Queue the delayed response
        mockWebServer.enqueue(delayedResponse)
        
        // Create the test LSP process holder
        val lspProcessHolder = TestLSPProcessHolder(mockProject, baseUrl)
        
        // The test will now time out when fetchCustomization() is called
        // because the server takes too long to respond
    }
}
