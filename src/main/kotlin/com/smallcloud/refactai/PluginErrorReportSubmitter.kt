package com.smallcloud.refactai

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.UsageStats
import java.awt.Component
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


class PluginErrorReportSubmitter : ErrorReportSubmitter(), Disposable {
    private val stats: UsageStats
        get() = ApplicationManager.getApplication().getService(UsageStats::class.java)

    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        for (event in events) {
            InferenceGlobalContext.status = ConnectionStatus.ERROR
            InferenceGlobalContext.lastErrorMsg = events.firstOrNull()?.message
            stats.addStatistic(
                false, UsageStatistic("uncaught exceptions"), "none",
                event.throwable.toString()
            )
        }

        return true
    }
    override fun getReportActionText(): String {
        return "Report error to plugin vendor"
    }

    override fun dispose() {}
}