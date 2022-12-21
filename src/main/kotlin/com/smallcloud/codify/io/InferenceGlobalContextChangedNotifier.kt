package com.smallcloud.codify.io

import com.intellij.util.messages.Topic
import java.net.URI

interface InferenceGlobalContextChangedNotifier {
    fun inferenceUriChanged(newUrl: URI?) {}
    fun userInferenceUriChanged(newUrl: URI?) {}
    fun temperatureChanged(newTemp: Float?) {}
    fun modelChanged(newModel: String?) {}

    companion object {
        val TOPIC = Topic.create(
            "Inference Global Context Changed Notifier",
            InferenceGlobalContextChangedNotifier::class.java
        )
    }
}
