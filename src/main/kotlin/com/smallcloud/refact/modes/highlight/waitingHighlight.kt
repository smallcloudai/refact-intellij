package com.smallcloud.refact.modes.highlight

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color


fun waitingHighlight(
    editor: Editor,
    startPosition: LogicalPosition,
    isProgress: () -> Boolean
) {

    val yellow = Color(255, 255, 0, (255 * 0.5).toInt())

    var line0 = startPosition.line
    var line1 = startPosition.line
    var line0Ended = false
    var line1Ended = false

    val rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()
    try {
        while (!(line0Ended && line1Ended) && isProgress()) {
            Thread.sleep(100)
            ApplicationManager.getApplication().invokeAndWait {
                rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
                rangeHighlighters.clear()

                if (line0 >= 0) {
                    rangeHighlighters.add(
                        editor.markupModel.addLineHighlighter(line0, 99999,
                            TextAttributes().apply {
                                backgroundColor = yellow
                            })
                    )
                } else line0Ended = true

                if (line1 < editor.document.lineCount) {
                    rangeHighlighters.add(
                        editor.markupModel.addLineHighlighter(line1, 99999,
                            TextAttributes().apply {
                                backgroundColor = yellow
                            })
                    )
                } else line1Ended = true
            }
            line0--
            line1++
        }
    } finally {
        ApplicationManager.getApplication().invokeAndWait {
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
        }
    }
}