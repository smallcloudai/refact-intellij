package com.smallcloud.codify.modes.highlight

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.JBColor
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequest
import java.awt.Color

class HighlightLayout(
    private val editor: Editor,
    val request: SMCRequest,
    val prediction: SMCPrediction,
) : Disposable {
    var rendered: Boolean = false
    private var rangeTokensHighlighters: MutableList<RangeHighlighter> = mutableListOf()
    private var rangeLinesHighlighters: MutableList<RangeHighlighter> = mutableListOf()

    fun isEmpty(): Boolean {
        return rangeTokensHighlighters.isEmpty() && rangeLinesHighlighters.isEmpty()
    }

    private fun clean() {
        rangeTokensHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        rangeTokensHighlighters.clear()
        rangeLinesHighlighters.forEach { editor.markupModel.removeHighlighter(it) }
        rangeLinesHighlighters.clear()
    }

    fun getHighlightsOffsets(caretOffset: Int): List<Int>? {
        for (rangeHighlight in rangeLinesHighlighters) {
            if (rangeHighlight.startOffset <= caretOffset && caretOffset <= rangeHighlight.endOffset) {
                return listOf(rangeHighlight.startOffset, rangeHighlight.endOffset)
            }
        }

        return null
    }

    override fun dispose() {
        clean()
    }

    private fun needToSkipToken(startOffset: Int, endOffset: Int): Boolean {
        val choice = prediction.choices?.first()
        val text = choice?.files?.get(request.body.cursorFile)
        if (text != null) {
            return text.subSequence(startOffset, endOffset) == "\n"
        }
        return true
    }

    fun render(): HighlightLayout {
        rendered = false

        for (highlightTokens in prediction.highlightTokens) {
            val startOffset = highlightTokens[0].toInt()
            val endOffset = highlightTokens[1].toInt()

            if (needToSkipToken(startOffset, endOffset)) continue

            rangeTokensHighlighters.add(
                editor.markupModel.addRangeHighlighter(
                    startOffset, endOffset,
                    99999,
                    TextAttributes().apply {
                        backgroundColor = Color(255, 255, 0, (256 * highlightTokens[2]).toInt())
                    },
                    HighlighterTargetArea.EXACT_RANGE
                ).also {
                    it.errorStripeMarkColor = JBColor.YELLOW
                    it.errorStripeTooltip = "Codify: \"${request.body.intent}\""
                }
            )
        }
        for (highlightLine in prediction.highlightLines) {
            rangeLinesHighlighters.add(
                editor.markupModel.addRangeHighlighter(
                    highlightLine[0].toInt(),
                    highlightLine[1].toInt(),
                    99998,
                    TextAttributes().apply {
                        backgroundColor = Color(255, 255, 0, (256 * highlightLine[2]).toInt())
                    },
                    HighlighterTargetArea.LINES_IN_RANGE
                ).also {
                    it.errorStripeMarkColor = JBColor.YELLOW
                    it.errorStripeTooltip = "Codify: \"${request.body.intent}\""
                }
            )
        }
        rendered = true
        return this
    }

}