package com.smallcloud.refactai.vcs

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.lspGetCommitMessage

class RefactCommitAction : AnAction(
    "Generate Commit Message with Refact AI",
    "Generate a commit message using AI",
    Resources.Icons.LOGO_RED_16x16
) {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage
        val lspAvailable = project != null && LSPProcessHolder.getInstance(project)?.isWorking == true
        e.presentation.isEnabledAndVisible = project != null && commitMessage != null
        e.presentation.isEnabled = lspAvailable
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val lsp = LSPProcessHolder.getInstance(project)
        if (lsp == null || !lsp.isWorking) return

        val commitMessage = e.getData(VcsDataKeys.COMMIT_MESSAGE_CONTROL) as? CommitMessage ?: return
        val handler = e.getData(VcsDataKeys.COMMIT_WORKFLOW_HANDLER) as? AbstractCommitWorkflowHandler<*, *>
        val changes = handler?.ui?.getIncludedChanges() ?: return
        if (changes.isEmpty()) return

        generateMessage(project, changes, commitMessage)
    }

    private fun generateMessage(project: Project, changes: Collection<Change>, commitMsg: CommitMessage) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Commit Message...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                val diff = buildDiff(changes)
                if (diff.isBlank()) return

                try {
                    val result = lspGetCommitMessage(project, diff, commitMsg.text ?: "")
                    if (result.isNotBlank()) {
                        ApplicationManager.getApplication().invokeLater {
                            commitMsg.setCommitMessage(result)
                        }
                    }
                } catch (e: Exception) {
                    // Silently fail - LSP might not be ready
                }
            }
        })
    }

    private fun buildDiff(changes: Collection<Change>): String {
        val sb = StringBuilder()
        for (change in changes) {
            val path = change.afterRevision?.file?.path ?: change.beforeRevision?.file?.path ?: continue
            val before = change.beforeRevision?.content ?: ""
            val after = change.afterRevision?.content ?: ""
            sb.append("--- a/$path\n+++ b/$path\n")
            if (before.isEmpty()) {
                after.lines().forEach { sb.append("+$it\n") }
            } else if (after.isEmpty()) {
                before.lines().forEach { sb.append("-$it\n") }
            } else {
                before.lines().forEach { sb.append("-$it\n") }
                after.lines().forEach { sb.append("+$it\n") }
            }
        }
        return sb.toString()
    }
}
