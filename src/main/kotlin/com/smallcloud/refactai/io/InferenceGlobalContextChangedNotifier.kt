package com.smallcloud.refactai.io

import com.intellij.util.messages.Topic
import com.smallcloud.refactai.struct.DeploymentMode
import java.net.URI

interface InferenceGlobalContextChangedNotifier {
    fun inferenceUriChanged(newUrl: URI?) {}
    fun userInferenceUriChanged(newUrl: String?) {}
    fun temperatureChanged(newTemp: Float?) {}
    fun modelChanged(newModel: String?) {}
    fun lastAutoModelChanged(newModel: String?) {}
    fun useAutoCompletionModeChanged(newValue: Boolean) {}
    fun developerModeEnabledChanged(newValue: Boolean) {}
    fun deploymentModeChanged(newMode: DeploymentMode) {}
    fun astFlagChanged(newValue: Boolean) {}
    fun astFileLimitChanged(newValue: Int) {}
    fun vecdbFlagChanged(newValue: Boolean) {}
    fun vecdbFileLimitChanged(newValue: Int) {}
    fun xDebugLSPPortChanged(newPort: Int?) {}
    fun insecureSSLChanged(newValue: Boolean) {}
    fun completionMaxTokensChanged(newMaxTokens: Int) {}
    fun telemetrySnippetsEnabledChanged(newValue: Boolean) {}
    fun experimentalLspFlagEnabledChanged(newValue: Boolean) {}

    companion object {
        val TOPIC = Topic.create(
            "Inference Global Context Changed Notifier",
            InferenceGlobalContextChangedNotifier::class.java
        )
    }
}
