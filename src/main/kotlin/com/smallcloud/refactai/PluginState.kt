package com.smallcloud.refactai

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.settings.AppSettingsState


interface ExtraInfoChangedNotifier {
    fun tooltipMessageChanged(newMsg: String?) {}
    fun inferenceMessageChanged(newMsg: String?) {}
    fun loginMessageChanged(newMsg: String?) {}

    companion object {
        val TOPIC = Topic.create("Extra Info Changed Notifier", ExtraInfoChangedNotifier::class.java)
    }
}

class PluginState : Disposable {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    var tooltipMessage: String?
        get() = AppSettingsState.instance.tooltipMessage
        set(newMsg) {
            if (AppSettingsState.instance.tooltipMessage == newMsg) return
            AppSettingsState.instance.tooltipMessage = newMsg
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .tooltipMessageChanged(newMsg)
        }

    var inferenceMessage: String?
        get() = AppSettingsState.instance.inferenceMessage
        set(newMsg) {
            if (AppSettingsState.instance.inferenceMessage == newMsg) return
            AppSettingsState.instance.inferenceMessage = newMsg
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .inferenceMessageChanged(newMsg)
        }

    var loginMessage: String?
        get() = AppSettingsState.instance.loginMessage
        set(newMsg) {
            if (AppSettingsState.instance.loginMessage == newMsg) return
            AppSettingsState.instance.loginMessage = newMsg
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .loginMessageChanged(newMsg)
        }

    override fun dispose() {}

    companion object {
        @JvmStatic
        val instance: PluginState
            get() = ApplicationManager.getApplication().getService(PluginState::class.java)
    }
}
