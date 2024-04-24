package com.smallcloud.refactai.codecompletion

import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.codeInsight.inline.completion.InlineCompletionRequest
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.util.PsiUtilBase
import com.intellij.util.concurrency.annotations.RequiresBlockingContext

class RefactAIContinuousEvent(val editor: Editor, val offset: Int) : InlineCompletionEvent {
    override fun toRequest(): InlineCompletionRequest? {
        val project = editor.project ?: return null
        val file = getPsiFile(editor, project) ?: return null
        return InlineCompletionRequest(this, file, editor, editor.document, offset, offset)
    }
}

@RequiresBlockingContext
private fun getPsiFile(editor: Editor, project: Project): PsiFile? {
    return runReadAction {
        val file =
            PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return@runReadAction null
        // * [PsiUtilBase] takes into account injected [PsiFile] (like in Jupyter Notebooks)
        // * However, it loads a file into the memory, which is expensive
        // * Some tests forbid loading a file when tearing down
        // * On tearing down, Lookup Cancellation happens, which causes the event
        // * Existence of [treeElement] guarantees that it's in the memory
        if (file.isLoadedInMemory()) {
            PsiUtilBase.getPsiFileInEditor(editor, project)
        } else {
            file
        }
    }
}

private fun PsiFile.isLoadedInMemory(): Boolean {
    return (this as? PsiFileImpl)?.treeElement != null
}
