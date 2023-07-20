package com.smallcloud.refactai.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer

class AsyncInlayer(
    val editor: Editor,
) : Disposable {
    private var lineRenderers: MutableMap<Int, AsyncLineRenderer> = mutableMapOf()
    private var lineInlays: MutableMap<Int, Inlay<*>> = mutableMapOf()
    private var blockRenderer: AsyncBlockElementRenderer? = null
    private var blockInlay: Inlay<*>? = null
    private var collectedTexts: MutableMap<Int, String> = mutableMapOf()
    private var disposed: Boolean = false
    private var hidden: Boolean = false
    private var realTexts: MutableMap<Int, String> = mutableMapOf()

    override fun dispose() {
        synchronized(this) {
            disposed = true
            lineInlays.forEach {
                it.value.dispose()
            }
            lineInlays.clear()
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
            lineInlays.forEach {
                it.value.dispose()
            }
            lineInlays.clear()
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
//            addText(initialOffset, "")
            lineInlays.forEach {
                it.value.update()
            }
//            lineInlay?.update()
            blockInlay?.update()
        }
    }

    private fun createLine(offset: Int, initialText: String) {
        lineRenderers[offset] = AsyncLineRenderer(initialText, editor, false)
        val lineInlay = editor
            .inlayModel
            .addInlineElement(offset, true, lineRenderers[offset]!!)
            ?.also { Disposer.register(this, it) }
        if (lineInlay != null) {
            lineInlays[offset] = lineInlay
        }
    }

    private fun createBlock(offset: Int, initialLines: List<String>) {
        blockRenderer = AsyncBlockElementRenderer(initialLines, editor, false)
        blockInlay = editor
            .inlayModel
            .addBlockElement(offset, false, false, 1, blockRenderer!!)
            ?.also { Disposer.register(this, it) }
    }

    fun getText(offset: Int): String? {
        synchronized(this) {
            return realTexts[offset]
        }
    }

    fun setText(offset: Int, text: String) {
        synchronized(this) {
            realTexts[offset] = text
        }
    }

    fun addText(offset: Int, text: String) {
        if (disposed) { dispose() }
        synchronized(this) {
            if (!collectedTexts.containsKey(offset)) { collectedTexts[offset] = "" }
            collectedTexts[offset] += text
            val lines = collectedTexts[offset]!!.split('\n')

            if (lineInlays[offset] == null && !disposed) {
                createLine(offset, lines[0])
            } else {
                lineRenderers[offset]?.text = lines[0]
                lineInlays.forEach {
                    it.value.update()
                }
            }

            if (lines.size > 1) {
                val subList = lines.subList(1, lines.size)
                val notEmpty = subList.any { it.isNotEmpty() }
                if (notEmpty) {
                    if (blockInlay == null && !disposed) {
                        createBlock(offset, subList)
                    } else {
                        blockRenderer?.blockText = subList
                        blockInlay?.update()
                    }
                }
            }
        }
    }

    fun addTextWithoutRendering(offset: Int, text: String) {
        synchronized(this) {
            if (disposed) return
            collectedTexts[offset] += text
        }
    }

    fun addTextToLast(text: String) {
        synchronized(this) {
            val entry = realTexts.maxBy { it.key }
            addText(entry.key, text)
        }
    }

    fun addTextWithoutRenderingToLast(text: String) {
        synchronized(this) {
            val entry = realTexts.maxBy { it.key }
            addTextWithoutRendering(entry.key, text)
        }
    }

    fun getLastText(): String? {
        synchronized(this) {
//            try {
                val entry = realTexts.maxBy { it.key }
                return getText(entry.key)
//            } catch (e: Exception) {
//                throw e
//            }
        }
    }

    fun setLastText(realBlockText: String) {
        synchronized(this) {
            val entry = realTexts.maxBy { it.key }
            setText(entry.key, realBlockText)
        }
    }
}