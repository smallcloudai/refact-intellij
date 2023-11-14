package com.smallcloud.refactai.struct

import com.google.gson.annotations.SerializedName
import com.smallcloud.refactai.statistic.UsageStatistic
import java.net.URI
import java.util.concurrent.ThreadLocalRandom
import kotlin.streams.asSequence

private val charPool : List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
private fun uuid() = ThreadLocalRandom.current()
    .ints(8.toLong(), 0, charPool.size)
    .asSequence()
    .map(charPool::get)
    .joinToString("")

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

data class SMCParameters(
    var temperature: Float = 0.2f,
    @SerializedName("max_new_tokens") var maxNewTokens: Int = 20
)

data class SMCRequestBody(
    var inputs: SMCInputs = SMCInputs(),
    var stream: Boolean = true,
    var parameters: SMCParameters = SMCParameters(),
    @SerializedName("no_cache") var noCache: Boolean = false
)

data class SMCRequest(
    var uri: URI,
    var body: SMCRequestBody,
    var token: String,
    var id: String = uuid(),
    var stat: UsageStatistic = UsageStatistic(),
)
