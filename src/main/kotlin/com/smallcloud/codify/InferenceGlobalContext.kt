package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.utils.dispatch

object InferenceGlobalContext {
    var inferenceUrl: String?
        get() = AppSettingsState.instance.inferenceUrl
        set(newInferenceUrl) {
            if (newInferenceUrl != inferenceUrl) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .inferenceUrlChanged(newInferenceUrl)
                }
            }
        }
    var temperature: Float?
        get() = AppSettingsState.instance.temperature
        set(newTemp) {
            if (newTemp != temperature) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .temperatureChanged(newTemp)
                }
            }
        }
    var model: String?
        get() = AppSettingsState.instance.model
        set(newModel) {
            if (newModel != model) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .modelChanged(newModel)
                }
            }
        }

    fun make_request(request_data: SMCRequestBody): SMCRequest? {
        val api_key = AccountManager.apiKey
        if (api_key.isNullOrEmpty()) return null
        if (inferenceUrl.isNullOrEmpty()) return null

        request_data.model = if (model != null) model!! else Resources.defaultModel
        request_data.temperature = if (temperature != null) temperature!! else Resources.defaultTemperature
        request_data.client = "${Resources.client}-${Resources.version}"
        val req = SMCRequest(inferenceUrl!!, request_data, api_key)
        return req
    }
}
