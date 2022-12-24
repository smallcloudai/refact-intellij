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

    fun addCompletion(completion: Completion, maxSize: Int = 160) {
        cleanup(maxSize)

        for (i in 0 until completion.completion.length) {
            val newCompletion = completion.copy(
                originalText = completion.originalText.substring(0, completion.startIndex) +
                        completion.completion.substring(0, i) +
                        completion.originalText.substring(completion.startIndex),
                predictedText = null,
                completion = completion.completion.substring(i),
                startIndex = completion.startIndex + i,
                endIndex = completion.endIndex + i,
            )
            this[CompletionHash(newCompletion.originalText, newCompletion.startIndex)] = newCompletion
        }
    }

    fun getCompletion(text: String, offset: Int): Completion? = getOrDefault(CompletionHash(text, offset), null)
}
