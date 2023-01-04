package com.smallcloud.codify.modes.completion

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.modes.EditorTextHelper
import java.util.regex.Pattern


data class Completion(
    val originalText: String,
    val predictedText: String?,
    val completion: String,
    val currentLinesAreEqual: Boolean,
    val multiline: Boolean,
    val startIndex: Int,
    val endIndex: Int,
    val createdTs: Long,
    val isSingleLineComplete: Boolean
) {
    fun isMakeSense() : Boolean {
        return completion.isNotEmpty()
    }

    fun symbolsBeforeLeftCursorReversed() : String {
        val reversedText = originalText
            .substring(0, startIndex)
            .reversed()
        val idx = reversedText.indexOfFirst { it == '\n' }
        if (idx == -1) return ""
        return reversedText.substring(0, idx)
    }
}

fun String.getChar(index: Int): Char? {
    if (isEmpty()) return null
    return if (index < 0) {
        this[length + index]
    } else {
        this[index]
    }
}

class CompletionStateVsCode(
    private var textHelper: EditorTextHelper
) {
    private val MAX_TEXT_SIZE: Long = 180 * 1024
    private val RIGHT_OF_CURSOR_SPECIAL_CHAR = Pattern.compile("^[:\\s\\t\\n\\r),.\"'\\]]*\$")

    private var cursor: Int = textHelper.offset
    private var multiline: Boolean = false
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
            val leftOfCursor = textHelper.currentLine.substring(0, textHelper.offsetByCurrentLine)
            val rightOfCursor = textHelper.currentLine.substring(textHelper.offsetByCurrentLine)
            val rightOfCursorHasOnlySpecialChars = RIGHT_OF_CURSOR_SPECIAL_CHAR.matcher(rightOfCursor).matches()
            if (!rightOfCursorHasOnlySpecialChars) return@run

            multiline = leftOfCursor.replace(" ", "").replace("\t", "").isEmpty()

            requestedText = textHelper.document.text
            if (requestedText.length > MAX_TEXT_SIZE) return@run

            if (requestedText.isNotEmpty() && requestedText.last() != '\n') {
                requestedText += "\n"
            }
            readyForCompletion = true
        }
    }

    fun difference(predictedText: String): Completion? {
        if (!readyForCompletion) {
            return null
        }

        val requestedTextHead = requestedText.substring(0, cursor)
        val predictedTextHead = predictedText.substring(0, cursor)
        assert(requestedTextHead == predictedTextHead)

        var stopAt = 0
        var endIndex = 0
        var anyDifferent = false
        for (i in -1 downTo -requestedText.length step 1) {
            val reqCh = requestedText.getChar(i)
            val predCh = predictedText.getChar(i)
            if (reqCh == '\n') {
                stopAt = i + 1
            }
            if (reqCh != predCh) {
                anyDifferent = true
                endIndex = i
                break
            }
        }
        stopAt += predictedText.length
        endIndex += predictedText.length

        var fail = !anyDifferent
        var completion = ""
        if (!fail) {
            fail = cursor > stopAt
            if (fail) {
                logger.info("FAIL cursor $cursor < $stopAt")
            }
        }
        if (!fail) {
            completion = predictedText.substring(cursor, stopAt)
            logger.info("SUCCESS: \n$requestedText\n$completion")
        }
        if (!fail && !multiline) {
            completion = completion.replace(Regex("/\\s+\$/"), "")
            logger.info("RTRIM: \n$requestedText\n$completion")
            fail = completion.matches(Regex("/\\n/g"))
        } else if (!fail) {
            completion = completion.replace(Regex("/[ \\t\\n]+\$/"), "")
            logger.info("MLINE RTRIM: \n$requestedText\n$completion")
        }
        if (!fail) {
            fail = completion.trim().isEmpty()
        }

        return if (fail) return null else Completion(
            originalText = requestedText,
            predictedText = predictedText,
            completion = completion,
            currentLinesAreEqual = false,
            multiline = multiline,
            startIndex = cursor,
            endIndex = endIndex,
            isSingleLineComplete = false,
            createdTs = System.currentTimeMillis()
        )
    }
}
