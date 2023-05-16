package com.smallcloud.refactai.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.struct.DeploymentMode
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCRequestBody
import java.net.URI
import java.util.concurrent.Future
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager

class InferenceGlobalContext : Disposable {
    private val reconnectScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCInferenceGlobalContextScheduler", 1
    )
    private var lastTask: Future<*>? = null

    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    var connection: AsyncConnection = AsyncConnection()

    fun checkConnection(uri: URI) {
        try {
            connection.ping(uri)
            status = ConnectionStatus.CONNECTED
            lastErrorMsg = null
        } catch (e: Exception) {
            status = ConnectionStatus.DISCONNECTED
            lastErrorMsg = e.message
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

            lastTask?.cancel(true)
            lastTask = reconnectScheduler.submit {
                ApplicationManager.getApplication().getService(LoginStateService::class.java)
                        .tryToWebsiteLogin(force = true)
                messageBus
                        .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                        .deploymentModeChanged(deploymentMode)
            }
        }

    // cloudInferenceUri is uri from SMC server; must be change only in login method
    var cloudInferenceUri: URI?
        set(newInferenceUrl) {
            if (newInferenceUrl == AppSettingsState.instance.inferenceUri?.let { URI(it) }) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .inferenceUriChanged(newInferenceUrl)
        }
        get() {
            return AppSettingsState.instance.inferenceUri?.let { URI(it) }
        }


    private fun hasUserInferenceUri(): Boolean {
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

    val deploymentMode: DeploymentMode
        get() {
            if (hasUserInferenceUri()) {
                return DeploymentMode.SELF_HOSTED
            }
            return DeploymentMode.CLOUD
        }

    val isCloud: Boolean
        get() {
            return deploymentMode == DeploymentMode.CLOUD
        }
    val isSelfHosted: Boolean
        get() {
            return deploymentMode == DeploymentMode.SELF_HOSTED
        }

    fun makeRequest(requestData: SMCRequestBody): SMCRequest? {
        val apiKey = AccountManager.apiKey
        if (apiKey.isNullOrEmpty() && isCloud) return null

        requestData.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
        requestData.client = "${Resources.client}-${Resources.version}"
        return inferenceUri?.let { SMCRequest(it, requestData, apiKey ?: "self_hosted") }
    }

    override fun dispose() {
        lastTask?.cancel(true)
        reconnectScheduler.shutdown()
    }

    companion object {
        @JvmStatic
        val instance: InferenceGlobalContext
            get() = ApplicationManager.getApplication().getService(InferenceGlobalContext::class.java)
    }
}
