package com.smallcloud.refactai.codecompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionFontUtils
import com.intellij.codeInsight.inline.completion.elements.InlineCompletionElement
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Rectangle


// delete this file ASAP

fun String.formatBeforeRendering(editor: Editor): String {
    val tabSize = editor.settings.getTabSize(editor.project)
    val tab = " ".repeat(tabSize)
    return replace("\t", tab)
}

class InlineBlockElementRenderer(private val editor: Editor, lines: List<String>) : EditorCustomElementRenderer {

    private val font = InlineCompletionFontUtils.font(editor)
    private val width = editor
        .contentComponent
        .getFontMetrics(font)
        .stringWidth(lines.maxBy { it.length })

    val lines = lines.map { it.formatBeforeRendering(editor) }

    override fun calcWidthInPixels(inlay: Inlay<*>) = width

    override fun calcHeightInPixels(inlay: Inlay<*>) = editor.lineHeight * lines.size

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        g.color = InlineCompletionFontUtils.color(editor)
        g.font = font
        lines.forEachIndexed { i, it -> g.drawString(it, 0, targetRegion.y + editor.ascent + i * editor.lineHeight) }
    }
}

class InlineSuffixRenderer(private val editor: Editor, suffix: String) : EditorCustomElementRenderer {
    private val font = InlineCompletionFontUtils.font(editor)
    private val width = editor.contentComponent.getFontMetrics(font).stringWidth(suffix)

    val suffix = suffix.formatBeforeRendering(editor)

    override fun calcWidthInPixels(inlay: Inlay<*>): Int = width
    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        return editor.contentComponent.getFontMetrics(font).height
    }

    override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
        g.color = InlineCompletionFontUtils.color(editor)
        g.font = font
        g.drawString(suffix, targetRegion.x, targetRegion.y + editor.ascent)
    }
}


class InlineCompletionGrayTextElementCustom(override val text: String, private val delta: Int = 0) :
    InlineCompletionElement {

    override fun toPresentable(): InlineCompletionElement.Presentable = Presentable(this, delta)

    open class Presentable(override val element: InlineCompletionElement, val delta: Int = 0) :
        InlineCompletionElement.Presentable {
        private var suffixInlay: Inlay<*>? = null
        private var blockInlay: Inlay<*>? = null

        override fun isVisible(): Boolean = suffixInlay != null || blockInlay != null

        /**
         * Temporal workaround for an internal plugin. **Should not be used.**
         */
        @ApiStatus.Internal
        @ApiStatus.Experimental
        protected open fun getText(): String = element.text

        override fun render(editor: Editor, offset: Int) {
            val text = getText()
            if (text.isEmpty()) return
            val lines = text.lines()
            renderSuffix(editor, lines, offset - delta)
            if (lines.size > 1) {
                renderBlock(lines.drop(1), editor, offset)
            }
        }

        override fun getBounds(): Rectangle? {
            val bounds = suffixInlay?.bounds?.let { Rectangle(it) }
            blockInlay?.bounds?.let { bounds?.add(Rectangle(it)) }
            return bounds
        }

        override fun startOffset(): Int? = suffixInlay?.offset
        override fun endOffset(): Int? = suffixInlay?.offset

        override fun dispose() {
            blockInlay?.also(Disposer::dispose)
            blockInlay = null
            suffixInlay?.also(Disposer::dispose)
            suffixInlay = null
        }

        private fun renderSuffix(editor: Editor, lines: List<String>, offset: Int) {
            // The following is a hacky solution to the effect described in ML-977
            // ML-1781 Inline completion renders on the left to the caret after moving it
            editor.forceLeanLeft()

            val line = lines.first()
            if (line.isEmpty()) {
                suffixInlay =
                    editor.inlayModel.addInlineElement(editor.caretModel.offset, object : EditorCustomElementRenderer {
                        override fun calcWidthInPixels(inlay: Inlay<*>) = 1
                        override fun calcHeightInPixels(inlay: Inlay<*>) = 1
                        override fun paint(
                            inlay: Inlay<*>,
                            g: Graphics,
                            targetRegion: Rectangle,
                            textAttributes: TextAttributes
                        ) {
                        }
                    })
                return
            }
            editor.inlayModel.execute(true) {
                val element = editor.inlayModel.addInlineElement(offset, true, InlineSuffixRenderer(editor, line))
                    ?: return@execute
                element.addActionAvailabilityHint(
                    EditorActionAvailabilityHint(
                        IdeActions.ACTION_INSERT_INLINE_COMPLETION,
                        EditorActionAvailabilityHint.AvailabilityCondition.CaretOnStart,
                    )
                )
                suffixInlay = element
            }
        }

        private fun renderBlock(
            lines: List<String>,
            editor: Editor,
            offset: Int
        ) {
            val element = editor.inlayModel.addBlockElement(
                offset, true, false, 1,
                InlineBlockElementRenderer(editor, lines)
            ) ?: return

            blockInlay = element
        }

        private fun Editor.forceLeanLeft() {
            val visualPosition = caretModel.visualPosition
            if (visualPosition.leansRight) {
                val leftLeaningPosition = VisualPosition(visualPosition.line, visualPosition.column, false)
                caretModel.moveToVisualPosition(leftLeaningPosition)
            }
        }
    }
}
