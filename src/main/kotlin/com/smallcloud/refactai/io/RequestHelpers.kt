package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.FimCache
import com.smallcloud.refactai.struct.SMCExceptions
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCStreamingPeace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

private fun lookForCommonErrors(json: JsonObject, request: SMCRequest): String? {
    if (json.has("detail")) {
        val gson = Gson()
        val detail = gson.toJson(json.get("detail"))
        return detail
    }
    if (json.has("retcode") && json.get("retcode").asString != "OK") {
        val message = if (json.has("human_readable_message")) {
            json.get("human_readable_message").asString
        } else {
            "Unknown error"
        }
        return message
    }
    if (json.has("status") && json.get("status").asString == "error") {
        val message = if (json.has("human_readable_message")) {
            json.get("human_readable_message").asString
        } else {
            "Unknown error"
        }
        return message
    }
    if (json.has("error")) {
        val errorObj = json.get("error").asJsonObject
        val message = if (errorObj.has("message")) {
            errorObj.get("message").asString
        } else {
            "Unknown error"
        }
        return message
    }
    return null
}

fun streamedInferenceFetch(
    request: SMCRequest,
    dataReceiveEnded: (String) -> Unit,
    dataReceived: (data: SMCStreamingPeace) -> Unit = {},
): CompletableFuture<Future<*>>? {
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val job = InferenceGlobalContext.connection.post(
        uri, body,
        dataReceiveEnded = dataReceiveEnded,
        dataReceived = { responseBody: String, reqId: String ->
            val rawJson = gson.fromJson(responseBody, JsonObject::class.java)
            FimCache.maybeSendFimData(responseBody)

            val json = gson.fromJson(responseBody, SMCStreamingPeace::class.java)
            InferenceGlobalContext.lastAutoModel = json.model
            json.requestId = reqId
            dataReceived(json)
        },
        errorDataReceived = {
            lookForCommonErrors(it, request)?.let { message ->
                throw SMCExceptions(message)
            }
        },
        requestId = request.id
    )

    return job
}
