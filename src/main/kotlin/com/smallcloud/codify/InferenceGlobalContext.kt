package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.utils.dispatch

object InferenceGlobalContext {
    private var _temperature: Float? = null
    private var _inferenceUrl: String? = null
    private var _model: String? = null

    var inferenceUrl: String?
        get() = _inferenceUrl
        set(newInferenceUrl) {
            if (newInferenceUrl != _inferenceUrl) {
                _inferenceUrl = newInferenceUrl
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .inferenceUrlChanged(_inferenceUrl)
                }
            }
        }
    var temperature: Float?
        get() = _temperature
        set(newTemp) {
            if (newTemp != _temperature) {
                _temperature = newTemp
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .temperatureChanged(_temperature)
                }
            }
        }
    var model: String?
        get() = _model
        set(newModel) {
            if (newModel != _model) {
                _model = newModel
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(InferenceGlobalContextChangedNotifier.TOPIC)
                            .modelChanged(_model)
                }
            }
        }

    fun make_request(request_data: SMCRequestBody): SMCRequest? {
        val api_key = AccountManager.apiKey
        if (api_key.isNullOrEmpty()) return null
        if (inferenceUrl.isNullOrEmpty()) return null

        request_data.model = if (model != null) model!! else Resources.default_model
        request_data.temperature = if (temperature != null) temperature!! else Resources.default_temperature
        request_data.client = "${Resources.client}-${Resources.version}"
        val req = SMCRequest(inferenceUrl!!, request_data, api_key)
        return req
    }

    fun startup(settings: AppSettingsState) {
        _temperature = settings.temperature
        _model = settings.model
        _inferenceUrl = settings.inference_url
    }
}