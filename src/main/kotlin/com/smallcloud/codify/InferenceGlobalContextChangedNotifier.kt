package com.smallcloud.codify

import com.intellij.util.messages.Topic

interface InferenceGlobalContextChangedNotifier {

    fun inferenceUrlChanged(newUrl: String?) {}
    fun temperatureChanged(newTemp: Float?) {}
    fun modelChanged(newModel: String?) {}

    companion object {
        val TOPIC = Topic.create("Inference Global Context Changed Notifier",
                InferenceGlobalContextChangedNotifier::class.java)
    }
}