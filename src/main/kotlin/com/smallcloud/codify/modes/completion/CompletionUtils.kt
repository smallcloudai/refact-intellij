package com.smallcloud.codify.modes.completion

import com.intellij.openapi.diagnostic.Logger
import com.smallcloud.codify.modes.EditorTextHelper
import java.util.regex.Pattern


class CompletionState(
    private var textHelper: EditorTextHelper,
    private val filterRightFromCursor: Boolean = false
) {
    private val MAX_TEXT_SIZE: Long = 180 * 1024
    private val RIGHT_OF_CURSOR_SPECIAL_CHAR = Pattern.compile("^[:\\s\\t\\n\\r),.\"'\\]]*\$")

    private var multiline: Boolean = true
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
            if (filterRightFromCursor) {
                val rightOfCursor = textHelper.currentLine.substring(textHelper.offsetByCurrentLine)
                val rightOfCursorHasOnlySpecialChars = RIGHT_OF_CURSOR_SPECIAL_CHAR.matcher(rightOfCursor).matches()
                if (!rightOfCursorHasOnlySpecialChars) {
                    logger.info("There are no special characters in the $rightOfCursor")
                    return@run
                }
            }
            val leftOfCursor = textHelper.currentLine.substring(0, textHelper.offsetByCurrentLine)
            multiline = leftOfCursor.replace(" ", "").replace("\t", "").isEmpty()
            requestedText = textHelper.document.text
            if (requestedText.length > MAX_TEXT_SIZE) return@run
            readyForCompletion = true
        }
    }

    fun difference(predictedText: String): Completion? {
        if (!readyForCompletion) {
            return null
        }

        val currentLineNum = textHelper.currentLineNumber
        val lines = textHelper.lines
        val predictedLines = predictedText.split('\n')
        val currentLine = textHelper.currentLine
        val predictedCurrentLine = predictedLines[currentLineNum]
        val currentLinesAreEqual = currentLine == predictedCurrentLine
        val hasChangesBeforeCursor = (
                requestedText.substring(0, textHelper.currentLineStartOffset) != predictedText.substring(
                    0,
                    textHelper.currentLineStartOffset
                ))
        val (linesOffset, predictedLinesOffset) = getMultilineOffsets(currentLineNum, lines, predictedLines)
        val diffLikeCompletion = false  // predictedLinesOffset <= linesOffset
        val deletionOnCurrentLine = textHelper.currentLineStartOffset + predictedCurrentLine.length < textHelper.offset
        multiline = multiline && !diffLikeCompletion && (predictedLinesOffset - currentLineNum > 1)
        if (hasChangesBeforeCursor) {
            logger.info("No valid completion: hasChangesBeforeCursor")
            return null
        }
        if (currentLinesAreEqual && !multiline) {
            logger.info("No completion: currentLinesAreEqual && !multiline")
            return null
        }
        if (deletionOnCurrentLine) {
            logger.info("No completion: deletionOnCurrentLine")
            return null
        }

        val startIndex = textHelper.offset
        var stopIndex = textHelper.offset
        var completion = ""
        if (!currentLinesAreEqual) {
            stopIndex += requestedText.substring(
                startIndex,
                textHelper.currentLineStartOffset + currentLine.length
            ).length

            completion += predictedText.substring(
                startIndex,
                textHelper.currentLineStartOffset + predictedCurrentLine.length
            )
        }

        if (!multiline) {
            logger.info("Single line completion: $completion")
            return Completion(
                originalText = requestedText,
                predictedText = predictedText,
                completion = completion,
                multiline = multiline,
                startIndex = startIndex,
                endIndex = stopIndex,
                createdTs = System.currentTimeMillis()
            )
        } else {
            stopIndex += lines.subList(
                minOf(currentLineNum + 1, linesOffset),
                linesOffset
            ).joinToString("\n").length
            completion += '\n'
            completion += predictedLines.subList(
                minOf(currentLineNum + 1, predictedLinesOffset),
                predictedLinesOffset
            ).joinToString("\n")
            logger.info("Multi line completion: $completion")
            return Completion(
                originalText = requestedText,
                predictedText = predictedText,
                completion = completion,
                multiline = multiline,
                startIndex = startIndex,
                endIndex = stopIndex,
                createdTs = System.currentTimeMillis()
            )
        }
    }

    private fun getMultilineOffsets(
        currentLineNum: Int,
        lines: List<String>,
        predictedLines: List<String>
    ): Pair<Int, Int> {
        val guesses: MutableList<Pair<Int, Int>> = mutableListOf()
        for (i in currentLineNum + 1 until lines.size) {
            if (lines[i].isEmpty()) {
                continue
            }
            var predictedLinesOffset = -1
            for (j in currentLineNum + 1 until predictedLines.size) {
                if (lines[i] == predictedLines[j] || predictedLines[j].contains(lines[i])) {
                    predictedLinesOffset = j
                    break
                }
            }
            if (predictedLinesOffset != -1) {
                guesses.add(i to predictedLinesOffset)
            }
        }

        val minEl = guesses.minByOrNull { it.first + it.second }
        var (linesOffset, predictedLinesOffset) = -1 to -1
        if (minEl != null) {
            linesOffset = minEl.first
            predictedLinesOffset = minEl.second
        }

        if (predictedLinesOffset == -1
            && lines.subList(currentLineNum + 1, lines.size).none { it.isNotEmpty() }
            && predictedLines.subList(currentLineNum + 1, predictedLines.size).any { it.isNotEmpty() }
        ) {
            linesOffset = lines.size - 1
            predictedLinesOffset = predictedLines.size - 1
        }

        if (linesOffset != -1 && predictedLinesOffset != -1) {
            val predictedOffset = predictedLinesOffset - linesOffset
            for (i in linesOffset - 1 downTo 0) {
                if (lines[i] == predictedLines[i + predictedOffset] && lines[i].isEmpty()) {
                    linesOffset -= 1
                    predictedLinesOffset -= 1
                } else {
                    break
                }
            }
        }

        return linesOffset to predictedLinesOffset
    }
}
