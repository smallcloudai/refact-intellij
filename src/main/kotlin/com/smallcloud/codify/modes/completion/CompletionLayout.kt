package com.smallcloud.codify.modes.completion

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.smallcloud.codify.modes.completion.renderer.Inlayer
import org.jetbrains.annotations.NotNull

class CompletionLayout(
    private val editor: Editor,
    private val completionData: Completion
) : Disposable {
    private var inlayer: Inlayer = Inlayer(editor)
    var blockEvents: Boolean = false

    override fun dispose() {
        inlayer.dispose()
    }

    fun render() {
        try {
            blockEvents = true
            editor.document.startGuardedBlockChecking()
            inlayer.render(completionData)
        } catch (ex: Exception) {
            Disposer.dispose(this)
            throw ex
        } finally {
            editor.document.stopGuardedBlockChecking()
            blockEvents = false
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
        editor.document.replaceString(
            completionData.startIndex,
            completionData.endIndex,
            completionData.completion
        )
        editor.caretModel.moveToOffset(
            completionData.startIndex + completionData.completion.length
        )
    }
}
