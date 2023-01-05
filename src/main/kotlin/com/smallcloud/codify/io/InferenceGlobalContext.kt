package com.smallcloud.codify.io

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.smallcloud.codify.Resources
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.inferenceLogin
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.net.URI

object InferenceGlobalContext {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    var connection: Connection? = inferenceUri?.let { makeConnection(it) }

    private fun makeConnection(uri: URI): Connection? {
        return try {
            val conn = Connection(uri)
            status = ConnectionStatus.CONNECTED
            lastErrorMsg = null
            conn
        } catch (e: Exception) {
            status = ConnectionStatus.DISCONNECTED
            lastErrorMsg = e.message
            null
        }
    }

    var status: ConnectionStatus = ConnectionStatus.DISCONNECTED
        set(newStatus) {
            if (field == newStatus) return
            field = newStatus
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(ConnectionChangedNotifier.TOPIC)
                .statusChanged(field)
        }
    var lastErrorMsg: String? = null
        set(newMsg) {
            if (field == newMsg) return
            field = newMsg
            ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(ConnectionChangedNotifier.TOPIC)
                .lastErrorMsgChanged(field)
        }

    var inferenceUri: URI?
        get() {
            if (AppSettingsState.instance.userInferenceUri != null)
                return AppSettingsState.instance.userInferenceUri?.let { URI(it) }
            return AppSettingsState.instance.inferenceUri?.let { URI(it) }
        }
        set(newInferenceUrl) {
            if (newInferenceUrl == inferenceUri) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .userInferenceUriChanged(newInferenceUrl)
            connection = inferenceUri?.let { makeConnection(it) }
            inferenceLogin()
        }

    // _inferenceUri is uri from SMC server; must be change only in login method
    var serverInferenceUri: URI? = null
        set(newInferenceUrl) {
            if (newInferenceUrl == AppSettingsState.instance.inferenceUri?.let { URI(it) }) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .inferenceUriChanged(newInferenceUrl)
            connection = inferenceUri?.let { Connection(it) }
        }

    fun hasUserInferenceUri(): Boolean {
        return AppSettingsState.instance.userInferenceUri != null
    }

    var temperature: Float?
        get() = AppSettingsState.instance.temperature
        set(newTemp) {
            if (newTemp == temperature) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .temperatureChanged(newTemp)
        }

    var lastAutoModel: String? = null
        set(newModel) {
            if (newModel == field) return
            field = newModel
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .lastAutoModelChanged(newModel)
        }

    var model: String?
        get() = AppSettingsState.instance.model
        set(newModel) {
            if (newModel == model) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .modelChanged(newModel)
        }

    fun makeRequest(requestData: SMCRequestBody): SMCRequest? {
        val apiKey = AccountManager.apiKey
        if (apiKey.isNullOrEmpty()) return null

        requestData.model = if (model != null) model!! else Resources.defaultModel
        requestData.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
        requestData.client = "${Resources.client}-${Resources.version}"
        return inferenceUri?.let { SMCRequest(it, requestData, apiKey) }
    }
}
