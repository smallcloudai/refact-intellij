package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.sendRequest
import java.net.URI

fun getInferenceUrl(): URI? {
    val inferUrl = InferenceGlobalContext.inferenceUri ?: return null
    return inferUrl.resolve("v1/secret-key-activate")
}

fun inferenceLogin(): String {
    val conn = InferenceGlobalContext.connection?: return ""

    val acc = AccountManager
    val token = acc.apiKey

    val inferUrl = getInferenceUrl() ?: return ""
    val headers = mutableMapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $token"
    )
    try {
        val result = sendRequest(
            inferUrl,
            "GET", headers, requestProperties = mapOf(
                "redirect" to "follow",
                "cache" to "no-cache",
                "referrer" to "no-referrer"
            )
        )
        val gson = Gson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        if (body.has("retcode") && body.get("retcode").asString == "OK") {
            if (body.has("inference_message") && body.get("inference_message").asString.isNotEmpty()) {
                PluginState.instance.inferenceMessage = body.get("codify_message").asString
            }
            conn.status = ConnectionStatus.CONNECTED
            return "OK"
        } else if (body.has("detail")) {
            logError("inference_login: ${body.get("detail").asString}")
        } else {
            logError("inference_login: ${result.body}")
        }
        return ""
    } catch (e: Exception) {
        logError("inference_login: $e")
        return ""
    }
}
