package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import com.smallcloud.codify.ExtraInfoChangedNotifier
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.io.InferenceGlobalContextChangedNotifier
import java.net.URI


/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "com.smallcloud.userSettings.AppSettingsState", storages = [Storage("CodifySettings.xml")])
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var apiKey: String? = null
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .tokenTextChanged(field)
        }
    var temperature: Float? = null
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .temperatureChanged(field)
        }
    var model: String? = null
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .modelChanged(field)
        }
    var userLoggedIn: String? = null
    var streamlinedLoginTicket: String? = null
    var inferenceUri: String? = null
    var userInferenceUri: String? = null
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .inferenceUriChanged(field)
        }
    var activePlan: String? = null
    var loginMessage: String? = null
    var tooltipMessage: String? = null
    var inferenceMessage: String? = null
    var pluginIsEnabled: Boolean = true
    var usageStatsMessagesCache: MutableMap<String, Int> = HashMap()
    var useForceCompletion: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .useForceCompletionModeChanged(field)
        }
    var useMultipleFilesCompletion: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .useMultipleFilesCompletionChanged(field)
        }
    var useStreamingCompletion: Boolean = false
        set(value) {
            if (value == field) return
            field = value
            messageBus
                .syncPublisher(SettingsChangedNotifier.TOPIC)
                .useStreamingCompletionChanged(field)
        }
    var diffIntentsHistory: List<String> = emptyList()

    @Transient
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    init {
        messageBus
            .connect(PluginState.instance)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun ticketChanged(newTicket: String?) {
                    instance.streamlinedLoginTicket = newTicket
                }

                override fun userChanged(newUser: String?) {
                    instance.userLoggedIn = newUser
                }

                override fun apiKeyChanged(newApiKey: String?) {
                    instance.apiKey = newApiKey
                }

                override fun planStatusChanged(newPlan: String?) {
                    instance.activePlan = newPlan
                }
            })
        messageBus
            .connect(PluginState.instance)
            .subscribe(
                InferenceGlobalContextChangedNotifier.TOPIC,
                object : InferenceGlobalContextChangedNotifier {
                    override fun inferenceUriChanged(newUrl: URI?) {
                        instance.inferenceUri = newUrl?.toString()

                    }

                    override fun userInferenceUriChanged(newUrl: URI?) {
                        instance.userInferenceUri = newUrl?.toString()
                    }

                    override fun modelChanged(newModel: String?) {
                        instance.model = newModel?.trim()
                    }

                    override fun temperatureChanged(newTemp: Float?) {
                        instance.temperature = newTemp
                    }
                })
        messageBus
            .connect(PluginState.instance)
            .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
                override fun loginMessageChanged(newMsg: String?) {
                    instance.loginMessage = newMsg
                }

                override fun tooltipMessageChanged(newMsg: String?) {
                    instance.tooltipMessage = newMsg
                }

                override fun inferenceMessageChanged(newMsg: String?) {
                    instance.inferenceMessage = newMsg
                }

                override fun pluginEnableChanged(newVal: Boolean) {
                    instance.pluginIsEnabled = newVal
                }
            })
    }


    override fun getState(): AppSettingsState {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        val instance: AppSettingsState
            get() = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    }
}

fun settingsStartup() {
    val settings = AppSettingsState.instance
    PluginState.startup(settings)
    AccountManager.startup()
}
