package com.smallcloud.refactai.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.struct.DeploymentMode
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCRequestBody
import java.net.URI
import java.util.concurrent.Future
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder
import com.smallcloud.refactai.settings.AppSettingsState.Companion.instance as AppSettingsState

class InferenceGlobalContext : Disposable {
    private val reconnectScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
            "SMCInferenceGlobalContextScheduler", 1
    )
    private var lastTask: Future<*>? = null

    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    var connection: AsyncConnection = AsyncConnection()

    fun checkConnection(uri: URI, needChangeStatus: Boolean = true) {
        try {
            if (needChangeStatus) {
                status = ConnectionStatus.PENDING
            }
            connection.ping(uri)
            if (needChangeStatus) {
                status = ConnectionStatus.CONNECTED
            }
            lastErrorMsg = null
        } catch (e: Exception) {
            if (needChangeStatus) {
                status = ConnectionStatus.DISCONNECTED
            }
            lastErrorMsg = e.message
        }
    }

    fun canRequest(): Boolean {
        return status == ConnectionStatus.CONNECTED
    }

    var status: ConnectionStatus = ConnectionStatus.CONNECTED
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

    var inferenceUri: String?
        get() {
            return AppSettingsState.userInferenceUri
        }
        set(newInferenceUrl) {
            if (newInferenceUrl == inferenceUri) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .userInferenceUriChanged(newInferenceUrl)

            lastTask?.cancel(true)
            lastTask = reconnectScheduler.submit {
//                checkConnection(inferenceUri!!)
//                if (!canRequest()) return@submit

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
            if (newInferenceUrl == AppSettingsState.inferenceUri?.let { URI(it) }) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .inferenceUriChanged(newInferenceUrl)
        }
        get() {
            return AppSettingsState.inferenceUri?.let { URI(it) }
        }
    

    var isNewChatStyle: Boolean = false

    var temperature: Float?
        get() = AppSettingsState.temperature
        set(newTemp) {
            if (newTemp == temperature) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .temperatureChanged(newTemp)
        }

    var developerModeEnabled: Boolean
        get() = AppSettingsState.developerModeEnabled
        set(newValue) {
            if (newValue == developerModeEnabled) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .developerModeEnabledChanged(newValue)
        }

    var stagingVersion: String
        get() = AppSettingsState.stagingVersion
        set(newStr) {
            if (newStr == AppSettingsState.stagingVersion) return
            AppSettingsState.stagingVersion = newStr
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
        get() = AppSettingsState.model
        set(newModel) {
            if (newModel == model) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .modelChanged(newModel)
        }
    var longthinkModel: String?
        get() = AppSettingsState.longthinkModel
        set(newModel) {
            if (newModel == AppSettingsState.longthinkModel) return
            AppSettingsState.longthinkModel = newModel
        }

    var useAutoCompletion: Boolean
        get() = AppSettingsState.useAutoCompletion
        set(newValue) {
            if (newValue == useAutoCompletion) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .useAutoCompletionModeChanged(newValue)
        }

    var useMultipleFilesCompletion: Boolean
        get() = AppSettingsState.useMultipleFilesCompletion
        set(newValue) {
            if (newValue == useMultipleFilesCompletion) return
            messageBus
                    .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                    .useMultipleFilesCompletionChanged(newValue)
        }

    val deploymentMode: DeploymentMode
        get() {
            if (AppSettingsState.userInferenceUri == null) {
                return DeploymentMode.CLOUD
            }

            return when(AppSettingsState.userInferenceUri!!.lowercase()) {
                "hf" -> DeploymentMode.HF
                "refact" -> DeploymentMode.CLOUD
                else -> DeploymentMode.SELF_HOSTED
            }
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
//        if (apiKey.isNullOrEmpty() && isCloud) return null

//        requestData.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
//        requestData.client = "${Resources.client}-${Resources.version}"
        return SMCRequest(LSPProcessHolder.url, requestData, apiKey ?: "self_hosted")
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
