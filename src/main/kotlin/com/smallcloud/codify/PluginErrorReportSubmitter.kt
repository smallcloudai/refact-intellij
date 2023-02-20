package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.util.Consumer
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import java.awt.Component


class PluginErrorReportSubmitter : ErrorReportSubmitter() {
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
                false, "uncaught exceptions", "none",
                event.throwable.toString()
            )
        }

        return true
    }
    override fun getReportActionText(): String {
        return "Report error to plugin vendor"
    }
}