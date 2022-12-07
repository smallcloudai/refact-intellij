package com.smallcloud.codify.inline.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer

class Inlayer(val editor: Editor) : Disposable {
    private var lineInlay: Inlay<*>? = null
    private var blockInlay: Inlay<*>? = null
    override fun dispose() {
        lineInlay?.let {
            Disposer.dispose(it)
            lineInlay = null
        }
        blockInlay?.let {
            Disposer.dispose(it)
            blockInlay = null
        }
    }
    private fun render_line(line: String, offset: Int) {
        val renderer = LineRenderer(editor, line, false)
        val element = editor
                .inlayModel
                .addInlineElement(offset, true, renderer)
        element?.let { Disposer.register(this, it) }
        lineInlay = element
    }
    private fun render_block(lines: List<String>, offset: Int) {
        val renderer = BlockElementRenderer(editor, lines, false)
        val element = editor
                .inlayModel
                .addBlockElement(offset, false, false, 1, renderer)
        element?.let { Disposer.register(this, it) }
        blockInlay = element
    }
    fun render(lines: List<String>, offset: Int) {
        val first_l = lines.first()
        val other_lines = lines.drop(1)

        if (!first_l.isEmpty()) {
            render_line(first_l, offset)
        }
        if (other_lines.isNotEmpty()) {
            render_block(other_lines, offset)
        }
    }
}