package com.smallcloud.refactai.modes

import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra

object EventAdapter {
    private fun bracketsAdapter(
        beforeText: List<DocumentEventExtra>,
        afterText: List<DocumentEventExtra>
    ): Pair<Boolean, Pair<DocumentEventExtra, DocumentEventExtra>?> {
        if (beforeText.size != 2 || afterText.size != 2) {
            return false to null
        }

        val startAutocompleteStrings = setOf("(", "\"", "{", "[", "'", "\"")
        val endAutocompleteStrings = setOf(")", "\"", "\'", "}", "]", "'''", "\"\"\"")
        val startToStopSymbols = mapOf(
            "(" to setOf(")"), "{" to setOf("}"), "[" to setOf("]"),
            "'" to setOf("'", "'''"), "\"" to setOf("\"", "\"\"\"")
        )

        val firstEventFragment = afterText[beforeText.size - 2].event?.newFragment.toString()
        val secondEventFragment = afterText[beforeText.size - 1].event?.newFragment.toString()

        if (firstEventFragment.isEmpty() || firstEventFragment !in startAutocompleteStrings) {
            return false to null
        }
        if (secondEventFragment.isEmpty() || secondEventFragment !in endAutocompleteStrings) {
            return false to null
        }
        if (secondEventFragment !in startToStopSymbols.getValue(firstEventFragment)) {
            return false to null
        }

        return true to (beforeText.last() to afterText.last().copy(
            offsetCorrection = -1
        ))
    }

    fun eventProcess(beforeText: List<DocumentEventExtra>, afterText: List<DocumentEventExtra>)
            : Pair<DocumentEventExtra?, DocumentEventExtra?> {
        if (beforeText.isNotEmpty() && afterText.isEmpty()) {
            return beforeText.last() to null
        }

        if (afterText.last().force) {
            return beforeText.last() to afterText.last()
        }

        val (succeed, events) = bracketsAdapter(beforeText, afterText)
        if (succeed && events != null) {
            return events
        }

        return beforeText.last() to afterText.last()
    }
}