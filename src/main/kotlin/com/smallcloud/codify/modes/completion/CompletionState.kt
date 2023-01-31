package com.smallcloud.codify.modes.completion

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.modes.EditorTextState
import com.smallcloud.codify.modes.completion.structs.Completion
import com.smallcloud.codify.modes.completion.structs.getChar
import java.util.regex.Pattern


class CompletionState(
    private var editorState: EditorTextState,
    private val filterRightFromCursor: Boolean = true,
    private val force: Boolean = false
) {
    private val maxTextSize: Long = 180 * 1024
    private val rightOfCursorSpecialChar = Pattern.compile("^[:\\s\\t\\n\\r),.\"'\\]]*\$")

    var multiline: Boolean = true
    private var requestedText: String = ""
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
            requestedText = editorState.document.text
            if (!force && requestedText.length > maxTextSize) return@run
            readyForCompletion = true
        }
    }

    fun makeCompletion(
        headIndex: Int,
        completion: String,
        tailIndex: Int
    ): Completion {
        val startIndex: Int
        var endIndex: Int
        if (editorState.offset < requestedText.length && requestedText[editorState.offset] != '\n') {
            startIndex = minOf(requestedText.length, headIndex)
            val firstEosIndex = requestedText.substring(startIndex).indexOfFirst { it == '\n' }
            endIndex = startIndex + (if (firstEosIndex == -1) 0 else firstEosIndex)
            endIndex = minOf(requestedText.length, endIndex)
        } else {
            startIndex = minOf(requestedText.length, editorState.offset)
            endIndex = startIndex
        }

        val lines = completion.split('\n')
        val hasMultiline = lines.size > 1
        var firstLine = lines.first()
        var offset = 0
        val editorCurrentLineWithOffset = editorState.currentLine.substring(editorState.offsetByCurrentLine - 1)
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
        val visualizedEndIndex = endIndex - offset
        firstLine = firstLine.substring(0, firstLine.length - offset)
        val visualizedCompletion = if (hasMultiline) {
            firstLine + lines.subList(1, lines.size).joinToString("\n", prefix = "\n")
        } else {
            firstLine
        }
        return Completion(
            originalText = requestedText,
            visualizedCompletion = visualizedCompletion,
            realCompletion = completion,
            currentLinesAreEqual = false,
            multiline = completion.count { it == '\n' } > 0,
            startIndex = startIndex,
            visualizedEndIndex = maxOf(visualizedEndIndex, startIndex),
            realCompletionIndex = maxOf(endIndex, startIndex),
            createdTs = System.currentTimeMillis(),
            isSingleLineComplete = completion.count { it == '\n' } == 0
        )
    }
}
