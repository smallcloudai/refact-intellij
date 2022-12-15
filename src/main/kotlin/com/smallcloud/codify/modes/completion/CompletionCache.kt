package com.smallcloud.codify.modes.completion


object CompletionCache : LinkedHashMap<String, Completion>() {
    private fun cleanup(maxSize: Int = 160) {
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
            this[newCompletion.originalText] = newCompletion
        }
    }

    fun getCompletion(text: String): Completion? = getOrDefault(text, null)
}
