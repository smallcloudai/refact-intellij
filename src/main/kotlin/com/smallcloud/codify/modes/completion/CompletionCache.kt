package com.smallcloud.codify.modes.completion


data class CompletionHash(
    val text: String,
    val offset: Int,
)


object CompletionCache : LinkedHashMap<CompletionHash, Completion>() {
    private fun cleanup(maxSize: Int = 1600) {
        if (size < maxSize || size == 0) {
            return
        }
        remove(minByOrNull { it.value.createdTs }?.key)
    }

    fun addCompletion(completion: Completion, maxSize: Int = 1600) {
        cleanup(maxSize)

        for (i in 0 until completion.completion.length) {
            val newCompletion = completion.copy(
                originalText = completion.originalText.substring(0, completion.startIndex) +
                        completion.completion.substring(0, i) +
                        completion.originalText.substring(completion.startIndex),
                predictedText = completion.predictedText.substring(0, completion.startIndex) +
                        completion.predictedText.substring(completion.startIndex + i),
                completion = completion.completion.substring(i),
                startIndex = completion.startIndex + i,
                endIndex = completion.endIndex + i,
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
                predictedText = completion.predictedText.substring(0, completion.startIndex - i) +
                        completion.originalText.substring(completion.startIndex - i, completion.startIndex) +
                        completion.predictedText.substring(completion.startIndex + i),
                completion = beforeLeft.substring(0, i).reversed() + completion.completion,
                startIndex = completion.startIndex - i,
                endIndex = completion.endIndex - i,
                isFromCache = true
            )
            this[CompletionHash(newCompletion.originalText, newCompletion.startIndex)] = newCompletion
        }
    }

    fun getCompletion(text: String, offset: Int): Completion? = getOrDefault(CompletionHash(text, offset), null)
}
