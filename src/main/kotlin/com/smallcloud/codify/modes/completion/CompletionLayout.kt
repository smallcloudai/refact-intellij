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
import org.jetbrains.annotations.NotNull

class CompletionLayout(
    private val editor: Editor,
    val completionData: Completion
) : Disposable {
    private var inlayer: Inlayer? = null
    var blockEvents: Boolean = false
    var rendered: Boolean = false

    override fun dispose() {
        rendered = false
        blockEvents = false
        inlayer?.dispose()
    }

    fun render(): CompletionLayout {
        assert(!rendered) { "Already rendered" }
        try {
            blockEvents = true
            editor.document.startGuardedBlockChecking()
            inlayer = Inlayer(editor).render(completionData)
            rendered = true
        } catch (ex: Exception) {
            Disposer.dispose(this)
            throw ex
        } finally {
            editor.document.stopGuardedBlockChecking()
            blockEvents = false
        }
        return this
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
        editor.document.replaceString(
            completionData.startIndex,
            completionData.endIndex,
            completionData.completion
        )
        editor.caretModel.moveToOffset(
            completionData.startIndex + completionData.completion.length
        )
    }

    fun hide() {
        if (!rendered) return
        dispose()
    }

    fun show() {
        if (rendered) return
        render()
    }
}
