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
    fun astLightModeChanged(newValue: Boolean) {}
    fun vecdbFlagChanged(newValue: Boolean) {}
    fun vecdbFileLimitChanged(newValue: Int) {}
    fun xDebugLSPPortChanged(newPort: Int?) {}

    companion object {
        val TOPIC = Topic.create(
            "Inference Global Context Changed Notifier",
            InferenceGlobalContextChangedNotifier::class.java
        )
    }
}
