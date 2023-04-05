package com.smallcloud.refactai.statistic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.defaultReportUrl
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.io.sendRequest
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.settings.ExtraState.Companion.instance as ExtraState


class UsageStats: Disposable {
    private var messages: MutableMap<String, Int>
        set(newMap) {
            ExtraState.usageStatsMessagesCache = newMap
        }
        get() {
            return ExtraState.usageStatsMessagesCache
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
        stat: UsageStatistic,
        relatedUrl: String,
        errorMessage: Any
    ) {
        var errorMessageStr = errorMessage.toString()
        val gson = Gson()
        if (errorMessageStr.length > 200) {
            errorMessageStr = errorMessageStr.substring(0, 200) + "…"
        }

        val errorMessageJson = gson.toJson(errorMessageStr)
        var scope = stat.scope
        if (stat.subScope.isNotEmpty()) {
            scope += ":" + stat.subScope
        }

        val scopeJson = gson.toJson(scope)
        val message = "${positive.compareTo(false)}\t" +
                "$scopeJson\t" +
                "$relatedUrl\t" +
                "$errorMessageJson"
        synchronized(this) {
            if (messages.containsKey(message)) {
                messages[message] = messages[message]!! + 1
            } else {
                messages[message] = 1
            }
        }
    }

    fun forceReport() {
        report()?.get()
    }

    private fun report(): Future<*>? {
        val acc = AccountManager
        val token: String = acc.apiKey ?: return null

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

        if (lastMessages.isEmpty()) return null
        var usage = ""
        lastMessages.forEach {
            usage += "${it.key}\t${it.value}\n"
        }
        val gson = Gson()
        val body = gson.toJson(
            mapOf(
                "client_version" to "${Resources.client}-${Resources.version}",
                "ide_version" to Resources.jbBuildVersion,
                "usage" to usage
            )
        )
        return AppExecutorUtil.getAppExecutorService().submit {
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
                instance.addStatistic(false, UsageStatistic(scope="usage stats report"), url.toString(), e)
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
    }

    override fun dispose() {
        task.cancel(true)
        forceReport()
    }
}