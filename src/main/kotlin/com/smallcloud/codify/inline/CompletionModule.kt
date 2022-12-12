package com.smallcloud.codify.inline

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.util.ObjectUtils
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.Module
import com.smallcloud.codify.Resources.default_contrast_url_suffix
import com.smallcloud.codify.io.fetch
import com.smallcloud.codify.notifications.emit_error
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody
import java.util.concurrent.Future

class CompletionModule : Module() {
    private var lastRenderTask: Future<*>? = null
    private var lastFetchAndRenderTask: Future<*>? = null

    override fun process(request_data: SMCRequestBody, request: SMCRequest, editor: Editor) {
        ObjectUtils.doIfNotNull(lastFetchAndRenderTask) { task -> task.cancel(true) }
        ObjectUtils.doIfNotNull(lastRenderTask) { task -> task.cancel(true) }

        val modificationStamp = editor.document.modificationStamp
        val offset = editor.caretModel.offset
        request.url += default_contrast_url_suffix

        lastFetchAndRenderTask = worker_pool
            .submit {
                try {
                    val prediction = fetch(request) ?: return@submit
                    if (prediction.status == null) {
                        Connection.status = ConnectionStatus.ERROR
                        Connection.last_error_msg = "Parameters is not correct"
                        return@submit
                    }
                    ApplicationManager.getApplication()
                            .invokeLater {
                                val invalidStamp = modificationStamp != editor.document.modificationStamp
                                val invalidOffset = offset != editor.caretModel.offset
                                if (invalidStamp || invalidOffset) {
                                    return@invokeLater
                                }

                                val prev = CompletionPreview.instance(
                                        editor, request_data,
                                        prediction, request_data.cursor0
                                )
                                prev.render()
                            }
                } catch (e: Exception) {
                    Connection.status = ConnectionStatus.ERROR
                    Connection.last_error_msg = e.toString()
                }

            }
    }
}