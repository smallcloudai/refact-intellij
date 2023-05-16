package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.struct.SMCExceptions
import com.smallcloud.refactai.struct.SMCPrediction
import com.smallcloud.refactai.struct.SMCRequest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

private fun lookForCommonErrors(json: JsonObject, request: SMCRequest): String? {
    if (json.has("detail")) {
        val gson = Gson()
        val detail = gson.toJson(json.get("detail"))
        UsageStats.addStatistic(false, request.stat, request.uri.toString(), detail)
        return detail
    }
    if (json.has("retcode") && json.get("retcode").asString != "OK") {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("status") && json.get("status").asString == "error") {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("human_readable_message").asString
        )
        return json.get("human_readable_message").asString
    }
    if (json.has("error")) {
        UsageStats.addStatistic(
                false, request.stat,
                request.uri.toString(), json.get("error").asJsonObject.get("message").asString
        )
        return json.get("error").asJsonObject.get("message").asString
    }
    return null
}

private var lastInferenceVerifyTs: Long = -1

fun streamedInferenceFetch(
        request: SMCRequest,
        dataReceiveEnded: (String) -> Unit,
        dataReceived: (data: SMCPrediction) -> Unit,
): CompletableFuture<Future<*>>? {
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
            "Authorization" to "Bearer ${request.token}",
    )

    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return null
    val now = System.currentTimeMillis()
    val needToVerify = (now - lastInferenceVerifyTs) > Resources.inferenceLoginCoolDown * 1000
    if (needToVerify) lastInferenceVerifyTs = now

    val job = InferenceGlobalContext.connection.post(
            uri, body, headers,
            needVerify = needToVerify, stat = request.stat,
            dataReceiveEnded = dataReceiveEnded,
            dataReceived = {
                val json = gson.fromJson(it, SMCPrediction::class.java)
                InferenceGlobalContext.lastAutoModel = json.model
                UsageStats.addStatistic(true, request.stat, request.uri.toString(), "")
                dataReceived(json)
            },
            errorDataReceived = {
                lookForCommonErrors(it, request)?.let { message ->
                    throw SMCExceptions(message)
                }
            }
    )

    return job
}

fun inferenceFetch(
        request: SMCRequest,
        dataReceiveEnded: (SMCPrediction) -> Unit,
): CompletableFuture<Future<*>>? {
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
            "Authorization" to "Bearer ${request.token}",
    )

    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return null
    val now = System.currentTimeMillis()
    val needToVerify = (now - lastInferenceVerifyTs) > Resources.inferenceLoginCoolDown * 1000
    if (needToVerify) lastInferenceVerifyTs = now

    val job = InferenceGlobalContext.connection.post(
            uri, body, headers,
            needVerify = needToVerify, stat = request.stat,
            dataReceiveEnded = {
                val json = gson.fromJson(it, SMCPrediction::class.java)
                InferenceGlobalContext.lastAutoModel = json.model
                UsageStats.addStatistic(true, request.stat, request.uri.toString(), "")
                dataReceiveEnded(json)
            },
            errorDataReceived = {
                lookForCommonErrors(it, request)?.let { message ->
                    throw SMCExceptions(message)
                }
            }
    )

    return job
}
