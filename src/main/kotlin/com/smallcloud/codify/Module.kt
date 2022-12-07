package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody

abstract class Module {
    protected val worker_pool = AppExecutorUtil.getAppExecutorService()

    abstract fun process(request_data: SMCRequestBody, request: SMCRequest, editor: Editor)
}