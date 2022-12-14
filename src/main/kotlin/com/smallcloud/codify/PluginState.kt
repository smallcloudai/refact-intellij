package com.smallcloud.codify

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.Topic
import com.smallcloud.codify.settings.AppSettingsState


interface ExtraInfoChangedNotifier {
    fun websiteMessageChanged(newMsg: String?) {}
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

    var websiteMessage: String? = null
        set(newMsg) {
            if (field == newMsg) return
            field = newMsg
            messageBus
                .syncPublisher(ExtraInfoChangedNotifier.TOPIC)
                .websiteMessageChanged(websiteMessage)
        }

    var inferenceMessage: String? = null
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
