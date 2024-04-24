package com.smallcloud.refactai.modes.completion.prompt

import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.SMCCursor
import com.smallcloud.refactai.struct.SMCInputs
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCRequestBody
import java.net.URI
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

object RequestCreator {
    fun create(
        fileName: String, text: String,
        line: Int, column: Int,
        stat: UsageStatistic,
        intent: String, functionName: String,
        promptInfo: List<PromptInfo>,
        baseUrl: URI,
        model: String? = null,
        useAst: Boolean = false,
        stream: Boolean = true,
        multiline: Boolean = false
    ): SMCRequest? {
        val inputs = SMCInputs(
            sources = mutableMapOf(fileName to text),
            cursor = SMCCursor(
                file = fileName,
                line = line,
                character = column,
            ),
            multiline = multiline,
        )

        val requestBody = SMCRequestBody(
            inputs = inputs,
            stream = stream,
            model = model,
            useAst = useAst
        )

        return InferenceGlobalContext.makeRequest(
            requestBody,
        )?.also {
            it.stat = stat
            it.uri = baseUrl.resolve(Resources.defaultCodeCompletionUrlSuffix)
        }
    }
}
