package com.smallcloud.refactai.account

import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.defaultLoginUrl
import com.smallcloud.refactai.Resources.defaultLoginUrlSuffix
import com.smallcloud.refactai.Resources.defaultRecallUrl
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.sendRequest
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.utils.makeGson
import org.apache.http.client.utils.URIBuilder
import java.net.URI
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.aitoolbox.LongthinkFunctionProvider.Companion.instance as DiffIntentProviderInstance
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.listeners.QuickLongthinkActionsService.Companion.instance as QuickLongthinkActionsServiceInstance
import com.smallcloud.refactai.settings.ExtraState.Companion.instance as ExtraState
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

private fun generateTicket(): String {
    return (Math.random() * 1e16).toLong().toString(36) + "-" + (Math.random() * 1e16).toLong().toString(36)
}

private const val TICKET_PERIOD = 45 * 60 * 1000

fun login() {
    val isLoggedIn = AccountManager.isLoggedIn
    if (isLoggedIn) {
        return
    }

    val now = System.currentTimeMillis()
    if ((AccountManager.ticketCreatedTs == null || (now - AccountManager.ticketCreatedTs!!) > TICKET_PERIOD)) {
        AccountManager.ticket = generateTicket()
    }
    BrowserUtil.browse("https://codify.smallcloud.ai/authentication?token=${AccountManager.ticket}&utm_source=plugin&utm_medium=jetbrains&utm_campaign=login")

    runCounterTask()
}

fun logError(scope: String, msg: String, needChange: Boolean = true) {
    Logger.getInstance("check_login").warn("$scope: $msg")
    val conn = InferenceGlobalContext
    if (needChange) {
        conn.status = ConnectionStatus.ERROR
        conn.lastErrorMsg = msg
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
    var token = acc.apiKey
    val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to ""
    )

    if (!streamlinedLoginTicket.isNullOrEmpty() && (token.isNullOrEmpty() || force)) {
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
            val gson = makeGson()
            val body = gson.fromJson(result.body, JsonObject::class.java)
            val retcode = body.get("retcode").asString
            val humanReadableMessage =
                    if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
            if (retcode == "OK") {
                acc.apiKey = body.get("secret_key").asString
                acc.ticket = null
                infC.status = ConnectionStatus.CONNECTED
                UsageStats.addStatistic(true, UsageStatistic("recall"), recallUrl.toString(), "")
            } else if (retcode == "FAILED" && humanReadableMessage.contains("rate limit")) {
                logError("recall", humanReadableMessage, false)
                return "OK"
            } else {
                result.body?.let {
                    UsageStats.addStatistic(false, UsageStatistic("recall (1)"), recallUrl.toString(), it)
                    logError("recall", it)
                }
                return ""
            }

        } catch (e: Exception) {
            UsageStats.addStatistic(false, UsageStatistic("recall (2)"), recallUrl.toString(), e)
            e.message?.let { logError("recall", it) }
            return ""
        }
    }

    token = acc.apiKey
    if (token.isNullOrEmpty() && InferenceGlobalContext.isCloud) {
        return ""
    }

    val urlBuilder = if (infC.isSelfHosted && infC.inferenceUri != null)
        URIBuilder(infC.inferenceUri?.resolve(defaultLoginUrlSuffix)) else URIBuilder(defaultLoginUrl)

    if (infC.developerModeEnabled) {
        urlBuilder.addParameter("want_staging_version", "1")
    }
    urlBuilder.addParameter("plugin_version", "${Resources.client}-${Resources.version}")
    val url = urlBuilder.build()

    headers["Authorization"] = "Bearer $token"
    try {
        val result = sendRequest(
                url, "GET", headers, requestProperties = mapOf(
                "redirect" to "follow",
                "cache" to "no-cache",
                "referrer" to "no-referrer"
            )
        )

        val gson = makeGson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        val humanReadableMessage =
                if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
        if (retcode == "OK") {
            if (body.has("account")) {
                acc.user = body.get("account").asString
            }
            acc.ticket = null
            if (body.get("inference_url") != null) {
                if (body.get("inference_url").asString != "DISABLED") {
                    infC.cloudInferenceUri = URI(body.get("inference_url").asString)
                }
            }
            if (body.has("inference")) {
                acc.activePlan = body.get("inference").asString
            }

            if (body.has("tooltip_message") && body.get("tooltip_message").asString.isNotEmpty()) {
                PluginState.instance.tooltipMessage = body.get("tooltip_message").asString
            }
            if (body.has("login_message") && body.get("login_message").asString.isNotEmpty()) {
                PluginState.instance.loginMessage = body.get("login_message").asString
            }

            if (body.has("longthink-functions-today-v2")) {
                val cloudEntries = body.get("longthink-functions-today-v2").asJsonObject.entrySet().map {
                    val elem = gson.fromJson(it.value, LongthinkFunctionEntry::class.java)
                    elem.entryName = it.key
                    return@map elem.mergeLocalInfo(ExtraState.getLocalLongthinkInfo(elem.entryName))
                }
                DiffIntentProviderInstance.defaultThirdPartyFunctions = cloudEntries
                QuickLongthinkActionsServiceInstance.recreateActions()
            }

            if (body.has("longthink-filters")) {
                val filters = body.get("longthink-filters").asJsonArray.map { it.asString }
                DiffIntentProviderInstance.intentFilters = filters//.ifEmpty { listOf("") }
            }

            if (body.has("metering_balance")) {
                acc.meteringBalance = body.get("metering_balance").asInt
            }

            UsageStats.addStatistic(true, UsageStatistic("login"), url.toString(), "")
            return if (infC.isCloud) inferenceLogin() else "OK"
        } else if (retcode == "FAILED" && humanReadableMessage.contains("rate limitrate limit")) {
            logError("login-failed", humanReadableMessage, false)
            UsageStats.addStatistic(false, UsageStatistic("login-failed"), url.toString(), humanReadableMessage)
            return "OK"
        } else if (retcode == "FAILED") {
            acc.user = null
            acc.activePlan = null
            logError("login-failed", humanReadableMessage)
            UsageStats.addStatistic(false, UsageStatistic("login-failed"), url.toString(), humanReadableMessage)
            return ""
        } else {
            acc.user = null
            acc.activePlan = null
            logError("login-failed", "unrecognized response")
            UsageStats.addStatistic(false, UsageStatistic("login (2)"), url.toString(), "unrecognized response")
            return ""
        }
    } catch (e: Exception) {
        e.message?.let { logError("login-fail", it) }
        UsageStats.addStatistic(false, UsageStatistic("login (3)"), url.toString(), e)
        return ""
    }
}
