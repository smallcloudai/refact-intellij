package com.smallcloud.refactai.modes.completion

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.refactai.modes.EditorTextState
import com.smallcloud.refactai.modes.completion.structs.Completion
import java.util.regex.Pattern
import kotlin.math.abs


class CompletionState(
    private var editorState: EditorTextState,
    private val filterRightFromCursor: Boolean = true,
    private val force: Boolean = false
) {
    private val maxTextSize: Long = 180 * 1024
    private val rightOfCursorSpecialChar = Pattern.compile("^[:\\s\\t\\n\\r),.\"'\\]]*\$")

    var multiline: Boolean = true
    private val logger = Logger.getInstance("CompletionUtils")

    @Suppress("RedundantSetter")
    var readyForCompletion: Boolean = false
        private set(value) {
            field = value
        }
    val stopTokens: List<String>
        get() {
            return if (multiline) listOf("\n\n") else listOf("\n", "\n\n")
        }

    init {
        run {
            if (!force && filterRightFromCursor) {
                val rightOfCursor = editorState.currentLine.substring(editorState.offsetByCurrentLine)
                val rightOfCursorHasOnlySpecialChars = rightOfCursorSpecialChar.matcher(rightOfCursor).matches()
                if (!rightOfCursorHasOnlySpecialChars) {
                    logger.info("There are no special characters in the $rightOfCursor")
                    return@run
                }
            }
            val leftOfCursor = editorState.currentLine.substring(0, editorState.offsetByCurrentLine)
            multiline = leftOfCursor.replace(" ", "").replace("\t", "").isEmpty()
            multiline = multiline || force
            val requestedText = editorState.document.text
            if (!force && requestedText.length > maxTextSize) return@run
            readyForCompletion = true
        }
    }

    fun makeCompletion(
        headIndex: Int,
        completion: String,
        tailIndex: Int,
        finishReason: String
    ): Completion {
        val requestedText = editorState.document.text
        val textCurrentLine = editorState.currentLine

        val lines = completion.split('\n')
        var firstLine = lines.first()

        var headIndexUpdated = headIndex
        var leftSymbolsToRemove = 0
        if (editorState.currentLineIsEmptySymbols() && firstLine.isNotEmpty()) {
            val firstNonNonEmptyIndex = firstLine.indexOfFirst { it !in setOf(' ', '\t') }
            val diff = firstNonNonEmptyIndex - textCurrentLine.length
            firstLine = firstLine.substring(if (diff <= 0) firstNonNonEmptyIndex else diff)
            leftSymbolsToRemove = if (diff < 0) abs(diff) else 0
            headIndexUpdated = headIndex + textCurrentLine.length
        }

        // remove it ASAP after fix in scratchpad
        val addedNewLine = requestedText.last() != '\n'

        val currentMultiline = multiline && lines.size > 1
        val startIndex: Int = minOf(requestedText.length, headIndexUpdated)
        logger.info("Finish reason: $finishReason, firstLine: $firstLine")
        val endIndex = if (finishReason == "ins-stoptoken" && completion.isEmpty() && tailIndex == 0) {
            startIndex
        } else {
            requestedText.length - tailIndex + if (addedNewLine) 1 else 0
        }

        val editedCompletion = if (currentMultiline) {
            firstLine + lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
        } else {
            firstLine
        }
        return Completion(
            originalText = requestedText,
            completion = editedCompletion,
            currentLinesAreEqual = false,
            multiline = currentMultiline,
            startIndex = startIndex,
            firstLineEndIndex = endIndex,
            endIndex = maxOf(endIndex, startIndex),
            createdTs = System.currentTimeMillis(),
            leftSymbolsToRemove = leftSymbolsToRemove,
        )
    }
}
