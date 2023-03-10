package com.smallcloud.codify.listeners

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.smallcloud.codify.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.codify.privacy.ActionUnderPrivacy

class AIToolboxInvokeAction: ActionUnderPrivacy() {
    private fun getEditor(e: AnActionEvent): Editor? {
        return CommonDataKeys.EDITOR.getData(e.dataContext)
                ?: e.presentation.getClientProperty(Key(CommonDataKeys.EDITOR.name))
    }
    override fun actionPerformed(e: AnActionEvent) {
        val editor: Editor = getEditor(e) ?: return
        if (!editor.document.isWritable) return
        if (getOrCreateModeProvider(editor).getDiffMode().isInRenderState() ||
            getOrCreateModeProvider(editor).getHighlightMode().isInRenderState())
            return

        if (getOrCreateModeProvider(editor).getDiffMode().isInActiveState() ||
            editor.selectionModel.selectionStart != editor.selectionModel.selectionEnd) {
            getOrCreateModeProvider(editor).getDiffMode().actionPerformed(editor)
        } else {
            getOrCreateModeProvider(editor).getHighlightMode().actionPerformed(editor)
        }
    }

    override fun setup(e: AnActionEvent) {
        e.presentation.isEnabled = getEditor(e) != null
        isEnabledInModalContext = getEditor(e) != null
    }
}