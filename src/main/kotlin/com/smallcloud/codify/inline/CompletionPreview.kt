package com.smallcloud.codify.inline

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.smallcloud.codify.inline.listeners.CaretListener
import com.smallcloud.codify.inline.listeners.FocusListener
import com.smallcloud.codify.inline.renderer.Inlayer
import com.smallcloud.codify.struct.SMCPrediction
import com.smallcloud.codify.struct.SMCRequestBody
import com.smallcloud.codify.utils.difference
import org.jetbrains.annotations.NotNull
import javax.annotation.Nullable


class CompletionPreview(val editor: Editor,
                        val request_body: SMCRequestBody,
                        val prediction: SMCPrediction,
                        val offset: Int) : Disposable {
    private val caretListener: CaretListener = CaretListener(this)
    private val focusListener: FocusListener = FocusListener(this)
    private var inlineData: Pair<String, Int>? = null
    private var inlayer: Inlayer = Inlayer(editor)
    override fun dispose() {
        inlayer.dispose()
        editor.putUserData(INLINE_COMPLETION_PREVIEW, null)
    }

    fun render() {
        val currentText = editor.document.text
        val predictedText = prediction.choices[0].files[request_body.cursorFile]
        inlineData = difference(currentText, predictedText, offset) ?: return
        val inline = inlineData!!.first
        if (inline.isEmpty()) return


        val lines = inline.split("\n")

        try {
            editor.document.startGuardedBlockChecking();
            inlayer.render(lines, offset)
        } finally {
            editor.document.stopGuardedBlockChecking();
        }

    }

    fun applyPreview(@Nullable caret: Caret?) {
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
        val (inline, textOffset) = inlineData ?: return
        editor.document.replaceString(cursorOffset, cursorOffset + textOffset, inline)
        editor.caretModel.moveToOffset(cursorOffset + inline.length)
    }

    companion object {
        private val INLINE_COMPLETION_PREVIEW: Key<CompletionPreview> = Key.create("SMC_INLINE_COMPLETION_PREVIEW")
        fun instance(editor: Editor,
                     request_body: SMCRequestBody,
                     prediction: SMCPrediction,
                     offset: Int): CompletionPreview {
            clean_instance(editor)
            val preview = CompletionPreview(editor, request_body, prediction, offset)
            editor.putUserData(INLINE_COMPLETION_PREVIEW, preview)
            return preview
        }

        fun clean_instance(editor: Editor) {
            var preview = getInstance(editor)
            if (preview != null) {
                Disposer.dispose(preview)
            }
        }

        fun getInstance(editor: Editor): CompletionPreview? {
            return editor.getUserData(INLINE_COMPLETION_PREVIEW)
        }
    }

}