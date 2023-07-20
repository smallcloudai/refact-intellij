package com.smallcloud.refactai.modes.diff.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.UIUtil
import java.awt.Cursor
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent


enum class Style {
    Normal, Underlined
}

class PanelRenderer(
    private val firstSymbolPos: Point,
    private val editor: Editor,
    private val labels: List<Pair<String, () -> Unit>>
) : EditorCustomElementRenderer, EditorMouseListener, EditorMouseMotionListener, Disposable {
    private var inlayVisitor: Inlay<*>? = null
    private var xBounds: MutableList<Pair<Int, Int>> = mutableListOf()
    private val styles: MutableList<Style> = labels.map { Style.Normal }.toMutableList()
    private val defaultCursor = Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR)

    init {
        editor.addEditorMouseListener(this)
        editor.addEditorMouseMotionListener(this)
    }

    override fun calcWidthInPixels(inlay: Inlay<*>): Int {
        inlayVisitor = inlay
        return labels.sumOf {
            editor.contentComponent
                .getFontMetrics(RenderHelper.getFont(editor, false)).stringWidth(it.first)
        }
    }

    override fun mouseClicked(event: EditorMouseEvent) {
        val insideIdx = insideBlockIndex(event.mouseEvent)
        if (insideIdx == -1) return
        ApplicationManager.getApplication()
            .invokeLater {
                labels[insideIdx].second()
            }
        UIUtil.setCursor(editor.contentComponent, defaultCursor)
    }

    override fun mouseMoved(event: EditorMouseEvent) {
        val insideIdx = insideBlockIndex(event.mouseEvent)
        if (insideIdx == -1) {
            UIUtil.setCursor(editor.contentComponent, defaultCursor)
        }
        for (idx in styles.indices) {
            if (idx == insideIdx) {
                UIUtil.setCursor(editor.contentComponent, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
                styles[idx] = Style.Underlined
            } else styles[idx] = Style.Normal
        }
        inlayVisitor?.update()
    }

    override fun paint(
        inlay: Inlay<*>,
        g: Graphics,
        targetRegion: Rectangle,
        textAttributes: TextAttributes
    ) {
        inlayVisitor = inlay
        val font = RenderHelper.getFont(editor, false)
        g.font = font.deriveFont(font.size2D - 1)

        xBounds.clear()
        var xOffset = maxOf(firstSymbolPos.x, 0)
        val separatorWidth = g.fontMetrics.getStringBounds(" | ", g).bounds.width
        for (idx in labels.indices) {
            val style = styles[idx]
            g.color = if (style == Style.Normal) RenderHelper.color else RenderHelper.underlineColor
            g.drawString(labels[idx].first, targetRegion.x + xOffset, targetRegion.y + editor.ascent)
            val strWidth = g.fontMetrics.getStringBounds(labels[idx].first, g).bounds.width
            xBounds.add(xOffset to xOffset + strWidth)
            xOffset += strWidth
            if (idx != labels.size - 1) {
                g.color = RenderHelper.color
                g.drawString(" | ", targetRegion.x + xOffset, targetRegion.y + editor.ascent)
                xOffset += separatorWidth
            }
        }
    }

    override fun dispose() {
        UIUtil.setCursor(editor.contentComponent, defaultCursor)
        editor.removeEditorMouseListener(this)
        editor.removeEditorMouseMotionListener(this)
    }

    private fun insideBlockIndex(mouseEvent: MouseEvent): Int {
        val inlay = inlayVisitor ?: return -1
        val inlayBounds = inlay.bounds ?: return -1
        val point = Point(mouseEvent.point)
        if (point.y < inlayBounds.y || point.y > (inlayBounds.y + inlayBounds.height)) return -1
        return xBounds.indexOfFirst { point.x >= it.first && point.x <= it.second }
    }
}
