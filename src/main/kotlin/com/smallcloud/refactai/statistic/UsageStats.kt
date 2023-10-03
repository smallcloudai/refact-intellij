package com.smallcloud.refactai.statistic

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources.defaultReportUrlSuffix
import com.smallcloud.refactai.Resources.defaultSnippetAcceptedUrlSuffix
import com.smallcloud.refactai.io.sendRequest
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder


class UsageStats: Disposable {
    private val execService = AppExecutorUtil.getAppScheduledExecutorService()

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
        val body = gson.toJson(
            mapOf(
                "success" to positive,
                "error_message" to errorMessageJson,
                "scope" to scopeJson,
                "url" to relatedUrl,
            )
        )
        val url = LSPProcessHolder.url.resolve(defaultReportUrlSuffix)
        execService.submit {
            try {
                val res = sendRequest(url, "POST", body=body)
                if (res.body.isNullOrEmpty()) return@submit

                val json = gson.fromJson(res.body, JsonObject::class.java)
                val success = if (json.has("success")) json.get("success").asInt else null
                if (success != null && success != 1) {
                    throw Exception(json.get("human_readable_message").asString)
                }
            } catch (e: Exception) {
                Logger.getInstance(UsageStats::class.java).warn("report to $url failed: $e")
            }
        }
    }

    fun snippetAccepted(snippetId: Int) {
        val url = LSPProcessHolder.url.resolve(defaultSnippetAcceptedUrlSuffix)
        execService.submit {
            try {
                val gson = Gson()
                val body = gson.toJson(
                    mapOf(
                        "snippet_telemetry_id" to snippetId,
                    )
                )
                val res = sendRequest(url, "POST", body=body)
                if (res.body.isNullOrEmpty()) return@submit

                val json = gson.fromJson(res.body, JsonObject::class.java)
                val success = if (json.has("success")) json.get("success").asInt else null
                if (success != null && success != 1) {
                    throw Exception(json.get("human_readable_message").asString)
                }
            } catch (e: Exception) {
                Logger.getInstance(UsageStats::class.java).warn("report to $url failed: $e")
            }
        }
    }

    companion object {
        @JvmStatic
        val instance: UsageStats
            get() = ApplicationManager.getApplication().getService(UsageStats::class.java)
    }

    override fun dispose() {}
}