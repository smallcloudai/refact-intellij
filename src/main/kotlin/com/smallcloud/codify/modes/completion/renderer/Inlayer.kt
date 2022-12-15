package com.smallcloud.codify.modes.completion.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.smallcloud.codify.modes.completion.Completion

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

    private fun renderLine(line: String, offset: Int) {
        val renderer = LineRenderer(editor, line, false)
        val element = editor
            .inlayModel
            .addInlineElement(offset, true, renderer)
        element?.let { Disposer.register(this, it) }
        lineInlay = element
    }

    private fun renderBlock(lines: List<String>, offset: Int) {
        val renderer = BlockElementRenderer(editor, lines, false)
        val element = editor
            .inlayModel
            .addBlockElement(offset, false, false, 1, renderer)
        element?.let { Disposer.register(this, it) }
        blockInlay = element
    }

    fun render(completionData: Completion) {
        if (!completionData.multiline) {
            renderLine(completionData.completion, completionData.startIndex)
        } else {
            val lines = completionData.completion.split('\n')
            if (lines.isEmpty()) return
            val firstLine = lines.first()
            val otherLines = lines.drop(1)
            if (firstLine.isNotEmpty()) {
                renderLine(firstLine, completionData.startIndex)
            }
            if (otherLines.isNotEmpty()) {
                renderBlock(otherLines, completionData.startIndex)
            }
        }
    }
}