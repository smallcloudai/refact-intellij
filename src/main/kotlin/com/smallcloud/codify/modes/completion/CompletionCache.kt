package com.smallcloud.codify.modes.completion


data class CompletionCacheInfo(
    val completion: String,
    val createdTs: Long
) {
    companion object {
        val completionCache = LinkedHashMap<String, CompletionCacheInfo>()
    }
}

fun LinkedHashMap<String, CompletionCacheInfo>.cleanup(maxSize: Int = 160) {
    if (size < maxSize || size == 0) {
        return
    }
    remove(minByOrNull { it.value.createdTs }?.key)
}
