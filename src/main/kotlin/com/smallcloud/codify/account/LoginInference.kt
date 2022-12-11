package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.io.sendRequest

fun get_inference_url(): String? {
    var infer_url = InferenceGlobalContext.inferenceUrl ?: return null

    if (infer_url.endsWith('/')) {
        infer_url = infer_url.dropLast(1)
    }
    return "$infer_url/v1/secret-key-activate"
}

fun inference_login() {
    val acc = AccountManager
    val token = acc.apiKey

    val infer_url = get_inference_url() ?: return
    val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer ${token}"
    )
    try {
        val result = sendRequest(infer_url, "GET", headers, request_properties = mapOf(
                "redirect" to "follow",
                "cache" to "no-cache",
                "referrer" to "no-referrer"
        ))
        val gson = Gson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        if (retcode == "OK") {
            if (body.has("inference_message") && body.get("inference_message").asString.isNotEmpty()) {
                SMCPlugin.instant.inference_message = body.get("codify_message").asString
            }
            Connection.status = ConnectionStatus.CONNECTED
            return
        } else if (body.has("detail")) {
            log_error("inference_login: ${body.get("detail").asString}")
        } else {
            log_error("inference_login: ${result.body}")
        }
    } catch (e: Exception) {
        log_error("inference_login: $e")
        return
    }


}