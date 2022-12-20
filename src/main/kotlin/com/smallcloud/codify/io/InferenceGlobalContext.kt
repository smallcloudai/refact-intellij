package com.smallcloud.codify.io

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.smallcloud.codify.Resources
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.net.URI

object InferenceGlobalContext {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    var connection: Connection? = inferenceUri?.let { Connection(it) }

    var inferenceUri: URI?
        get() = AppSettingsState.instance.inferenceUri?.let { URI(it) }
        set(newInferenceUrl) {
            if (newInferenceUrl == inferenceUri) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .inferenceUriChanged(newInferenceUrl)
            connection = if (newInferenceUrl != null)
                Connection(newInferenceUrl)
            else
                null
        }
    var temperature: Float?
        get() = AppSettingsState.instance.temperature
        set(newTemp) {
            if (newTemp == temperature) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .temperatureChanged(newTemp)
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
