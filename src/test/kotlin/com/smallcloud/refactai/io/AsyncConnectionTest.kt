package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.testUtils.MockServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Test
import java.net.URI
import java.util.concurrent.TimeUnit



class AsyncConnectionTest: MockServer() {

    @Test
    fun testBasicGetRequest() {
        val httpClient = AsyncConnection()
        // Prepare a mock response
        val responseBody = """{"status":"success","data":"test data"}"""
        this.server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(responseBody)
        )

        val response = httpClient.get(URI.create(this.baseUrl + "api/test")).join().get().toString()

        // Verify the request was made correctly
        val recordedRequest = this.server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(recordedRequest)
        assertEquals("GET", recordedRequest!!.method)
        assertEquals("/api/test", recordedRequest.path)
        
        // Verify the response
        assertEquals(responseBody, response)
        
        // Parse the JSON to verify the content
        val gson = Gson()
        val jsonObject = gson.fromJson(response, JsonObject::class.java)
        assertEquals("success", jsonObject.get("status").asString)
        assertEquals("test data", jsonObject.get("data").asString)
    }

}
