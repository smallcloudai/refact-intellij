package com.smallcloud.codify.modes.completion

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import java.util.regex.Pattern

object CompletionUtils {
    private val END_OF_LINE_VALID_PATTERN = Pattern.compile("^\\s*[)}\\]\"'`]*\\s*[:{;,]?\\s*$")

    @JvmStatic
    fun isValidDocumentChange(editor: Editor, document: Document, newOffset: Int, previousOffset: Int): Boolean {
        if (newOffset < 0 || previousOffset > newOffset) return false

        // Convert to kotlin
//        let max_tokens = 50;
//        let max_edits = 1;
//        let sources: { [key: string]: string } = {};
//        sources[file_name] = whole_doc;
//        let stop_tokens: string[];
//        if (multiline) {
//            stop_tokens = ["\n\n"];
//        } else {
//            stop_tokens = ["\n", "\n\n"];
//        }
//        let fail = false;
//        let stop_at = cursor;
//        let modif_doc = whole_doc;



        val addedText = document.getText(TextRange(previousOffset, newOffset))
        return isValidMidlinePosition(document, newOffset) &&
               isValidNonEmptyChange(addedText.length, addedText)
    }

    @JvmStatic
    fun isValidMidlinePosition(document: Document, offset: Int): Boolean {
        val lineIndex: Int = document.getLineNumber(offset)
        val lineRange = TextRange.create(document.getLineStartOffset(lineIndex), document.getLineEndOffset(lineIndex))
        val line = document.getText(lineRange)
        val lineSuffix = line.substring(offset - lineRange.startOffset)
        return END_OF_LINE_VALID_PATTERN.matcher(lineSuffix).matches()
    }

    @JvmStatic
    fun isValidNonEmptyChange(replacedTextLength: Int, newText: String): Boolean {
        return replacedTextLength >= 0 && newText != ""
    }
}
