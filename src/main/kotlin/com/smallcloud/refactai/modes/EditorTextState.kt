package com.smallcloud.refactai.modes

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

class EditorTextState(
    val editor: Editor,
    val modificationStamp: Long,
    var offset: Int
) {
    var text: String
    val document: Document
    val lines: List<String>
    val currentLineNumber: Int
    val currentLine: String
    val currentLineStartOffset: Int
    val currentLineEndOffset: Int
    val offsetByCurrentLine: Int
    private val initialOffset: Int

    init {
        text = editor.document.text
        document = editor.document
        lines = document.text.split("\n", "\r\n")
        currentLineNumber = document.getLineNumber(offset)
        currentLine = lines[currentLineNumber]
        currentLineStartOffset = document.getLineStartOffset(currentLineNumber)
        currentLineEndOffset = document.getLineEndOffset(currentLineNumber)
        offsetByCurrentLine = offset - currentLineStartOffset
        initialOffset = offset
    }

    fun currentLineIsEmptySymbols(): Boolean {
        if (currentLine.isEmpty()) return false
        return currentLine.substring(offsetByCurrentLine).isEmpty() &&
                currentLine.substring(0, offsetByCurrentLine)
                    .replace("\t", "")
                    .replace(" ", "").isEmpty()
    }

    fun getRidOfLeftSpacesInplace() {
        if (!currentLineIsEmptySymbols()) return

        val before = if (currentLineNumber == 0)
            "" else lines.subList(0, currentLineNumber).joinToString("\n", postfix = "\n")
        val after = if (currentLineNumber == lines.size - 1)
            "" else lines.subList(currentLineNumber + 1, lines.size).joinToString("\n", prefix = "\n")
        text = before + after
        offset = before.length
    }

    fun restoreInplace() {
        if (!currentLineIsEmptySymbols()) return
        if (offset == initialOffset) return

        text = editor.document.text
        offset = initialOffset
    }

    fun isValid(): Boolean {
        return lines.size == document.lineCount
    }
}
