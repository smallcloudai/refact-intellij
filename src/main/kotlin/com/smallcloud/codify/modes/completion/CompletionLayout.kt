package com.smallcloud.codify.modes.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.smallcloud.codify.modes.completion.renderer.Inlayer
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequestBody
import org.jetbrains.annotations.NotNull

class CompletionLayout(
    private val editor: Editor,
    private val completionData: Pair<String, Int>,
    private val offset: Int
) : Disposable {
    private var inlayer: Inlayer = Inlayer(editor)

    override fun dispose() {
        inlayer.dispose()
    }

    fun render() {
        val inline = completionData.first
        val lines = inline.split("\n")
        try {
            editor.document.startGuardedBlockChecking();
            inlayer.render(lines, offset)
        } finally {
            editor.document.stopGuardedBlockChecking();
        }
    }

    fun applyPreview(caret: Caret?) {
        if (caret == null) {
            return
        }
        val project = editor.project ?: return
        val file: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return
        try {
            applyPreviewInternal(caret.offset, project, file)
        } catch (e: Throwable) {
            Logger.getInstance(javaClass).warn("Failed in the processes of accepting completion", e)
        } finally {
            Disposer.dispose(this)
        }
    }

    private fun applyPreviewInternal(@NotNull cursorOffset: Int, project: Project, file: PsiFile) {
        val (inline, textOffset) = completionData
        editor.document.replaceString(cursorOffset, cursorOffset + textOffset, inline)
        editor.caretModel.moveToOffset(cursorOffset + inline.length)
    }
}
