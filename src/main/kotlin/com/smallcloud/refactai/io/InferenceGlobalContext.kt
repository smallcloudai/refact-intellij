package com.smallcloud.refactai.io

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.account.inferenceLogin
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCRequestBody
import java.net.URI

object InferenceGlobalContext {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    private var cloudConnection_: Connection? = null
    private var userConnection_: Connection? = null
    private var cloudInferenceConnection_: AsyncConnection? = null
    private var userInferenceConnection_: AsyncConnection? = null

    init {
        reconnect()
    }

    private fun makeConnection(uri: URI, isCustomUrl: Boolean = false): Connection? {
        return try {
            val conn = Connection(uri, isCustomUrl)
            status = ConnectionStatus.CONNECTED
            lastErrorMsg = null
            conn
        } catch (e: Exception) {
            status = ConnectionStatus.DISCONNECTED
            lastErrorMsg = e.message
            null
        }
    }

    private fun makeAsyncConnection(uri: URI, isCustomUrl: Boolean = false): AsyncConnection? {
        return try {
            val conn = AsyncConnection(uri, isCustomUrl)
            status = ConnectionStatus.CONNECTED
            lastErrorMsg = null
            conn
        } catch (e: Exception) {
            status = ConnectionStatus.DISCONNECTED
            lastErrorMsg = e.message
            null
        }
    }

    fun reconnect() {
        cloudConnection_ = cloudInferenceUri?.let { makeConnection(it) }
        userConnection_ = userInferenceUri?.let { makeConnection(it, true) }
        cloudInferenceConnection_ = cloudInferenceUri?.let { makeAsyncConnection(it) }
        userInferenceConnection_ = userInferenceUri?.let { makeAsyncConnection(it, true) }
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
            reconnect()
            inferenceLogin()
        }

    // cloudInferenceUri is uri from SMC server; must be change only in login method
    var cloudInferenceUri: URI?
        set(newInferenceUrl) {
            if (newInferenceUrl == AppSettingsState.instance.inferenceUri?.let { URI(it) }) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .inferenceUriChanged(newInferenceUrl)
            reconnect()
        }
        get() {
            return AppSettingsState.instance.inferenceUri?.let { URI(it) }
        }

    private val userInferenceUri: URI?
        get() {
            return AppSettingsState.instance.userInferenceUri?.let { URI(it) }
        }

    val inferenceConnection: AsyncConnection?
        get() {
            return if (userInferenceConnection_ == null) cloudInferenceConnection_ else userInferenceConnection_
        }
    val cloudConnection: Connection?
        get() {
            return cloudConnection_
        }
    val userConnection: Connection?
        get() {
            return userConnection_
        }
    val connection: Connection?
        get() {
            return if (userConnection_ == null) cloudConnection_ else userConnection_
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

    var developerModeEnabled: Boolean
        get() = AppSettingsState.instance.developerModeEnabled
        set(newValue) {
            if (newValue == developerModeEnabled) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .developerModeEnabledChanged(newValue)
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
    var longthinkModel: String?
        get() = AppSettingsState.instance.longthinkModel
        set(newModel) {
            if (newModel == AppSettingsState.instance.longthinkModel) return
            AppSettingsState.instance.longthinkModel = newModel
        }

    var useForceCompletion: Boolean
        get() = AppSettingsState.instance.useForceCompletion
        set(newValue) {
            if (newValue == useForceCompletion) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .useForceCompletionModeChanged(newValue)
        }

    var useMultipleFilesCompletion: Boolean
        get() = AppSettingsState.instance.useMultipleFilesCompletion
        set(newValue) {
            if (newValue == useMultipleFilesCompletion) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .useMultipleFilesCompletionChanged(newValue)
        }

    fun makeRequest(
            requestData: SMCRequestBody,
            sendToCloudServer: Boolean
    ): SMCRequest? {
        val apiKey = AccountManager.apiKey
        if (apiKey.isNullOrEmpty()) return null

        requestData.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
        requestData.client = "${Resources.client}-${Resources.version}"
        return if (sendToCloudServer) {
            cloudInferenceUri?.let { SMCRequest(it, requestData, apiKey, sendToCloudServer=sendToCloudServer) }
        } else {
            inferenceUri?.let { SMCRequest(it, requestData, apiKey, sendToCloudServer=sendToCloudServer) }
        }
    }
}