package com.smallcloud.codify.modes.diff.renderer


import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import dev.gitlive.difflib.patch.Patch
import java.awt.Color
import java.awt.Graphics
import java.awt.Rectangle


open class BlockElementRenderer(
    private val color: Color,
    private val veryColor: Color,
    private val editor: Editor,
    private val blockText: List<String>,
    private val smallPatches: List<Patch<Char>>,
    private val deprecated: Boolean
) : EditorCustomElementRenderer {

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        val line = blockText.maxByOrNull { it.length }
        return editor.contentComponent
            .getFontMetrics(RenderHelper.getFont(editor, deprecated)).stringWidth(line!!)
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
        val highlightG = g.create()
        highlightG.color = color
        highlightG.fillRect(targetRegion.x, targetRegion.y, 9999999, targetRegion.height)
        g.font = RenderHelper.getFont(editor, deprecated)
        g.color = editor.colorsScheme.defaultForeground
        val metric = g.getFontMetrics(g.font)

        val smallPatchesG = g.create()
        smallPatchesG.color = veryColor
        smallPatches.withIndex().forEach{ (i, patch) ->
            val currentLine = blockText[i]
            patch.getDeltas().forEach {
                val startBound = g.font.getStringBounds(currentLine.substring(0, it.target.position),
                    metric.fontRenderContext)
                val endBound = g.font.getStringBounds(currentLine.substring(0, it.target.position + it.target.size()),
                    metric.fontRenderContext)
                smallPatchesG.fillRect(
                    targetRegion.x + startBound.width.toInt(),
                    targetRegion.y + i * editor.lineHeight,
                    (endBound.width - startBound.width).toInt(),
                    editor.lineHeight)
            }
        }
        blockText.withIndex().forEach { (i, line) ->
            g.drawString(
                line,
                0,
                targetRegion.y + i * editor.lineHeight + editor.ascent
            )
        }
    }
}

class InsertBlockElementRenderer(
    private val editor: Editor,
    private val blockText: List<String>,
    private val smallPatches: List<Patch<Char>>,
    private val deprecated: Boolean
) : BlockElementRenderer(greenColor, veryGreenColor, editor, blockText, smallPatches, deprecated)

class DeleteBlockElementRenderer(
    private val editor: Editor,
    private val blockText: List<String>,
    private val smallPatches:List<Patch<Char>>,
    private val deprecated: Boolean
) : BlockElementRenderer(redColor, veryRedColor, editor, blockText, smallPatches, deprecated)

