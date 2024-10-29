package com.smallcloud.refactai.code_lens

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import kotlin.io.path.relativeTo
import java.util.concurrent.atomic.AtomicBoolean

class CodeLensAction(
    private val editor: Editor,
    private val line1: Int,
    private val line2: Int,
    private val contentMsg: String,
    private val sendImmediately: Boolean,
    private val openNewTab: Boolean
) : DumbAwareAction(Resources.Icons.LOGO_RED_16x16) {
    override fun actionPerformed(p0: AnActionEvent) {
        actionPerformed()
    }

    private fun formatMessage(): String {
        val pos1 = LogicalPosition(line1, 0)
        val text = editor.document.text.slice(
            editor.logicalPositionToOffset(pos1) until editor.document.getLineEndOffset(line2)
        )
        val filePath = editor.virtualFile.toNioPath()
        val relativePath = editor.project?.let {
            ProjectRootManager.getInstance(it).contentRoots.map { root ->
                filePath.relativeTo(root.toNioPath())
            }.minBy { it.toString().length }
        }

        return contentMsg
            .replace("%CURRENT_FILE%", relativePath?.toString() ?: filePath.toString())
            .replace("%CURSOR_LINE%", line1.toString())
            .replace("%CODE_SELECTION%", text)
    }

    private val isActionRunning = AtomicBoolean(false)

    fun actionPerformed() {
        val chat = editor.project?.let { ToolWindowManager.getInstance(it).getToolWindow("Refact") }
        chat?.activate {
            RefactAIToolboxPaneFactory.chat?.requestFocus()
            RefactAIToolboxPaneFactory.chat?.executeCodeLensCommand(formatMessage(), sendImmediately, openNewTab)
        }
        // If content is empty, then it's "Open Chat" instruction, selecting range of code in active tab
        if (contentMsg.isEmpty()) {
            if (isActionRunning.compareAndSet(false, true)) {
                ApplicationManager.getApplication().invokeLater {
                    try {
                        editor.selectionModel.setSelection(editor.logicalPositionToOffset(LogicalPosition(line1, 0)), editor.logicalPositionToOffset(LogicalPosition(line1, 0)))

                        ApplicationManager.getApplication().invokeLater {
                            Thread.sleep(150)
                            val pos1 = LogicalPosition(line1, 0)
                            val pos2 = LogicalPosition(line2, editor.document.getLineEndOffset(line2))

                            val intendedStart = editor.logicalPositionToOffset(pos1)
                            val intendedEnd = editor.logicalPositionToOffset(pos2)

                            editor.selectionModel.setSelection(intendedStart, intendedEnd)
                        }
                    } finally {
                        isActionRunning.set(false)
                    }
                }
            }
        }
    }
}