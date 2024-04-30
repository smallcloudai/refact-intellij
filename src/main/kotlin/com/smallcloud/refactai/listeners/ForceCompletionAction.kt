package com.smallcloud.refactai.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.inline.completion.CallInlineCompletionAction
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.smallcloud.refactai.Resources

class ForceCompletionAction :
    EditorAction(CallInlineCompletionAction.CallInlineCompletionHandler()),
    ActionToIgnore {
    val ACTION_ID = "ForceCompletionAction"

    init {
        this.templatePresentation.icon = Resources.Icons.LOGO_RED_16x16
    }
}
