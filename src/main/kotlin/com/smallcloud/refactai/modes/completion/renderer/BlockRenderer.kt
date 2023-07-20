package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle


class AsyncBlockElementRenderer(
    initialBlockText: List<String>,
    private val editor: Editor,
    private val deprecated: Boolean
) : EditorCustomElementRenderer {
    private var color: Color? = null
    var blockText: List<String> = initialBlockText
        set(value) {
            synchronized(this) {
                field = value
            }
        }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        synchronized(this) {
            val line = blockText.maxByOrNull { it.length }
            val width = editor.contentComponent
                .getFontMetrics(RenderHelper.getFont(editor, deprecated)).stringWidth(line!!)
            return maxOf(width, 1)
        }
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        synchronized(this) {
            return maxOf(editor.lineHeight * blockText.size, 1)
        }
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        synchronized(this) {
            color = color ?: RenderHelper.color
            g.color = color
            g.font = RenderHelper.getFont(editor, deprecated)

            blockText.withIndex().forEach { (i, line) ->
                g.drawString(
                    line,
                    0,
                    targetRegion.y + i * editor.lineHeight + editor.ascent
                )
            }
        }
    }
}
