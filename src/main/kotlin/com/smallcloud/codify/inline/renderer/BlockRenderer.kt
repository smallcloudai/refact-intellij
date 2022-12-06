package com.smallcloud.codify.inline.renderer


import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle

fun longest_string(array: List<String>) : String {
    var index = 0
    var elementLength: Int = array.get(0).length
    for (i in 1 until array.size) {
        if (array.get(i).length > elementLength) {
            index = i
            elementLength = array.get(i).length
        }
    }
    return array.get(index)
}

class BlockElementRenderer(
        private val editor: Editor,
        private val blockText: List<String>,
        private val deprecated: Boolean
) : EditorCustomElementRenderer {
    private var color: Color? = null

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val line = longest_string(blockText)
        return editor.contentComponent
                .getFontMetrics(RenderHelper.getFont(editor, deprecated)).stringWidth(line)
    }

    override fun calcHeightInPixels(inlay: Inlay<*>): Int {
        return editor.lineHeight * blockText.size
    }

    override fun paint(
            inlay: Inlay<*>,
            g: Graphics,
            targetRegion: Rectangle,
            textAttributes: TextAttributes
    ) {
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

    @TestOnly
    fun getContent(): String {
        return blockText.joinToString("\n")
    }
}
