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


// copy code from https://github.com/JetBrains/intellij-community/blob/97f1fa8169ce800fd5bfecccb07ccc869d827a4c/platform/platform-impl/src/com/intellij/codeInsight/inline/completion/InlineCompletionActions.kt#L130
// CallInlineCompletionHandler became internal
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
