package com.smallcloud.codify.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.Resources.default_login_url
import com.smallcloud.codify.Resources.default_recall_url
import com.smallcloud.codify.io.sendRequest
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

fun check_login() {
    val settings = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    val acc = AccountManager
    val inf_c = InferenceGlobalContext
    val is_logined = acc.is_logged_in
    if (is_logined) {
        return
    }

    val streamlined_login_ticket = acc.ticket
    val user_logged_in = acc.user
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
            if (retcode == "OK") {
                acc.apiKey = body.get("secret_key").asString
                acc.ticket = null
            } else {
//                TODO("this cathcer")
                return
            }

        } catch (e: Exception) {
//            TODO("this cathcer")
            return
        }
    }

    if (token.isNullOrEmpty()) {
        // wait until user clicks the login button
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
                inf_c.inferenceUrl = body.get("inference_url").asString
            }
            if (body.get("codify_message") != null) {
                val i = 0
//                TODO("set codify_message")
//                statusBar.set_website_message(json.codify_message);
            }

            acc.active_plan = PlanType.valueOf(body.get("inference").asString)
//            if (global.side_panel) {
//                global.side_panel.update_webview();
//            }
//
//            if (body.get("inference_url").asString == "DISABLED") {
//                TODO("set codify_message")
//                fetchAPI.save_url_from_login("");
//            }
//            if (body.get("login_message") != null) {
//                await usabilityHints.show_message_from_server("LoginServer", json.login_message);
//            }
//            usageStats.report_success_or_failure(true, "login", login_url, "", "");
//            inference_login_force_retry();
        } else if (retcode == "FAILED" && human_readable_message.contains("rate limit")) {
//            usageStats.report_success_or_failure(false, "login-failed", login_url, json.human_readable_message, "");
            return
        } else if (retcode == "FAILED") {
            // Login failed, but the request was a success.

            acc.user = null
//            global.user_active_plan = "";
//            if (global.side_panel) {
//                global.side_panel.update_webview();
//            }
//            usageStats.report_success_or_failure(true, "login-failed", login_url, json.human_readable_message, "");
            return
        } else {
            acc.user = null
//            global.user_active_plan = "";
//            if (global.side_panel) {
//                global.side_panel.update_webview();
//            }
//            usageStats.report_success_or_failure(false, "login (2)", login_url, "unrecognized response", "");
            return
        }


    } catch (e: Exception) {
        //            TODO("this cathcer")
        return
    }

}
