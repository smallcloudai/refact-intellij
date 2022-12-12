package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.io.sendRequest

fun getInferenceUrl(): String? {
    var inferUrl = InferenceGlobalContext.inferenceUrl ?: return null

    if (inferUrl.endsWith('/')) {
        inferUrl = inferUrl.dropLast(1)
    }
    return "$inferUrl/v1/secret-key-activate"
}

fun inferenceLogin(): String {
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
        val retcode = body.get("retcode").asString
        if (retcode == "OK") {
            if (body.has("inference_message") && body.get("inference_message").asString.isNotEmpty()) {
                SMCPlugin.instance.inferenceMessage = body.get("codify_message").asString
            }
            Connection.status = ConnectionStatus.CONNECTED
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
