package com.smallcloud.refactai.account

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.defaultCloudAuthLink
import com.smallcloud.refactai.Resources.defaultLoginUrl
import com.smallcloud.refactai.Resources.defaultRecallUrl
import com.smallcloud.refactai.Resources.loginSuffixUrl
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.sendRequest
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.DeploymentMode
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

private const val TICKET_PERIOD = 5 * 60 * 1000

fun login() {
    val isLoggedIn = AccountManager.isLoggedIn
    if (isLoggedIn) {
        return
    }

    val now = System.currentTimeMillis()
    if ((AccountManager.ticketCreatedTs == null || (now - AccountManager.ticketCreatedTs!!) > TICKET_PERIOD)) {
        AccountManager.ticket = generateTicket()
    }
    BrowserUtil.browse(defaultCloudAuthLink.format(AccountManager.ticket))
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

fun finishedGood() {
    val conn = InferenceGlobalContext
    conn.status = ConnectionStatus.CONNECTED
    conn.lastErrorMsg = null
}


private fun tryTicketPass(): String? {
    val headers = mutableMapOf("Content-Type" to "application/json", "Authorization" to "codify-${AccountManager.ticket}")
    try {
        val result = sendRequest(defaultRecallUrl, "GET", headers, requestProperties = mapOf("redirect" to "follow", "cache" to "no-cache", "referrer" to "no-referrer"))
        val gson = Gson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        val humanReadableMessage = if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
        if (retcode == "OK") {
            AccountManager.apiKey = body.get("secret_key").asString
            AccountManager.ticket = null
            InferenceGlobalContext.status = ConnectionStatus.CONNECTED
            UsageStats.addStatistic(true, UsageStatistic("recall"), defaultRecallUrl.toString(), "")
            finishedGood()
            return null
        } else if (retcode == "FAILED" && humanReadableMessage.contains("rate limit")) {
            logError("recall", humanReadableMessage, false)
            return "OK"
        } else {
            result.body?.let {
                UsageStats.addStatistic(false, UsageStatistic("recall (1)"), defaultRecallUrl.toString(), it)
                logError("recall", it)
            }
            return ""
        }

    } catch (e: Exception) {
        UsageStats.addStatistic(false, UsageStatistic("recall (2)"), defaultRecallUrl.toString(), e)
        e.message?.let { logError("recall", it) }
        return ""
    }
}

private fun buildLoginUrl(): URI {
    val urlBuilder = URIBuilder(if (InferenceGlobalContext.isCloud || InferenceGlobalContext.inferenceUri == null)
        defaultLoginUrl else URI(InferenceGlobalContext.inferenceUri!!).resolve(loginSuffixUrl))

    if (InferenceGlobalContext.developerModeEnabled && InferenceGlobalContext.stagingVersion.isNotEmpty()) {
        urlBuilder.addParameter("want_staging_version", InferenceGlobalContext.stagingVersion)
    }
    if (InferenceGlobalContext.isCloud) {
        urlBuilder.addParameter("plugin_version", "${Resources.client}-${Resources.version}")
    }
    return urlBuilder.build()
}

private fun tryLoginWithApiKey(): String {
    val token = AccountManager.apiKey

    val url = buildLoginUrl()
    val headers = mutableMapOf("Content-Type" to "application/json", "Authorization" to "Bearer $token")
    try {
        val result = sendRequest(url, "GET", headers, requestProperties = mapOf("redirect" to "follow", "cache" to "no-cache", "referrer" to "no-referrer"))

        val gson = makeGson()
        val body = gson.fromJson(result.body, JsonObject::class.java)
        val retcode = body.get("retcode").asString
        val humanReadableMessage = if (body.has("human_readable_message")) body.get("human_readable_message").asString else ""
        if (retcode == "OK") {
            if (body.has("account")) {
                AccountManager.user = body.get("account").asString
            }
            AccountManager.ticket = null
            if (body.get("inference_url") != null) {
                if (body.get("inference_url").asString != "DISABLED") {
                    InferenceGlobalContext.cloudInferenceUri = URI(body.get("inference_url").asString)
                }
            }
            if (body.has("inference")) {
                AccountManager.activePlan = body.get("inference").asString
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
                DiffIntentProviderInstance.intentFilters = filters
            }
            if (body.has("metering_balance")) {
                AccountManager.meteringBalance = body.get("metering_balance").asInt
            }

            if (body.has("chat-v1-style")) {
                InferenceGlobalContext.isNewChatStyle = (body.get("chat-v1-style").asInt > 0)
            }

            UsageStats.addStatistic(true, UsageStatistic("login"), url.toString(), "")
            finishedGood()
            return "OK"
        } else if (retcode == "FAILED" && humanReadableMessage.contains("rate limit")) {
            logError("login-failed", humanReadableMessage, false)
            UsageStats.addStatistic(false, UsageStatistic("login-failed"), url.toString(), humanReadableMessage)
            return "OK"
        } else if (retcode == "FAILED") {
            AccountManager.user = null
            AccountManager.activePlan = null
            logError("login-failed", humanReadableMessage)
            UsageStats.addStatistic(false, UsageStatistic("login-failed"), url.toString(), humanReadableMessage)
            return ""
        } else {
            AccountManager.user = null
            AccountManager.activePlan = null
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

fun checkLogin(force: Boolean = false): String {
    if (AccountManager.isLoggedIn && !force) {
        return ""
    }

    when (InferenceGlobalContext.deploymentMode) {
        DeploymentMode.CLOUD -> {
            if (!AccountManager.ticket.isNullOrEmpty() &&
                    (AccountManager.apiKey.isNullOrEmpty() || force)) {
                val status = tryTicketPass()
                if (status != null) {
                    return status
                }
            }
            if (AccountManager.apiKey.isNullOrEmpty()) {
                return ""
            }
        }

        else -> {}
    }

    return tryLoginWithApiKey()
}
