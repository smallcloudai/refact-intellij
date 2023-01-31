package com.smallcloud.codify.io

import com.intellij.util.messages.Topic
import java.net.URI

interface InferenceGlobalContextChangedNotifier {
    fun inferenceUriChanged(newUrl: URI?) {}
    fun userInferenceUriChanged(newUrl: URI?) {}
    fun temperatureChanged(newTemp: Float?) {}
    fun modelChanged(newModel: String?) {}
    fun longThinkModelChanged(newModel: String?) {}
    fun lastAutoModelChanged(newModel: String?) {}
    fun useForceCompletionModeChanged(newValue: Boolean) {}
    fun useMultipleFilesCompletionChanged(newValue: Boolean) {}

    companion object {
        val TOPIC = Topic.create(
            "Inference Global Context Changed Notifier",
            InferenceGlobalContextChangedNotifier::class.java
        )
    }
}
