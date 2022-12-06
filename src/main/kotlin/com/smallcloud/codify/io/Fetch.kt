package com.smallcloud.codify.io

//import com.intellij.util.io.HttpsURLConnection
//import kotlinx.serialization.*
import com.google.gson.Gson
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequest
import java.net.HttpURLConnection
import java.net.URL
data class Response(val statusCode: Int, val headers: Map<String, List<String>>? = null, val body: String? = null)


fun sendRequest(url: String, method: String = "GET", headers: Map<String, String>? = null, body: String? = null): Response {
    val conn = URL(url).openConnection() as HttpURLConnection

    with(conn) {
        requestMethod = method
        doOutput = body != null
        headers?.forEach(this::setRequestProperty)
    }

    if (body != null) {
        conn.outputStream.use {
            it.write(body.toByteArray())
        }
    }

    val responseBody = conn.inputStream.use { it.readBytes() }.toString(Charsets.UTF_8)

    return Response(conn.responseCode, conn.headerFields, responseBody)
}

fun fetch(req: SMCRequest) : SMCPrediction {
    val gson = Gson()
    val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${req.token}",
    )
    val url = "https://inference.smallcloud.ai/v1/contrast"
    val json = gson.toJson(req.body)
    val response = sendRequest(url, "POST", headers, json.toString())
//    if (response.statusCode != 200) return
    val out = gson.fromJson(response.body.toString(), SMCPrediction::class.java)
    return out
}