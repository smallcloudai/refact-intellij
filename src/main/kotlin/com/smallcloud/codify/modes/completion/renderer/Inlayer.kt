package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer

class AsyncInlayer(
    val editor: Editor,
    private val offset: Int
) : Disposable {
    private var lineRenderer: AsyncLineRenderer? = null
    private var lineInlay: Inlay<*>? = null
    private var blockRenderer: AsyncBlockElementRenderer? = null
    private var blockInlay: Inlay<*>? = null
    private var collectedText: String = ""
    private var disposed: Boolean = false
    private var hidden: Boolean = false

    override fun dispose() {
        synchronized(this) {
            disposed = true
            lineInlay?.let {
                it.dispose()
                lineInlay = null
            }
            blockInlay?.let {
                it.dispose()
                blockInlay = null
            }
        }
    }

    fun hide() {
        synchronized(this) {
            if (disposed) return
            hidden = true
            lineInlay?.let {
                it.dispose()
                lineInlay = null
            }
            blockInlay?.let {
                it.dispose()
                blockInlay = null
            }
        }
    }

    fun show() {
        synchronized(this) {
            if (disposed) return
            hidden = false
            addText("")
            lineInlay?.update()
            blockInlay?.update()
        }
    }

    private fun createLine(initialText: String) {
        lineRenderer = AsyncLineRenderer(initialText, editor, false)
        lineInlay = editor
            .inlayModel
            .addInlineElement(offset, true, lineRenderer!!)
            ?.also { Disposer.register(this, it) }
    }

    private fun createBlock(initialLines: List<String>) {
        blockRenderer = AsyncBlockElementRenderer(initialLines, editor, false)
        blockInlay = editor
            .inlayModel
            .addBlockElement(offset, false, false, 1, blockRenderer!!)
            ?.also { Disposer.register(this, it) }
    }

    fun addText(text: String) {
        if (disposed) { dispose() }
        synchronized(this) {
            collectedText += text
            val lines = collectedText.split('\n')

            if (lineInlay == null && !disposed) {
                createLine(lines[0])
            } else {
                lineRenderer?.text = lines[0]
                lineInlay?.update()
            }

            if (lines.size > 1) {
                val subList = lines.subList(1, lines.size)
                if (blockInlay == null && !disposed) {
                    createBlock(subList)
                } else {
                    blockRenderer?.blockText = subList
                    blockInlay?.update()
                }
            }
        }
    }

    fun addTextWithoutRendering(text: String) {
        synchronized(this) {
            if (disposed) return
            collectedText += text
        }
    }
}