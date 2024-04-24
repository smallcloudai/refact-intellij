package com.smallcloud.refactai.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.Transient
import com.smallcloud.refactai.ExtraInfoChangedNotifier
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager

/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */

@State(name = "com.smallcloud.userSettings.AppSettingsState", storages = [
    Storage("CodifySettings.xml", deprecated = true),
    Storage("SMCSettings.xml"),
])
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var apiKey: String? = null
    var temperature: Float? = null
    var model: String? = null
    var userLoggedIn: String? = null
    var streamlinedLoginTicket: String? = null
    var streamlinedLoginTicketWasCreatedTs: Long? = null
    var inferenceUri: String? = null
    var userInferenceUri: String? = null
    var loginMessage: String? = null
    var tooltipMessage: String? = null
    var inferenceMessage: String? = null
    var useAutoCompletion: Boolean = true
    var startupLoggedIn: Boolean = false
    var developerModeEnabled: Boolean = false
    var xDebugLSPPort: Int? = null
    var stagingVersion: String = ""
    var rateUsNotification: Boolean = false
    var defaultSystemPrompt: String = ""
    var astIsEnabled: Boolean = false
    var vecdbIsEnabled: Boolean = false

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
            })
        messageBus
            .connect(PluginState.instance)
            .subscribe(
                InferenceGlobalContextChangedNotifier.TOPIC,
                object : InferenceGlobalContextChangedNotifier {
                    override fun inferenceUriChanged(newUrl: URI?) {
                        instance.inferenceUri = newUrl?.toString()
                    }

                    override fun userInferenceUriChanged(newUrl: String?) {
                        instance.userInferenceUri = newUrl
                    }

                    override fun modelChanged(newModel: String?) {
                        instance.model = newModel?.trim()
                    }

                    override fun temperatureChanged(newTemp: Float?) {
                        instance.temperature = newTemp
                    }

                    override fun useAutoCompletionModeChanged(newValue: Boolean) {
                        instance.useAutoCompletion = newValue
                    }
                    override fun developerModeEnabledChanged(newValue: Boolean) {
                        instance.developerModeEnabled = newValue
                    }

                    override fun astFlagChanged(newValue: Boolean) {
                        instance.astIsEnabled = newValue
                    }
                    override fun vecdbFlagChanged(newValue: Boolean) {
                        instance.vecdbIsEnabled = newValue
                    }
                    override fun xDebugLSPPortChanged(newPort: Int?) {
                        instance.xDebugLSPPort = newPort
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

        val acceptedCompletionCounter = AtomicInteger(0)
    }
}

fun settingsStartup() {
    val settings = AppSettingsState.instance
    PluginState.startup(settings)
    AccountManager.startup()
    PrivacyState.instance
}
