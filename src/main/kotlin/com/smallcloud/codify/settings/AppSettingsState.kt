package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.SettingsCategory
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.smallcloud.codify.ExtraInfoChangedNotifier
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.InferenceGlobalContextChangedNotifier
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.struct.PlanType


/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "com.smallcloud.userSettings.AppSettingsState", storages = [Storage("CodifySettings.xml")])
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    var apiKey: String? = null
    var temperature: Float? = null
    var model: String? = null
    var userLoggedIn: String? = null
    var streamlinedLoginTicket: String? = null
    var inferenceUrl: String? = null
    var activePlan: PlanType = PlanType.UNKNOWN
    var loginMessage: String? = null
    var pluginIsEnabled: Boolean = true
    init {
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
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
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC,
                        object : InferenceGlobalContextChangedNotifier {
                    override fun inferenceUrlChanged(newUrl: String?) {
                        instance.inferenceUrl = newUrl
                    }
                })
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
                    override fun loginMessageChanged(newMsg: String?) {
                        instance.loginMessage = newMsg
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

fun settings_startup() {
    val settings = AppSettingsState.instance
    SMCPlugin.startup(settings)
    AccountManager.startup(settings)
}