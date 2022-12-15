package com.smallcloud.codify.modes

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor

class EditorTextHelper(
    editor: Editor,
    customOffset: Int
) {
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
        offset = customOffset
        currentLineNumber = document.getLineNumber(offset)
        currentLine = lines[currentLineNumber]
        currentLineStartOffset = document.getLineStartOffset(currentLineNumber)
        currentLineEndOffset = document.getLineEndOffset(currentLineNumber)
        offsetByCurrentLine = offset - currentLineStartOffset
    }
}
