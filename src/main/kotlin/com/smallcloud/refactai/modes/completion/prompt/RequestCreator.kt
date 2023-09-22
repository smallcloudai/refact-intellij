package com.smallcloud.refactai.modes.completion.prompt

import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.SMCCursor
import com.smallcloud.refactai.struct.SMCInputs
import com.smallcloud.refactai.struct.SMCRequest
import com.smallcloud.refactai.struct.SMCRequestBody
import java.net.URI
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

object RequestCreator {
    private const val symbolsBudget: Long = 5_000
    private const val distanceThreshold: Double = 0.75

    fun create(
            fileName: String, text: String,
            line: Int, column: Int,
            stat: UsageStatistic,
            intent: String, functionName: String,
            promptInfo: List<PromptInfo>,
            model: String,
            stream: Boolean = true,
            multiline: Boolean = false
    ): SMCRequest? {
        var inputs = SMCInputs(
                sources = mutableMapOf(fileName to text),
                cursor = SMCCursor(
                        file=fileName,
                        line=line,
                        character=column,
                ),
                multiline=multiline,
        )
//        var currentBudget = symbolsBudget
//        val sources = mutableMapOf(fileName to text)
//        val poi = mutableListOf<POI>()
//        promptInfo
//            .filter { it.fileInfo.isOpened() }
//            .filter { it.distance < distanceThreshold }
//            .sortedByDescending { it.fileInfo.lastEditorShown }
//            .forEach {
//                if ((currentBudget - it.prompt.length) <= 0) return@forEach
//                if (sources.containsKey(it.fileName)) return@forEach
//                sources[it.fileName] = it.text
//                val cursors = it.cursors()
//                poi.add(POI(it.fileName, cursors.first, cursors.second, 1.0 - it.distance))
//                currentBudget -= it.prompt.length
//            }
//        promptInfo
//            .filter { !it.fileInfo.isOpened() }
//            .filter { it.distance < distanceThreshold }
//            .sortedByDescending { it.fileInfo.lastUpdatedTs }
//            .forEach {
//                if ((currentBudget - it.prompt.length) <= 0) return@forEach
//                if (sources.containsKey(it.fileName)) return@forEach
//                sources[it.fileName] = it.text
//                val cursors = it.cursors()
//                poi.add(POI(it.fileName, cursors.first, cursors.second, 1.0 - it.distance))
//                currentBudget -= it.prompt.length
//            }

        val requestBody = SMCRequestBody(
            inputs=inputs,
            stream=stream,
        )

        return InferenceGlobalContext.makeRequest(
            requestBody,
        )?.also {
            it.stat = stat
            it.uri = URI("http://127.0.0.1:8001/v1/code-completion")//it.uri.resolve(Resources.defaultContrastUrlSuffix)
        }
    }
}
