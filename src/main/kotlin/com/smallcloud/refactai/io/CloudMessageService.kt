package com.smallcloud.refactai.io

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.smallcloud.refactai.Resources.cloudUserMessage
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.PluginState.Companion.instance as PluginState
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

@Service
class CloudMessageService : Disposable {
    init {
        if (InferenceGlobalContext.isCloud && !AccountManager.apiKey.isNullOrEmpty()) {
            updateLoginMessage()
        }
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun userInferenceUriChanged(newUrl: String?) {
                    if (InferenceGlobalContext.isCloud && !AccountManager.apiKey.isNullOrEmpty()) {
                        updateLoginMessage()
                    }
                }
            })
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun apiKeyChanged(newApiKey: String?) {
                    if (InferenceGlobalContext.isCloud && !AccountManager.apiKey.isNullOrEmpty()) {
                        updateLoginMessage()
                    }
                }
            })

    }

    private fun updateLoginMessage() {
        AccountManager.apiKey?.let { apiKey ->
            InferenceGlobalContext.connection.get(cloudUserMessage,
                headers = mapOf("Authorization" to "Bearer $apiKey"),
                dataReceiveEnded = {
                    Gson().fromJson(it, JsonObject::class.java).let { value ->
                        if (value.has("retcode") && value.get("retcode").asString != null) {
                            val retcode = value.get("retcode").asString
                            if (retcode == "OK") {
                                if (value.has("message") && value.get("message").asString != null) {
                                    PluginState.loginMessage = value.get("message").asString
                                }
                            }
                        }
                    }
                }, failedDataReceiveEnded = {
                    InferenceGlobalContext.status = ConnectionStatus.ERROR
                    if (it != null) {
                        InferenceGlobalContext.lastErrorMsg = it.message
                    }
                })
        }

    }

    override fun dispose() {}
}