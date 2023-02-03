package com.smallcloud.codify.statistic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Resources
import com.smallcloud.codify.UsageStats
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.io.sendRequest
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class StatisticService {
    private val stats = mutableListOf<CompletionStatistic>()
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "CodifyStatisticScheduler", 1
    )
    private var task: Future<*>? = null

    init {
        task = scheduler.schedule({
            report()
            task = scheduler.scheduleWithFixedDelay({
                report()
            }, 1, 1, TimeUnit.HOURS)
        }, 5, TimeUnit.MINUTES)
    }

    private fun report() {
        if (stats.isEmpty()) return
        val acc = AccountManager
        val token: String = acc.apiKey ?: return

        val headers = mutableMapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $token"
        )
        val url = Resources.defaultAcceptRejectReportUrl
        val usage = mutableMapOf<String, Int>()
        var oldStats = mutableListOf<CompletionStatistic>()
        synchronized(this) {
            oldStats = stats.toMutableList()
            stats.clear()
        }

        oldStats.forEach {
            it.getMetrics().forEach { key ->
                if (usage.containsKey(key)) {
                    usage[key] = usage[key]!! + 1
                } else {
                    usage[key] = 1
                }
            }
        }

        val gson = Gson()
        val body = gson.toJson(
            mapOf(
                "client_version" to "${Resources.client}-${Resources.version}",
                "usage" to gson.toJson(
                    mapOf(
                        "completion" to usage
                    )
                )
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
                mergeMessages(oldStats)
                UsageStats.instance.addStatistic(
                    false, "accept/reject usage stats report",
                    url.toString(), e
                )
            }
        }
    }

    fun addCompletionStatistic(stat: CompletionStatistic) {
        synchronized(this) {
            stats.add(stat)
        }
    }

    private fun mergeMessages(newStats: List<CompletionStatistic>) {
        synchronized(this) {
            stats += newStats
        }
    }

    companion object {
        @JvmStatic
        val instance: StatisticService
            get() = ApplicationManager.getApplication().getService(StatisticService::class.java)
    }
}