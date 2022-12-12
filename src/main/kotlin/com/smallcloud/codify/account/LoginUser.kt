package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.Resources.defaultLoginUrl
import com.smallcloud.codify.Resources.defaultRecallUrl
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.io.sendRequest
import com.smallcloud.codify.struct.PlanType

private fun generateTicket(): String {
    return (Math.random() * 1e16).toLong().toString(36) + "-" + (Math.random() * 1e16).toLong().toString(36)
}

fun login() {
    val isLogined = AccountManager.isLoggedIn
    if (isLogined) {
        return
    }
    AccountManager.ticket = generateTicket()
    BrowserUtil.browse("https://codify.smallcloud.ai/authentication?token=${AccountManager.ticket}")
}

fun logError(msg: String, need_change: Boolean = true) {
    Logger.getInstance("check_login").warn(msg)
    if (need_change) {
        Connection.status = ConnectionStatus.ERROR
        Connection.last_error_msg = msg
    }
}

fun checkLogin(force: Boolean = false): String {
    val acc = AccountManager
    val infC = InferenceGlobalContext
    val isLoggedIn = acc.isLoggedIn
    if (isLoggedIn && !force) {
        return ""
    }

    val streamlinedLoginTicket = acc.ticket
    val token = acc.apiKey
    val headers = mutableMapOf(
        "Content-Type" to "application/json",
        "Authorization" to ""
    )

    if (!streamlinedLoginTicket.isNullOrEmpty() && token.isNullOrEmpty()) {
        val recallUrl = defaultRecallUrl
        headers["Authorization"] = "codify-${streamlinedLoginTicket}"
        try {
            val result = sendRequest(
                recallUrl, "GET", headers, requestProperties = mapOf(
                    "redirect" to "follow",
                    "cache" to "no-cache",
                    "referrer" to "no-referrer"
                )
            )
            val gson = Gson()
            val body = gson.fromJson(result.body, JsonObject::class.java)
            val retcode = body.get("retcode").asString
            val humanReadableMessage =
                if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
            if (retcode == "OK") {
                acc.apiKey = body.get("secret_key").asString
                acc.ticket = null
                Connection.status = ConnectionStatus.CONNECTED
                return "OK"
            } else if (retcode == "FAILED" && humanReadableMessage.contains("rate limit")) {
                logError("recall: $humanReadableMessage", false)
//                log_error("login-fail: $human_readable_message")
                return "OK"
            } else {
                logError("recall: ${result.body}")
                return ""
            }

        } catch (e: Exception) {
            logError("recall: $e")
            return ""
        }
    }

    if (token.isNullOrEmpty()) {
        return ""
    }

    val url = defaultLoginUrl
    headers["Authorization"] = "Bearer ${token}"
    try {
        val result = sendRequest(
            url, "GET", headers, requestProperties = mapOf(
                "redirect" to "follow",
                "cache" to "no-cache",
                "referrer" to "no-referrer"
            )
        )
        val gson = Gson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        val human_readable_message =
            if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
        if (retcode == "OK") {
            acc.user = body.get("account").asString
            acc.ticket = null
            if (body.get("inference_url") != null) {
                if (body.get("inference_url").asString == "DISABLED") {
                    infC.inferenceUrl = null
                } else {
                    infC.inferenceUrl = body.get("inference_url").asString
                }
            }

            if (body.has("codify_message") && body.get("codify_message").asString.isNotEmpty()) {
                SMCPlugin.instance.websiteMessage = body.get("codify_message").asString
            }

            acc.activePlan = PlanType.valueOf(body.get("inference").asString)

            if (body.has("login_message") && body.get("login_message").asString.isNotEmpty()) {
                SMCPlugin.instance.loginMessage = body.get("login_message").asString
            }
            Connection.status = ConnectionStatus.CONNECTED
            inferenceLogin()
            return inferenceLogin()
        } else if (retcode == "FAILED" && human_readable_message.contains("rate limitrate limit")) {
            logError("login-fail: $human_readable_message", false)
//            log_error("login-fail: $human_readable_message")
            return "OK"
        } else if (retcode == "FAILED") {
            // Login failed, but the request was a success.

            acc.user = null
            acc.activePlan = PlanType.UNKNOWN
            logError("login-fail: $human_readable_message")
            return ""
        } else {
            acc.user = null
            acc.activePlan = PlanType.UNKNOWN
            logError("login-fail: unrecognized response")
            return ""
        }


    } catch (e: Exception) {
        logError("login-fail: $e")
        return ""
    }

}
