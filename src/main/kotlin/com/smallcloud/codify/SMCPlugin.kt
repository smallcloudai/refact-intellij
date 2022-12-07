package com.smallcloud.codify

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.inline.CompletionModule
import com.smallcloud.codify.io.check_login
import com.smallcloud.codify.io.login
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.ProcessType
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.util.concurrent.TimeUnit


class SMCPlugin {
    private var modules: Map<ProcessType, Module> = mapOf(
            ProcessType.COMPLETION to CompletionModule()
    )

    init {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                {
                    try {
                        check_login()
                    } catch (e: Exception) {
                        Logger.getInstance(SMCPlugin::class.java).warn("check_login exception: $e")
                    }
                },
                0,
                10000,
                TimeUnit.MILLISECONDS
        )
    }

    private val contrast_url: String
        get() {
            return AppSettingsState.instance.contrast_url?: Resources.default_contrast_url
        }


    fun make_request(request_data: SMCRequestBody): SMCRequest? {
        request_data.model = AppSettingsState.instance.model
        request_data.client = "${Resources.client}-${Resources.version}"
        request_data.temperature = AppSettingsState.instance.temperature?: Resources.default_temperature
        val req = AppSettingsState.instance.token?.let { SMCRequest(contrast_url, request_data, it) }
        return req
    }

    fun process(process_type: ProcessType, request_body: SMCRequestBody, editor: Editor) {
        val request = make_request(request_body)

        val module = modules[process_type]
        if (module != null && request != null) {
            module.process(request_body, request, editor)
        }
    }

    companion object {
        var instant = SMCPlugin()
    }
}