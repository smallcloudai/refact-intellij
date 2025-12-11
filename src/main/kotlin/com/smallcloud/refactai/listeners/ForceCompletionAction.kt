package com.smallcloud.refactai.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.InlineCompletionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.smallcloud.refactai.Resources


// DirectCall is required by InlineCompletionHandler.invoke() API
class CallInlineCompletionHandler : EditorWriteActionHandler() {
    override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext?) {
        val curCaret = caret ?: editor.caretModel.currentCaret

        val listener = InlineCompletion.getHandlerOrNull(editor) ?: return
        listener.invoke(InlineCompletionEvent.DirectCall(editor, curCaret, dataContext))
    }
}

class ForceCompletionAction :
    EditorAction(CallInlineCompletionHandler()),
    ActionToIgnore {
    val ACTION_ID = "ForceCompletionAction"

    init {
        this.templatePresentation.icon = Resources.Icons.LOGO_RED_16x16
    }
}
