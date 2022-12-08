package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import com.smallcloud.codify.Resources


/**
 * Supports storing the application settings in a persistent way.
 * The [State] and [Storage] annotations define the name of the data and the file name where
 * these persistent application settings are stored.
 */
@State(name = "com.smallcloud.userSettings.AppSettingsState", storages = [Storage("CodifySettings.xml")])
class AppSettingsState : PersistentStateComponent<AppSettingsState?> {
    var token: String? = null
    var temperature: Float? = null
    var model: String = "CONTRASTcode/3b/py"
    var contrast_url: String? = null
    var userLogged: String? = null
    var ticket: String? = null
    var personalizeAndImprove: Boolean = false

    override fun getState(): AppSettingsState? {
        return this
    }

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        val instance: AppSettingsState
            get() = ApplicationManager.getApplication().getService(AppSettingsState::class.java)
    }
}