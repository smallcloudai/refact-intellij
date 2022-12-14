package com.smallcloud.codify.modes.completion

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import java.util.regex.Pattern

class EditorTextHelper(editor: Editor) {
    val document: Document
    val lines: List<String>
    val offset: Int
    val currentLineNumber: Int
    val currentLine: String
    val currentLineStartOffset: Int
    val currentLineEndOffset: Int
    val offsetByCurrentLine: Int

    init {
        document = editor.document
        lines = document.text.split("\n")
        assert(lines.size == document.lineCount)
        offset = editor.caretModel.offset
        currentLineNumber = document.getLineNumber(offset)
        currentLine = lines[currentLineNumber]
        currentLineStartOffset = document.getLineStartOffset(currentLineNumber)
        currentLineEndOffset = document.getLineEndOffset(currentLineNumber)
        offsetByCurrentLine = offset - currentLineStartOffset
    }

}

fun String.getChar(index: Int): Char = if (index < 0) {
    this[length + index]
} else {
    this[index]
}

class CompletionState(
    private var textHelper: EditorTextHelper
) {
    private val MAX_TEXT_SIZE: Long = 180 * 1024
    private val RIGHT_OF_CURSOR_SPECIAL_CHAR = Pattern.compile("^[:\\s\\t\\n\\r),.\"'\\]]*\$")

    private var cursor: Int = textHelper.offset
    private var multiline: Boolean = false
    private var requestedText: String = ""
    var readyForCompletion: Boolean = false
        private set(value) {
            field = value
        }
    val stopTokens: List<String>
        get() {
            return if (multiline) listOf("\n") else listOf("\n", "\n\n")
        }

    init {
        run {
            val leftOfCursor = textHelper.currentLine.substring(0, textHelper.offsetByCurrentLine)
            val rightOfCursor = textHelper.currentLine.substring(textHelper.offsetByCurrentLine)
            val rightOfCursorHasOnlySpecialChars = RIGHT_OF_CURSOR_SPECIAL_CHAR.matcher(rightOfCursor).matches()
            if (!rightOfCursorHasOnlySpecialChars) return@run

            multiline = leftOfCursor.replace(Regex("/\\s/g"), "").isEmpty()

            requestedText = textHelper.document.text
            if (requestedText.length > MAX_TEXT_SIZE) return@run

            if (requestedText.isNotEmpty() && requestedText.last() != '\n') {
                requestedText += "\n";
            }
            readyForCompletion = true

            var textLeft = requestedText.substring(0, cursor)
            var deletedSpacesLeft = 0
            while (
                multiline && textLeft.isNotEmpty()
                && (textLeft[textLeft.length - 1] == ' ' || textLeft[textLeft.length - 1] == '\t')
            ) {
                textLeft = textLeft.substring(0, textLeft.length - 1)
                cursor -= 1
                deletedSpacesLeft += 1
            }
            requestedText = textLeft + requestedText.substring(cursor)
        }
    }

    fun difference(predictedText: String): Pair<String, Int>? {
        if (!readyForCompletion) {
            return null
        }

        val requestedTextHead = requestedText.substring(0, cursor)
        val predictedTextHead = predictedText.substring(0, cursor)
        assert(requestedTextHead == predictedTextHead)

        var stopAt = 0
        var anyDifferent = false
        for (i in -1 downTo -requestedText.length step 1) {
            val reqCh = requestedText.getChar(i)
            val predCh = predictedText.getChar(i)
            if (reqCh == '\n') {
                stopAt = i + 1
            }
            if (reqCh != predCh) {
                anyDifferent = true
                break
            }
        }
        stopAt += predictedText.length

        var fail = !anyDifferent
        var completion = ""
        if (!fail) {
            fail = cursor >= stopAt
        }
        if (!fail) {
            completion = predictedText.substring(cursor, stopAt)
        }
        if (!fail && !multiline) {
            completion = completion.replace(Regex("/\\s+\$/"), "")
            fail = completion.matches(Regex("/\\n/g"))
        } else if (!fail) {
            completion = completion.replace(Regex("/[ \\t\\n]+\$/"), "");
        }
        if (!fail) {
            fail = completion.isEmpty()
        }

        return completion to stopAt
    }
}
