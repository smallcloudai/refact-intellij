package com.smallcloud.refactai.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContext
import java.net.URI

fun getInferenceUrl(): URI? {
    val inferUrl = InferenceGlobalContext.inferenceUri ?: return null
    return inferUrl.resolve("v1/secret-key-activate")
}

fun inferenceLogin(): String {
    val conn = InferenceGlobalContext.connection ?: return ""

    val acc = AccountManager
    val token = acc.apiKey

    val inferUrl = getInferenceUrl() ?: return ""
    val headers = mutableMapOf(
        "Content-Type" to "application/json",
        "Authorization" to "Bearer $token"
    )
    try {
        val reqJob = conn.get(inferUrl, headers, requestProperties = mapOf(
            "redirect" to "follow",
            "cache" to "no-cache",
            "referrer" to "no-referrer"
        ))

        val result = reqJob.future.get() as String
        val gson = Gson()
        val body = gson.fromJson(result, JsonObject::class.java)
        if (body.has("retcode") && body.get("retcode").asString == "OK") {
            if (body.has("inference_message") && body.get("inference_message").asString.isNotEmpty()) {
                PluginState.instance.inferenceMessage = body.get("codify_message").asString
            }
            if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED
                || InferenceGlobalContext.status == ConnectionStatus.ERROR) {
                InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            }
            return "OK"
        } else if (body.has("detail")) {
            logError("inference_login", body.get("detail").asString)
        } else {
            logError("inference_login", result)
        }
        return ""
    } catch (e: Exception) {
        e.message?.let { logError("inference_login", it) }
        return ""
    }
}
