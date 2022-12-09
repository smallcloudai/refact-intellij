package com.smallcloud.codify

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.inline.CompletionModule
import com.smallcloud.codify.struct.ProcessType
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody


class SMCPlugin: Disposable {
    private var modules: Map<ProcessType, Module> = mapOf(
            ProcessType.COMPLETION to CompletionModule()
    )

    fun process(process_type: ProcessType, request_body: SMCRequestBody, editor: Editor) {
        val request = InferenceGlobalContext.make_request(request_body)

        val module = modules[process_type]
        if (module != null && request != null) {
            module.process(request_body, request, editor)
        }
    }

    companion object {
        var instant = SMCPlugin()
        fun startup() {

        }
    }

    override fun dispose() {
    }
}