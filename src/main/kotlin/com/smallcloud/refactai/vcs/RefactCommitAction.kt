package com.smallcloud.refactai.vcs

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.vcs.commit.AbstractCommitWorkflowHandler
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.lspGetCommitMessage
import com.smallcloud.refactai.notifications.emitError
import com.smallcloud.refactai.notifications.emitInfo
import java.io.StringWriter
import java.nio.file.Path

class RefactCommitAction : AnAction(
    "Generate Commit Message with Refact AI",
    "Generate a commit message using AI",
    Resources.Icons.LOGO_RED_16x16
) {
    companion object {
        private const val DIFF_SIZE_WARNING_THRESHOLD = 256000
    }

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

        generateMessage(project, changes.toList(), commitMessage)
    }

    private fun generateMessage(project: Project, changes: List<Change>, commitMsg: CommitMessage) {
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project, "Generating Commit Message...", true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true

                val repositoryPath = getVcsRoot(project, changes)
                if (repositoryPath == null) {
                    ApplicationManager.getApplication().invokeLater {
                        emitError("No VCS repository found for the selected changes")
                    }
                    return
                }

                val diff = try {
                    generateDiff(project, changes, repositoryPath)
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        emitError("Failed to generate diff: ${e.message}")
                    }
                    return
                }

                if (diff.isBlank()) {
                    ApplicationManager.getApplication().invokeLater {
                        emitInfo("No changes to generate commit message for")
                    }
                    return
                }

                if (diff.length > DIFF_SIZE_WARNING_THRESHOLD) {
                    var proceed = false
                    ApplicationManager.getApplication().invokeAndWait {
                        val result = Messages.showYesNoDialog(
                            project,
                            "The selected changes are large (${diff.length / 1000}KB, ~${diff.length / 4} tokens).\n\n" +
                                "This may take longer and use more API credits.\n" +
                                "Consider selecting fewer files for better results.\n\n" +
                                "Generate commit message anyway?",
                            "Large Diff Warning",
                            "Generate",
                            "Cancel",
                            Messages.getWarningIcon()
                        )
                        proceed = result == Messages.YES
                    }
                    if (!proceed) return
                }

                try {
                    val currentMessage = commitMsg.text?.takeIf { it.isNotBlank() } ?: ""
                    val result = lspGetCommitMessage(project, diff, currentMessage)

                    if (result.isNotBlank()) {
                        ApplicationManager.getApplication().invokeLater {
                            commitMsg.setCommitMessage(result)
                        }
                    } else {
                        ApplicationManager.getApplication().invokeLater {
                            emitError("Failed to generate commit message: empty response from server")
                        }
                    }
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        val errorMsg = when {
                            e.message?.contains("timeout", ignoreCase = true) == true ->
                                "Request timed out. The diff might be too large."
                            e.message?.contains("connection", ignoreCase = true) == true ->
                                "Connection error. Please check if LSP server is running."
                            else -> "Error generating commit message: ${e.message}"
                        }
                        emitError(errorMsg)
                    }
                }
            }
        })
    }

    private fun getVcsRoot(project: Project, changes: List<Change>): Path? {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)

        val firstFile = changes.firstNotNullOfOrNull { change ->
            change.virtualFile ?: change.afterRevision?.file?.virtualFile ?: change.beforeRevision?.file?.virtualFile
        }

        val vcsRoot = firstFile?.let { vcsManager.getVcsRootFor(it) }
            ?: vcsManager.allVcsRoots.firstOrNull()?.path

        return vcsRoot?.toNioPath()
    }

    private fun generateDiff(project: Project, changes: List<Change>, repositoryPath: Path): String {
        val filePatches = IdeaTextPatchBuilder.buildPatch(
            project,
            changes,
            repositoryPath,
            false,
            true
        )

        val writer = StringWriter()
        UnifiedDiffWriter.write(
            null,
            repositoryPath,
            filePatches,
            writer,
            "\n",
            null,
            null
        )

        return writer.toString()
    }
}
