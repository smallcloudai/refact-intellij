package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.Resources.default_login_url
import com.smallcloud.codify.Resources.default_recall_url
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.io.sendRequest
import com.smallcloud.codify.notifications.emit_error
import com.smallcloud.codify.notifications.emit_info
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.PlanType

private fun generate_ticket(): String {
    return (Math.random() * 1e16).toLong().toString(36) + "-" + (Math.random() * 1e16).toLong().toString(36)
}

fun login() {
    val is_logined = AccountManager.is_logged_in
    if (is_logined) {
        return
    }
    AccountManager.ticket = generate_ticket()
    BrowserUtil.browse("https://codify.smallcloud.ai/authentication?token=${AccountManager.ticket}")
}

fun log_error(msg: String, need_change: Boolean = true) {
    Logger.getInstance("check_login").warn(msg)
    if (need_change) {
        Connection.status = ConnectionStatus.ERROR
        Connection.last_error_msg = msg
    }
}

fun check_login(force: Boolean = false) {
    val acc = AccountManager
    val inf_c = InferenceGlobalContext
    val is_logined = acc.is_logged_in
    if (is_logined && !force) {
        return
    }

    val streamlined_login_ticket = acc.ticket
    val token = acc.apiKey
    val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to ""
    )

    if (!streamlined_login_ticket.isNullOrEmpty() && token.isNullOrEmpty()) {
        val recall_url = default_recall_url
        headers["Authorization"] = "codify-${streamlined_login_ticket}"
        try {
            val result = sendRequest(recall_url, "GET", headers, request_properties = mapOf(
                    "redirect" to "follow",
                    "cache" to "no-cache",
                    "referrer" to "no-referrer"
            ))
            val gson = Gson()
            val body = gson.fromJson(result.body, JsonObject::class.java)
            val retcode = body.get("retcode").asString
            val human_readable_message = if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
            if (retcode == "OK") {
                acc.apiKey = body.get("secret_key").asString
                acc.ticket = null
                Connection.status = ConnectionStatus.CONNECTED
            } else if (retcode == "FAILED" && human_readable_message.contains("rate limit")) {
                log_error("recall: $human_readable_message", false)
//                log_error("login-fail: $human_readable_message")
                return
            } else {
                log_error("recall: ${result.body}")
                return
            }

        } catch (e: Exception) {
            log_error("recall: $e")
            return
        }
    }

    if (token.isNullOrEmpty()) {
        return;
    }

    val url = default_login_url
    headers["Authorization"] = "Bearer ${token}"
    try {
        val result = sendRequest(url, "GET", headers, request_properties = mapOf(
                "redirect" to "follow",
                "cache" to "no-cache",
                "referrer" to "no-referrer"
        ))
        val gson = Gson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        val human_readable_message = if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
        if (retcode == "OK") {
            acc.user = body.get("account").asString
            acc.ticket = null
            if (body.get("inference_url") != null) {
                if (body.get("inference_url").asString == "DISABLED") {
                    inf_c.inferenceUrl = null
                } else {
                    inf_c.inferenceUrl = body.get("inference_url").asString
                }
            }

            if (body.has("codify_message") && body.get("codify_message").asString.isNotEmpty()) {
                SMCPlugin.instant.website_message = body.get("codify_message").asString
            }

            acc.active_plan = PlanType.valueOf(body.get("inference").asString)

            if (body.has("login_message") && body.get("login_message").asString.isNotEmpty()) {
                SMCPlugin.instant.login_message = body.get("login_message").asString
            }
            Connection.status = ConnectionStatus.CONNECTED
            inference_login()
        } else if (retcode == "FAILED" && human_readable_message.contains("rate limitrate limit")) {
            log_error("login-fail: $human_readable_message", false)
//            log_error("login-fail: $human_readable_message")
            return
        } else if (retcode == "FAILED") {
            // Login failed, but the request was a success.

            acc.user = null
            acc.active_plan = PlanType.UNKNOWN
            log_error("login-fail: $human_readable_message")
            return
        } else {
            acc.user = null
            acc.active_plan = PlanType.UNKNOWN
            log_error("login-fail: unrecognized response")
            return
        }


    } catch (e: Exception) {
        log_error("login-fail: $e")
        return
    }

}
