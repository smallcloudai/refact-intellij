package com.smallcloud.codify.io

import com.smallcloud.codify.struct.SMCPrediction
import com.google.common.collect.EvictingQueue
import com.google.gson.Gson
import com.smallcloud.codify.notifications.emit_error
import com.smallcloud.codify.struct.SMCRequest


private object Cache {
    private val buffer: EvictingQueue<Pair<Int, SMCPrediction>> = EvictingQueue.create(15)

    fun get_from_cache(request: SMCRequest): SMCPrediction? {
        val hash = request.hashCode()
        val elem = buffer.find { it -> it.first == hash }
        return elem?.second
    }

    fun add_cache(request: SMCRequest, prediction: SMCPrediction) {
        val hash = request.hashCode()
        buffer.add(Pair(hash, prediction))
    }

}

private fun _fetch(req: SMCRequest): SMCPrediction? {
    val gson = Gson()
    val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${req.token}",
    )
    val json = gson.toJson(req.body)
    try {
        val response = sendRequest(req.url, "POST", headers, json.toString())
        // TODO make normal statusCode
//        if (response.statusCode != 200) return null
        return gson.fromJson(response.body.toString(), SMCPrediction::class.java)
    } catch (e: Exception) {
        emit_error(e.toString())
        return null
    }
}

fun fetch(request: SMCRequest): SMCPrediction? {
    val cache = Cache.get_from_cache(request)
    if (cache != null) return cache
    val prediction = _fetch(request) ?: return null

    Cache.add_cache(request, prediction)
    return prediction
}