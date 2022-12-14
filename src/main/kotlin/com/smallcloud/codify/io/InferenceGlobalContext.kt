package com.smallcloud.codify.io

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.MessageBus
import com.smallcloud.codify.Resources
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody

object InferenceGlobalContext {
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    var inferenceUrl: String?
        get() = AppSettingsState.instance.inferenceUrl
        set(newInferenceUrl) {
            if (newInferenceUrl == inferenceUrl) return
            messageBus
                .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                .inferenceUrlChanged(newInferenceUrl)
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
        if (inferenceUrl.isNullOrEmpty()) return null

        requestData.model = if (model != null) model!! else Resources.defaultModel
        requestData.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
        requestData.client = "${Resources.client}-${Resources.version}"
        return SMCRequest(inferenceUrl!!, requestData, apiKey)
    }
}
