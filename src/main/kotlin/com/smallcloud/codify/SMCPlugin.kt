package com.smallcloud.codify

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

    private val contrast_url: String
        get() {
            return AppSettingsState.instance.contrast_url
        }

    init {
        AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                {
                    check_login()
                },
                0,
                10000,
                TimeUnit.MILLISECONDS
        )
    }


    fun make_request(request_data: SMCRequestBody): SMCRequest {
        request_data.model = AppSettingsState.instance.model
        request_data.client = "${Resources.client}-${Resources.version}"
        request_data.temperature = AppSettingsState.instance.temperature
        val req = SMCRequest(contrast_url, request_data, AppSettingsState.instance.token)
        return req
    }

    fun process(process_type: ProcessType, request_body: SMCRequestBody, editor: Editor) {
        val request = make_request(request_body)

        val module = modules[process_type]
        if (module != null) {
            module.process(request_body, request, editor)
        }
    }

    companion object {
        var instant = SMCPlugin()
    }
}