package com.smallcloud.codify.listeners

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.smallcloud.codify.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.codify.privacy.ActionUnderPrivacy

class DiffInvokeAction: ActionUnderPrivacy() {
    private fun getEditor(dataContext: DataContext): Editor? {
        return CommonDataKeys.EDITOR.getData(dataContext)
    }
    override fun actionPerformed(e: AnActionEvent) {
        val dataContext = e.dataContext
        val editor: Editor = getEditor(dataContext) ?: return
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
        val dataContext = e.dataContext
        e.presentation.isEnabled = getEditor(dataContext) != null
        isEnabledInModalContext = getEditor(dataContext) != null
    }
}