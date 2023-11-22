package com.smallcloud.refactai.modes.completion.prompt
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.struct.*
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.instance as LSPProcessHolder

@Deprecated("Will be removed in next release")
object RequestCreatorOld {
    private const val symbolsBudget: Long = 5_000
    private const val distanceThreshold: Double = 0.75

    fun create(
        fileName: String, text: String,
        startOffset: Int, endOffset: Int,
        stat: UsageStatistic,
        intent: String, functionName: String,
        promptInfo: List<PromptInfo>,
        model: String,
        stream: Boolean = true,
    ): SMCRequestOld? {
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

        val requestBody = SMCRequestBodyOld(
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

        return InferenceGlobalContext.makeRequestOld(
            requestBody,
        )?.also {
            it.stat = stat
            it.uri = it.uri.resolve(Resources.defaultContrastUrlSuffix)
        }
    }
}

object RequestCreator {
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
        val inputs = SMCInputs(
                sources = mutableMapOf(fileName to text),
                cursor = SMCCursor(
                        file=fileName,
                        line=line,
                        character=column,
                ),
                multiline=multiline,
        )

        val requestBody = SMCRequestBody(
            inputs=inputs,
            stream=stream,
        )

        return InferenceGlobalContext.makeRequest(
            requestBody,
        )?.also {
            it.stat = stat
            it.uri = LSPProcessHolder.url.resolve(Resources.defaultCodeCompletionUrlSuffix)
        }
    }
}
