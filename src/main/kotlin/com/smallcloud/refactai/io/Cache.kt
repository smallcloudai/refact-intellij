package com.smallcloud.refactai.io

import com.google.common.collect.EvictingQueue
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.notifications.emitError
import com.smallcloud.refactai.struct.SMCExceptions
import com.smallcloud.refactai.struct.SMCPrediction
import com.smallcloud.refactai.struct.SMCRequest
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

private object Cache {
    private val buffer = EvictingQueue.create<Pair<Int, SMCPrediction>>(15)

    fun getFromCache(request: SMCRequest): SMCPrediction? {
        synchronized(this) {
            val hash = request.hashCode()
            val elem = buffer.find { it.first == hash }
            return elem?.second
        }
    }

    fun addCache(request: SMCRequest, prediction: SMCPrediction) {
        synchronized(this) {
            val hash = request.hashCode()
            buffer.add(Pair(hash, prediction))
        }
    }

}

private fun fetchRequest(req: SMCRequest): SMCPrediction? {
    val gson = Gson()
    val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${req.token}",
    )
    val json = gson.toJson(req.body)
    return try {
        val response = sendRequest(req.uri, "POST", headers, json.toString())
        // TODO make normal statusCode
//        if (response.statusCode != 200) return null
        gson.fromJson(response.body.toString(), SMCPrediction::class.java)
    } catch (e: Exception) {
        emitError(e.toString())
        null
    }
}

fun fetch(request: SMCRequest): SMCPrediction? {
    val cache = Cache.getFromCache(request)
    if (cache != null) return cache
    Logger.getInstance("fetch").info("fetching the request")
    val prediction = fetchRequest(request) ?: return null
    Cache.addCache(request, prediction)
    return prediction
}

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
fun inferenceFetch(request: SMCRequest): RequestJob? {
    val cache = Cache.getFromCache(request)
    if (cache != null)
        return RequestJob(CompletableFuture.supplyAsync {
            return@supplyAsync cache
        }, null)
    val gson = Gson()
    val uri = request.uri
    val body = gson.toJson(request.body)
    val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${request.token}",
            "cache" to "no-cache",
            "referrer" to "no-referrer"
    )

    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED) return null
    val now = System.currentTimeMillis()
    val needToVerify = (now - lastInferenceVerifyTs) > Resources.inferenceLoginCoolDown * 1000
    if (needToVerify) lastInferenceVerifyTs = now

    val job = InferenceGlobalContext.connection?.post(uri, body, headers, needVerify = needToVerify, stat = request.stat)
            ?: return null

    job.future = job.future.thenApplyAsync {
        val rawObject = gson.fromJson((it as String), JsonObject::class.java)
        val errorMsg = lookForCommonErrors(rawObject, request)
        if (errorMsg != null) {
            throw Exception(errorMsg)
        }
        if (rawObject.has("metering_balance")) {
            AccountManager.meteringBalance = rawObject.get("metering_balance").asInt
        }
        val json = gson.fromJson(it, SMCPrediction::class.java)
        InferenceGlobalContext.lastAutoModel = json.model
        UsageStats.addStatistic(true, request.stat, request.uri.toString(), "")
        return@thenApplyAsync json
    }

    return job
}

fun streamedInferenceFetch(
        request: SMCRequest,
        dataReceiveEnded: () -> Unit,
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

    if (InferenceGlobalContext.inferenceConnection == null) {
        InferenceGlobalContext.reconnect()
    }

    val job = InferenceGlobalContext.inferenceConnection?.post(
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
