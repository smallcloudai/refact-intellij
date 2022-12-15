package com.smallcloud.codify.io

import com.smallcloud.codify.struct.SMCPrediction
import com.google.common.collect.EvictingQueue
import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.notifications.emitError
import com.smallcloud.codify.struct.SMCRequest


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
        val response = sendRequest(req.url, "POST", headers, json.toString())
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
