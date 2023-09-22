package com.smallcloud.refactai.struct

import com.smallcloud.refactai.statistic.UsageStatistic
import java.net.URI


data class SMCCursor(
    val file: String = "",
    val line: Int = 0,
    val character: Int = 0
)
data class SMCInputs(
    var sources: Map<String, String> = mapOf(),
    val cursor: SMCCursor = SMCCursor(),
    val multiline: Boolean = true

)

data class SMCRequestBody(
    var inputs: SMCInputs = SMCInputs(),
    var stream: Boolean = true,
    var parameters: Map<String, Any> = mapOf(
            "temperature" to 0.1,
            "max_new_tokens" to 20
    ),
)

data class SMCRequest(
        var uri: URI,
        var body: SMCRequestBody,
        var token: String,
        var stat: UsageStatistic = UsageStatistic(),
)
