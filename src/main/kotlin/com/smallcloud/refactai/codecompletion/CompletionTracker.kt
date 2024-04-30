package com.smallcloud.refactai.codecompletion

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key

object CompletionTracker {
    private val LAST_COMPLETION_REQUEST_TIME = Key.create<Long>("LAST_COMPLETION_REQUEST_TIME")
    private const val DEBOUNCE_INTERVAL_MS = 500

    fun calcDebounceTime(editor: Editor): Long {
        val lastCompletionTimestamp = LAST_COMPLETION_REQUEST_TIME[editor]
        if (lastCompletionTimestamp != null) {
            val elapsedTimeFromLastEvent = System.currentTimeMillis() - lastCompletionTimestamp
            if (elapsedTimeFromLastEvent < DEBOUNCE_INTERVAL_MS) {
                return DEBOUNCE_INTERVAL_MS - elapsedTimeFromLastEvent
            }
        }
        return 0
    }

    fun updateLastCompletionRequestTime(editor: Editor) {
        val currentTimestamp = System.currentTimeMillis()
        LAST_COMPLETION_REQUEST_TIME[editor] = currentTimestamp
    }
}
