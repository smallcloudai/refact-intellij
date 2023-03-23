package com.smallcloud.refact.modes.diff

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin


fun waitingDiff(
    editor: Editor,
    startPosition: LogicalPosition, finishPosition: LogicalPosition,
    isProgress: () -> Boolean
) {
    val colors: MutableList<Color> = emptyList<Color>().toMutableList()
    for (c in 0 until 20) {
        val phase: Float = c.toFloat() / 10
        colors.add(
            Color(
                maxOf(100, floor(255 * sin(phase * Math.PI + Math.PI)).toInt()),
                maxOf(100, floor(255 * sin(phase * Math.PI + Math.PI / 2)).toInt()),
                maxOf(100, floor(255 * sin(phase * Math.PI + 3 * Math.PI / 2)).toInt()),
                (255 * 0.3).toInt()
            )
        )
    }

    var t = 0
    val rangeHighlighters: MutableList<RangeHighlighter> = mutableListOf()
    try {
        while (isProgress()) {
            Thread.sleep(100)
            ApplicationManager.getApplication().invokeAndWait {
                rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
                rangeHighlighters.clear()
                for (lineNumber in startPosition.line..finishPosition.line) {
                    val localStartOffset = editor.document.getLineStartOffset(lineNumber)
                    val localEndOffset = editor.document.getLineEndOffset(lineNumber)
                    for (c in localStartOffset until localEndOffset step 2) {
                        val a = (lineNumber + c + t) % colors.size
                        rangeHighlighters.add(
                            editor.markupModel.addRangeHighlighter(
                                c, min(c + 2, localEndOffset), 9999,
                                TextAttributes().apply {
                                    backgroundColor = colors[a]
                                },
                                HighlighterTargetArea.EXACT_RANGE
                            )
                        )

                    }
                }
            }

            t++
        }
    } finally {
        ApplicationManager.getApplication().invokeAndWait {
            rangeHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
            rangeHighlighters.clear()
        }
    }
}