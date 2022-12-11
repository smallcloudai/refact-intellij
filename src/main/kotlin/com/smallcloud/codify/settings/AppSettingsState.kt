package com.smallcloud.codify.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.smallcloud.codify.ExtraInfoChangedNotifier
import com.smallcloud.codify.InferenceGlobalContextChangedNotifier
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.InferenceGlobalContext
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.struct.PlanType


/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "com.smallcloud.userSettings.AppSettingsState", storages = [Storage("CodifySettings.xml")])
class AppSettingsState : PersistentStateComponent<AppSettingsState?> {
    var apiKey: String? = null
    var temperature: Float? = null
    var model: String? = null
    var user_logged_in: String? = null
    var streamlined_login_ticket: String? = null
    var inference_url: String? = null
    var active_plan: PlanType = PlanType.UNKNOWN
    var login_message: String? = null
    var plugin_is_enabled: Boolean = true

    init {
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                    override fun ticketChanged(newTicket: String?) {
                        streamlined_login_ticket = newTicket
                    }
                    override fun userChanged(newUser: String?) {
                        user_logged_in = newUser
                    }
                    override fun apiKeyChanged(newApiKey: String?) {
                        apiKey = newApiKey
                    }
                })
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC,
                        object : InferenceGlobalContextChangedNotifier {
                    override fun inferenceUrlChanged(newUrl: String?) {
                        inference_url = newUrl
                    }
                })
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
                    override fun loginMessageChanged(newMsg: String?) {
                        login_message = newMsg
                    }
                    override fun pluginEnableChanged(newVal: Boolean) {
                        plugin_is_enabled = newVal
                    }
                })
    }


    override fun getState(): AppSettingsState? {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }
}

fun settings_startup() {
    val settings = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    SMCPlugin.startup(settings)
    InferenceGlobalContext.startup(settings)
    AccountManager.startup(settings)
}