package com.smallcloud.codify.modes.completion

import com.smallcloud.codify.modes.completion.structs.Completion
import com.smallcloud.codify.modes.completion.structs.CompletionHash


object CompletionCache : LinkedHashMap<CompletionHash, Completion>() {
    private fun cleanup(maxSize: Int = 1600) {
        if (size < maxSize || size == 0) {
            return
        }
        remove(minByOrNull { it.value.createdTs }?.key)
    }

    fun addCompletion(completion: Completion, maxSize: Int = 1600) {
        cleanup(maxSize)

        for (i in 0 until completion.visualizedCompletion.length) {
            val newCompletion = completion.copy(
                originalText = completion.originalText.substring(0, completion.startIndex) +
                        completion.realCompletion.substring(0, i) +
                        completion.originalText.substring(completion.startIndex),
                visualizedCompletion = completion.visualizedCompletion.substring(i),
                realCompletion = completion.realCompletion.substring(i),
                startIndex = completion.startIndex + i,
                visualizedEndIndex = completion.visualizedEndIndex + i,
                realCompletionIndex = completion.realCompletionIndex + i,
                isFromCache = true
            )
            this[CompletionHash(newCompletion.originalText, newCompletion.startIndex)] = newCompletion
        }

        val beforeLeft = completion.symbolsBeforeLeftCursorReversed()
        for (i in beforeLeft.indices) {
            if (beforeLeft[i] != ' ' && beforeLeft[i] != '\t') {
                break
            }
            val newCompletion = completion.copy(
                originalText = completion.originalText.substring(0, completion.startIndex - i) +
                        completion.originalText.substring(completion.startIndex),
                visualizedCompletion = beforeLeft.substring(0, i).reversed() + completion.visualizedCompletion,
                realCompletion = beforeLeft.substring(0, i).reversed() + completion.realCompletion,
                startIndex = completion.startIndex - i,
                visualizedEndIndex = completion.visualizedEndIndex - i,
                realCompletionIndex = completion.realCompletionIndex - i,
                isFromCache = true
            )
            this[CompletionHash(newCompletion.originalText, newCompletion.startIndex)] = newCompletion
        }
    }

    fun getCompletion(text: String, offset: Int): Completion? = getOrDefault(CompletionHash(text, offset), null)
}
