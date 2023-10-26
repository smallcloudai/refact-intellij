package com.smallcloud.refactai

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.ErrorReportSubmitter
import com.intellij.openapi.diagnostic.IdeaLoggingEvent
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import com.smallcloud.refactai.struct.DeploymentMode
import java.awt.Component
import java.net.URLEncoder
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

class PluginErrorReportSubmitter : ErrorReportSubmitter(), Disposable {
    override fun submit(
        events: Array<out IdeaLoggingEvent>,
        additionalInfo: String?,
        parentComponent: Component,
        consumer: Consumer<in SubmittedReportInfo>
    ): Boolean {
        val event = events.firstOrNull()
        val eventMessage = event?.message ?: "(no message)"
        val eventThrowable = event?.throwableText ?: "(no stack trace)"
        val exceptionClassName = event?.throwableText?.lines()?.firstOrNull()?.split(':')?.firstOrNull()?.split('.')?.lastOrNull()?.let { ": $it" }.orEmpty()
        val issueTitle = "[JB plugin] Internal error${exceptionClassName}".urlEncoded()
        val ideNameAndVersion = ApplicationInfoEx.getInstanceEx().let { appInfo ->
            appInfo.fullApplicationName + "  " + "Build #" + appInfo.build.asString()
        }
        val mode = when(InferenceGlobalContext.deploymentMode) {
            DeploymentMode.CLOUD -> "Cloud"
            DeploymentMode.SELF_HOSTED -> "Self-Hosted/Enterprise"
            DeploymentMode.HF -> "HF"
        }
        val pluginVersion = getThisPlugin()?.version ?: "unknown"
        val properties = System.getProperties()
        val jdk = properties.getProperty("java.version", "unknown") +
            "; VM: " + properties.getProperty("java.vm.name", "unknown") +
            "; Vendor: " + properties.getProperty("java.vendor", "unknown")
        val os = SystemInfo.getOsNameAndVersion()
        val issueBody = """
      |An internal error happened in the IDE plugin.
      |
      |Message: $eventMessage
      |
      |### Stack trace
      |```
      |$eventThrowable
      |```
      |
      |### Environment
      |- Plugin version: $pluginVersion
      |- IDE: $ideNameAndVersion
      |- JDK: $jdk
      |- OS: $os
      |- MODE: $mode
      |
      |### Additional information
      |${additionalInfo.orEmpty()}
    """.trimMargin().urlEncoded()
        val gitHubUrl = "https://github.com/smallcloudai/refact-intellij/issues/new?" +
            "labels=bug" +
            "&title=${issueTitle}" +
            "&body=${issueBody}"
        BrowserUtil.browse(gitHubUrl)
        consumer.consume(SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
        return true
    }
    override fun getReportActionText() = RefactAIBundle.message("errorReport.actionText")

    override fun dispose() {}
}