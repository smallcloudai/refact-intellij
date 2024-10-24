package com.smallcloud.refactai.code_lens.renderer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.util.TextRange
import com.intellij.util.text.findTextRange
import com.smallcloud.refactai.code_lens.CodeLen
import java.awt.Point

private fun findAlignment(str: String): String {
    val splited = str.split(Regex("\\s+"))
    if (splited.size < 2 || splited[0] != "") return ""
    return str.substring(0, str.findTextRange(splited[1])!!.startOffset)
}
class Inlayer(
    val editor: Editor,
    val codeLens: List<CodeLen>
) : Disposable {
    private var inlays: MutableList<Inlay<*>> = mutableListOf()
    private var renderers: MutableList<PanelRenderer> = mutableListOf()

    init {
        codeLens.forEach { codeLen ->
            val label2func = codeLen.labelToAction.map { it.first to { it.second.actionPerformed() } }.toMutableList()
            val alignment = findAlignment(
                editor.document.getText(
                    TextRange(
                        editor.document.getLineStartOffset(codeLen.line),
                        editor.document.getLineEndOffset(codeLen.line)
                    )
                )
            )
            var firstSymbolPos = Point(0, 0)
            ApplicationManager.getApplication().invokeAndWait {
                firstSymbolPos = editor.offsetToXY(editor.document.getLineStartOffset(codeLen.line) + alignment.length)
            }
            val renderer = PanelRenderer(firstSymbolPos, editor, label2func)
            renderers.add(renderer)
            ApplicationManager.getApplication().invokeAndWait {
                val inlay = editor.inlayModel.addBlockElement(
                    editor.logicalPositionToOffset(LogicalPosition(codeLen.line, 0)), true, true, 9, renderer)
                if (inlay != null) {
                    inlays.add(inlay)
                }
            }
        }
    }

    override fun dispose() {
        inlays.forEach { it.dispose() }
        inlays.clear()

        renderers.forEach { it.dispose() }
        renderers.clear()
    }

}