package com.smallcloud.codify.settings

import com.intellij.util.messages.Topic.create

interface SettingsChangedNotifier {
    fun tokenTextChanged(newValue: String?) {}
    fun modelChanged(newValue: String?) {}
    fun temperatureChanged(newValue: Float?) {}
    fun inferenceUriChanged(newValue: String?) {}
    fun useForceCompletionModeChanged(newValue: Boolean) {}
    fun useMultipleFilesCompletionChanged(newValue: Boolean) {}
    fun useStreamingCompletionChanged(newValue: Boolean) {}

    companion object {
        val TOPIC = create(
            "Settings Changed Notifier",
            SettingsChangedNotifier::class.java
        )
    }
}
