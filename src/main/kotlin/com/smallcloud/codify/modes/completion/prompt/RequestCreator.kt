package com.smallcloud.codify.modes.completion.prompt

import com.smallcloud.codify.Resources
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.struct.POI
import com.smallcloud.codify.struct.SMCRequest
import com.smallcloud.codify.struct.SMCRequestBody

object RequestCreator {
    private const val symbolsBudget: Long = 5_000
    private const val distanceThreshold: Double = 0.75

    fun create(
        fileName: String, text: String,
        startOffset: Int, endOffset: Int,
        scope: String,
        intent: String, functionName: String,
        promptInfo: List<PromptInfo>,
        model: String,
        stream: Boolean = false,
        sendToCodifyServer: Boolean = false
    ): SMCRequest? {
        var currentBudget = symbolsBudget
        val sources = mutableMapOf(fileName to text)
        val poi = mutableListOf<POI>()
        promptInfo
            .filter { it.fileInfo.isOpened() }
            .filter { it.distance < distanceThreshold }
            .sortedByDescending { it.fileInfo.lastEditorShown }
            .forEach {
                if ((currentBudget - it.prompt.length) <= 0) return@forEach
                if (sources.containsKey(it.fileName)) return@forEach
                sources[it.fileName] = it.text
                val cursors = it.cursors()
                poi.add(POI(it.fileName, cursors.first, cursors.second, 1.0 - it.distance))
                currentBudget -= it.prompt.length
            }
        promptInfo
            .filter { !it.fileInfo.isOpened() }
            .filter { it.distance < distanceThreshold }
            .sortedByDescending { it.fileInfo.lastUpdatedTs }
            .forEach {
                if ((currentBudget - it.prompt.length) <= 0) return@forEach
                if (sources.containsKey(it.fileName)) return@forEach
                sources[it.fileName] = it.text
                val cursors = it.cursors()
                poi.add(POI(it.fileName, cursors.first, cursors.second, 1.0 - it.distance))
                currentBudget -= it.prompt.length
            }

        val requestBody = SMCRequestBody(
            sources = sources,
            intent = intent,
            functionName = functionName,
            cursorFile = fileName,
            cursor0 = startOffset,
            cursor1 = endOffset,
            maxTokens = 50,
            maxEdits = 1,
            stopTokens = listOf("\n\n"),
            stream = stream,
            poi = poi,
            model = model
        )

        return InferenceGlobalContext.makeRequest(
            requestBody,
            sendToCodifyServer
        )?.also {
            it.scope = scope
            it.uri = it.uri.resolve(Resources.defaultContrastUrlSuffix)
        }
    }
}
