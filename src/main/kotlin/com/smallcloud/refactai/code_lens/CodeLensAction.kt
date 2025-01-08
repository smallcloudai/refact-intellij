package com.smallcloud.refactai.code_lens

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.UsageStats
import com.smallcloud.refactai.struct.ChatMessage
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.relativeTo

class CodeLensAction(
    private val editor: Editor,
    private val line1: Int,
    private val line2: Int,
    private val messages: Array<ChatMessage>,
    private val sendImmediately: Boolean,
    private val openNewTab: Boolean
) : DumbAwareAction(Resources.Icons.LOGO_RED_16x16) {
    override fun actionPerformed(p0: AnActionEvent) {
        actionPerformed()
    }

    private fun replaceVariablesInText(
        text: String,
        relativePath: String,
        cursor: Int?,
        codeSelection: String
    ): String {
        return text
            .replace("%CURRENT_FILE%", relativePath)
            .replace("%CURSOR_LINE%", cursor?.plus(1)?.toString() ?: "")
            .replace("%CODE_SELECTION%", codeSelection)
            .replace("%PROMPT_EXPLORATION_TOOLS%", "")
    }

    private fun formatMultipleMessagesForCodeLens(
        messages: Array<ChatMessage>,
        relativePath: String,
        cursor: Int?,
        text: String
    ): Array<ChatMessage> {
        return messages.map { message ->
            if (message.role == "user") {
                message.copy(
                    content = replaceVariablesInText(message.content, relativePath, cursor, text)
                )
            } else {
                message
            }
        }.toTypedArray()
    }

    private fun formatMessage(): Array<ChatMessage> {
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

        val formattedMessages = formatMultipleMessagesForCodeLens(messages, relativePath?.toString() ?: filePath.toString(), line1, text);

        return formattedMessages
    }

    private val isActionRunning = AtomicBoolean(false)

    fun actionPerformed() {
        val chat = editor.project?.let { ToolWindowManager.getInstance(it).getToolWindow("Refact") }

        chat?.activate {
            RefactAIToolboxPaneFactory.chat?.requestFocus()
            RefactAIToolboxPaneFactory.chat?.executeCodeLensCommand("", formatMessage(), sendImmediately, openNewTab)
            editor.project?.service<UsageStats>()?.addChatStatistic(true, UsageStatistic("openChatByCodelens"), "")
        }

        // If content is empty, then it's "Open Chat" instruction, selecting range of code in active tab
        if (contentMsg.isEmpty() && isActionRunning.compareAndSet(false, true)) {
            ApplicationManager.getApplication().invokeLater {
                try {
                    val pos1 = LogicalPosition(line1, 0)
                    val pos2 = LogicalPosition(line2, editor.document.getLineEndOffset(line2))

                    val intendedStart = editor.logicalPositionToOffset(pos1)
                    val intendedEnd = editor.logicalPositionToOffset(pos2)
                    editor.selectionModel.setSelection(intendedStart, intendedEnd)
                } finally {
                    isActionRunning.set(false)
                }
            }
        }
    }
}