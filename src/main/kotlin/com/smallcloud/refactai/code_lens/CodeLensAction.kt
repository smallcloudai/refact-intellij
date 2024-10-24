package com.smallcloud.refactai.code_lens

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory

class CodeLensAction(private val messages: List<String>,
                     private val file: String): DumbAwareAction(Resources.Icons.LOGO_RED_16x16) {
    override fun actionPerformed(p0: AnActionEvent) {
        actionPerformed()
    }
    fun actionPerformed() {
        RefactAIToolboxPaneFactory.chat?.executeCodeLensCommand("", file)
    }
}