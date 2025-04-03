package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * Basic demonstration of MockWebServer for network mocking in tests.
 * This example uses Java's HttpClient instead of project-specific classes
 * to show the core concepts of MockWebServer.
 */
class MockWebServerDemoTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var httpClient: HttpClient
    private lateinit var baseUrl: String

    @Before
    fun setup() {
        // Start the mock server
        mockWebServer = MockWebServer()
        mockWebServer.start()
        
        // Get the base URL of the mock server
        baseUrl = mockWebServer.url("/").toString()
        
        // Create a standard HttpClient
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build()
    }

    @After
    fun tearDown() {
        // Shutdown the mock server after each test
        mockWebServer.shutdown()
    }

    @Test
    fun testBasicGetRequest() {
        // Prepare a mock response
        val responseBody = """{"status":"success","data":"test data"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        // Create and send a GET request
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "api/test"))
            .header("Accept", "application/json")
            .GET()
            .build()
            
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        // Verify the request was made correctly
        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals("/api/test", recordedRequest.path)
        
        // Verify the response
        assertEquals(200, response.statusCode())
        assertEquals(responseBody, response.body())
        
        // Parse the JSON to verify the content
        val gson = Gson()
        val jsonObject = gson.fromJson(response.body(), JsonObject::class.java)
        assertEquals("success", jsonObject.get("status").asString)
        assertEquals("test data", jsonObject.get("data").asString)
    }

    @Test
    fun testBasicPostRequest() {
        // Prepare a mock response
        val responseBody = """{"status":"created","id":"12345"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        // Request body to send
        val requestBody = """{"name":"test","value":"data"}"""
        
        // Create and send a POST request
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "api/create"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer test-token")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
            
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        // Verify the request was made correctly
        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("POST", recordedRequest!!.method)
        assertEquals("/api/create", recordedRequest.path)
        assertEquals("Bearer test-token", recordedRequest.getHeader("Authorization"))
        assertEquals(requestBody, recordedRequest.body.readUtf8())
        
        // Verify the response
        assertEquals(201, response.statusCode())
        assertEquals(responseBody, response.body())
    }

    @Test
    fun testErrorResponse() {
        // Prepare an error response
        val errorBody = """{"error":"invalid_request","message":"Bad request"}"""
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setHeader("Content-Type", "application/json")
                .setBody(errorBody)
        )

        // Create and send a GET request
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "api/error"))
            .GET()
            .build()
            
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        // Verify the request was made
        val recordedRequest = mockWebServer.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals("/api/error", recordedRequest.path)
        
        // Verify the error response
        assertEquals(400, response.statusCode())
        assertEquals(errorBody, response.body())
        
        // Parse the error JSON
        val gson = Gson()
        val jsonObject = gson.fromJson(response.body(), JsonObject::class.java)
        assertEquals("invalid_request", jsonObject.get("error").asString)
        assertEquals("Bad request", jsonObject.get("message").asString)
    }
}
