package com.smallcloud.codify.modes.completion

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.modes.EditorTextState
import com.smallcloud.codify.modes.completion.structs.Completion
import com.smallcloud.codify.modes.completion.structs.getChar
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
        tailIndex: Int
    ): Completion {
        val requestedText = editorState.document.text
        var textCurrentLine = editorState.currentLine

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

        multiline = multiline && lines.size > 1
        val startIndex: Int = minOf(requestedText.length, headIndexUpdated)
        var endIndex = if (multiline) {
            val firstEosIndex = requestedText.substring(startIndex).indexOfFirst { it == '\n' }
            minOf(requestedText.length, startIndex + (if (firstEosIndex == -1) 0 else firstEosIndex))
        } else {
            startIndex
        }

        var offset = 0
        if (multiline) {
            val editorCurrentLineWithOffset = if (editorState.offsetByCurrentLine - 1 > 1)
                editorState.currentLine.substring(editorState.offsetByCurrentLine - 1)
            else editorState.currentLine
            for (i in -1 downTo -firstLine.length) {
                if (editorCurrentLineWithOffset.length <= -i) {
                    break
                }
                val curCh = editorCurrentLineWithOffset.getChar(i)
                val compCh = firstLine.getChar(i)
                if (curCh != compCh) {
                    break
                }
                offset += 1
            }
        }
        val firstLineEndIndex = endIndex - offset
        firstLine = firstLine.substring(0, firstLine.length - offset)
        val editedCompletion = if (multiline) {
            firstLine + lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
        } else {
            firstLine
        }
        return Completion(
            originalText = requestedText,
            completion = editedCompletion,
            currentLinesAreEqual = false,
            multiline = multiline,
            startIndex = startIndex,
            firstLineEndIndex = firstLineEndIndex,
            endIndex = maxOf(endIndex, startIndex),
            createdTs = System.currentTimeMillis(),
            leftSymbolsToRemove = leftSymbolsToRemove,
            isSingleLineComplete = !multiline
        )
    }
}
