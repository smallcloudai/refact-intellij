package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.FimCache
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.struct.SMCExceptions
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCStreamingPeace
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

private fun lookForCommonErrors(json: JsonObject, request: SMCRequest): String? {
    if (json.has("detail")) {
        val gson = Gson()
        val detail = gson.toJson(json.get("detail"))
        UsageStats?.addStatistic(false, request.stat, request.uri.toString(), detail)
        return detail
    }
    if (json.has("retcode") && json.get("retcode").asString != "OK") {
        val message = if (json.has("human_readable_message")) {
            json.get("human_readable_message").asString
        } else {
            "Unknown error"
        }
        UsageStats?.addStatistic(false, request.stat, request.uri.toString(), message)
        return message
    }
    if (json.has("status") && json.get("status").asString == "error") {
        val message = if (json.has("human_readable_message")) {
            json.get("human_readable_message").asString
        } else {
            "Unknown error"
        }
        UsageStats?.addStatistic(false, request.stat, request.uri.toString(), message)
        return message
    }
    if (json.has("error")) {
        val errorObj = json.get("error").asJsonObject
        val message = if (errorObj.has("message")) {
            errorObj.get("message").asString
        } else {
            "Unknown error"
        }
        UsageStats?.addStatistic(false, request.stat, request.uri.toString(), message)
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
    val headers = mapOf(
        "Authorization" to "Bearer ${request.token}",
    )

    val job = InferenceGlobalContext.connection.post(
        uri, body, headers,
        stat = request.stat,
        dataReceiveEnded = dataReceiveEnded,
        dataReceived = { responseBody: String, reqId: String ->
            val rawJson = gson.fromJson(responseBody, JsonObject::class.java)
            if (rawJson.has("metering_balance")) {
                AccountManager.instance.meteringBalance = rawJson.get("metering_balance").asInt
            }

            FimCache.maybeSendFimData(responseBody)

            val json = gson.fromJson(responseBody, SMCStreamingPeace::class.java)
            InferenceGlobalContext.lastAutoModel = json.model
            json.requestId = reqId
            UsageStats?.addStatistic(true, request.stat, request.uri.toString(), "")
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
