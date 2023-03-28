package com.smallcloud.refactai.statistic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.io.sendRequest
import org.apache.http.client.utils.URIBuilder
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

class ExtraInfoService: Disposable {
    private data class LikeRecord(val before: Boolean, var current: Boolean)
    private val likeSendCandidates: MutableMap<String, LikeRecord> = mapOf<String, LikeRecord>().toMutableMap()

    private var reportTask: Future<*>? = null
    private fun makeTask() {
        if (reportTask == null || reportTask!!.isDone) {
            reportTask = AppExecutorUtil.getAppScheduledExecutorService().schedule({
                report()
            }, 1, TimeUnit.SECONDS/*5, TimeUnit.MINUTES*/)
        }
    }


    fun addLike(functionName: String, isLiked: Boolean) {
        synchronized(this) {
            if (likeSendCandidates.containsKey(functionName)) {
                likeSendCandidates[functionName]!!.current = isLiked
            } else {
                likeSendCandidates[functionName] = LikeRecord(!isLiked, isLiked)
            }
            makeTask()
        }
    }
    private fun addLike(functionName: String, record: LikeRecord) {
        synchronized(this) {
            likeSendCandidates[functionName] = record
            makeTask()
        }
    }

    private fun report() {
        val acc = AccountManager
        val token: String = acc.apiKey ?: return

        val headers = mutableMapOf(
            "Authorization" to "Bearer $token"
        )
        val likes: Map<String, LikeRecord>
        synchronized(this) {
            likes = likeSendCandidates.toMap()
            likeSendCandidates.clear()
        }

        val gson = Gson()
        likes.forEach {
            if (it.value.before != it.value.current) {
                val url = URIBuilder(Resources.defaultLikeReportUrl)
                    .addParameter("function_name", it.key)
                    .addParameter("like", it.value.current.compareTo(false).toString())
                    .build()
                try {
                    val res = sendRequest(url, "GET", headers)
                    if (res.body.isNullOrEmpty()) return@forEach

                    val json = gson.fromJson(res.body, JsonObject::class.java)
                    val retcode = if (json.has("retcode")) json.get("retcode").asString else null
                    if (retcode != null && retcode != "OK") {
                        throw Exception(json.get("human_readable_message").asString)
                    }
                } catch (e: Exception) {
                    Logger.getInstance(UsageStats::class.java).warn("report to $url failed: $e")
                    addLike(it.key, it.value)
                    UsageStats.addStatistic(
                        false, UsageStatistic("longthink-like"),
                        url.toString(), e
                    )
                }
            }
        }
    }

    companion object {
        @JvmStatic
        val instance: ExtraInfoService
            get() = ApplicationManager.getApplication().getService(ExtraInfoService::class.java)
    }

    override fun dispose() {
        reportTask?.cancel(true)
        report()
    }
}