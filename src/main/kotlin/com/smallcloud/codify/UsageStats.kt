package com.smallcloud.codify

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Resources.defaultReportUrl
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.io.sendRequest
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.codify.settings.AppSettingsState.Companion.instance as Settings


class UsageStats {
    private var messages: MutableMap<String, Int>
        set(newMap) {
            Settings.usageStatsMessagesCache = newMap
        }
        get() {
            return Settings.usageStatsMessagesCache
        }
    private var task: Future<*>

    init {
        task = AppExecutorUtil.getAppScheduledExecutorService().schedule({
            report()
            task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
                report()
            }, 1, 1, TimeUnit.HOURS)
        }, 5, TimeUnit.MINUTES)
    }

    fun addStatistic(
        positive: Boolean,
        scope: String,
        relatedUrl: String,
        errorMessage: Any
    ) {
        val errorMsgString = if (errorMessage !is String) {
            errorMessage.toString()
        } else {
            errorMessage
        }
        val message = "${positive.compareTo(false)} $scope $relatedUrl $errorMsgString"
        synchronized(this) {
            if (messages.containsKey(message)) {
                messages[message] = messages[message]!! + 1
            } else {
                messages[message] = 1
            }
        }
    }

    private fun report() {
        val acc = AccountManager
        val token: String = acc.apiKey ?: return

        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $token"
        )
        val url = defaultReportUrl
        val lastMessages: MutableMap<String, Int>
        synchronized(this) {
            lastMessages = HashMap(messages)
            messages.clear()
        }

        if (lastMessages.isEmpty()) return
        var usage = ""
        lastMessages.forEach {
            usage += "${it.key} ${it.value}\n"
        }
        val gson = Gson()
        val body = gson.toJson(
            mapOf(
                "client_version" to "${Resources.client}-${Resources.version}",
                "usage" to usage
            )
        )
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                val res = sendRequest(url, "POST", headers, body)
                if (res.body.isNullOrEmpty()) return@submit

                val json = gson.fromJson(res.body, JsonObject::class.java)
                val retcode = if (json.has("retcode")) json.get("retcode").asString else null
                if (retcode != null && retcode != "OK") {
                    throw Exception(json.get("human_readable_message").asString)
                }
            } catch (e: Exception) {
                Logger.getInstance(UsageStats::class.java).warn("report to $url failed: $e")
                instance.mergeMessages(lastMessages)
                instance.addStatistic(false, "usage stats report", url.toString(), e)
            }
        }
    }

    private fun mergeMessages(newMessages: MutableMap<String, Int>) {
        synchronized(this) {
            messages = HashMap((messages.toList() + newMessages.toList())
                .groupBy({ it.first }, { it.second })
                .map { (key, values) -> key to values.sum() }
                .toMap())
        }
    }

    companion object {
        @JvmStatic
        val instance: UsageStats
            get() = ApplicationManager.getApplication().getService(UsageStats::class.java)

        val addStatistic = instance::addStatistic
    }
}