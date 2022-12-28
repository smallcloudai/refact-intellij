package com.smallcloud.codify

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.smallcloud.codify.settings.AppSettingsState


interface ExtraInfoChangedNotifier {
    fun tooltipMessageChanged(newMsg: String?) {}
    fun inferenceMessageChanged(newMsg: String?) {}
    fun loginMessageChanged(newMsg: String?) {}
    fun pluginEnableChanged(newVal: Boolean) {}

    companion object {
        val TOPIC = Topic.create("Extra Info Changed Notifier", ExtraInfoChangedNotifier::class.java)
    }
}

class PluginState : Disposable {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    var isEnabled: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .pluginEnableChanged(field)
        }

    var tooltipMessage: String? = null
        get() = AppSettingsState.instance.tooltipMessage
        set(newMsg) {
            if (field == newMsg) return
            field = newMsg
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .tooltipMessageChanged(field)
        }

    var inferenceMessage: String? = null
        get() = AppSettingsState.instance.inferenceMessage
        set(newMsg) {
            if (field != newMsg) {
                field = newMsg
                messageBus
                    .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                    .inferenceMessageChanged(field)
            }
        }

    var loginMessage: String?
        get() = AppSettingsState.instance.loginMessage
        set(newMsg) {
            if (loginMessage == newMsg) return
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .loginMessageChanged(newMsg)
        }

    override fun dispose() {
    }

    companion object {
        var instance = PluginState()
        fun startup(settings: AppSettingsState) {
            instance.isEnabled = settings.pluginIsEnabled
        }
    }
}
