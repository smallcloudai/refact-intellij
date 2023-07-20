package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

class AsyncLineRenderer(
    initialText: String,
    private val editor: Editor,
    private val deprecated: Boolean
) : EditorCustomElementRenderer {
    private var color: Color? = null
    var text: String = initialText
        set(value) {
            synchronized(this) {
                field = value
            }
        }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        synchronized(this) {
            val width = editor.contentComponent
                .getFontMetrics(RenderHelper.getFont(editor, deprecated)).stringWidth(text)
            return maxOf(width, 1)
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
            g.drawString(text, targetRegion.x, targetRegion.y + editor.ascent)
        }
    }
}
