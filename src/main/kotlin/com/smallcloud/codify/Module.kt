package com.smallcloud.codify

import com.intellij.openapi.editor.Editor
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody

abstract class Module {
    protected val workerPool = AppExecutorUtil.getAppExecutorService()

    abstract fun process(requestData: SMCRequestBody, request: SMCRequest, editor: Editor)
}
