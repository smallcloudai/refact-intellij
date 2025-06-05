package com.smallcloud.refactai.listeners

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
//import com.intellij.openapi.diff.impl.patch.IdeaTextPatchBuilder
//import com.intellij.openapi.diff.impl.patch.UnifiedDiffWriter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.ui.AnimatedIcon
import com.intellij.vcsUtil.VcsUtil
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.lsp.lspGetCommitMessage
import java.io.IOException
import java.io.StringWriter
import java.util.concurrent.ExecutionException


class GenerateGitCommitMessageAction : AnAction(
    Resources.titleStr,
    RefactAIBundle.message("generateCommitMessage.action.description"),
    Resources.Icons.LOGO_RED_13x13
) {
    private val spinIcon = AnimatedIcon.Default.INSTANCE
    override fun update(event: AnActionEvent) {
        ApplicationManager.getApplication().invokeLater {
            val commitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
            if (commitWorkflowUi == null) {
                event.presentation.isVisible = false
                return@invokeLater
            }

            val lspService =
                event.project?.service<com.smallcloud.refactai.lsp.LSPProcessHolder>() ?: return@invokeLater

            val isEnabled = lspService.isWorking && (commitWorkflowUi.getIncludedChanges().isNotEmpty() || commitWorkflowUi.getIncludedUnversionedFiles().isNotEmpty())

            event.presentation.isEnabled = isEnabled
            event.presentation.text = if (lspService.isWorking) {
                RefactAIBundle.message("generateCommitMessage.action.selectFiles")
            } else {
                RefactAIBundle.message("generateCommitMessage.action.loginInRefactAI")
            }
        }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project == null || project.basePath == null) {
            return
        }

        val gitDiff = getDiff(event, project) ?: return
        val commitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        if (commitWorkflowUi != null) {
            ApplicationManager.getApplication().executeOnPooledThread {
                event.presentation.icon = spinIcon
                ApplicationManager.getApplication().invokeLater {
                    commitWorkflowUi.commitMessageUi.stopLoading()
                }
                val message = lspGetCommitMessage(project, gitDiff, commitWorkflowUi.commitMessageUi.text)
                ApplicationManager.getApplication().invokeLater {
                    commitWorkflowUi.commitMessageUi.stopLoading()
                    commitWorkflowUi.commitMessageUi.setText(message)
                }
                event.presentation.icon = Resources.Icons.LOGO_RED_13x13
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    private fun getDiff(event: AnActionEvent, project: Project): String? {
        val commitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
            ?: throw IllegalStateException("Could not retrieve commit workflow ui.")

        try {
            val projectFile = project.projectFile ?: return null
            val projectFileVcsRoot = VcsUtil.getVcsRootFor(project, projectFile) ?: return null

            try {
                val includedChanges = commitWorkflowUi.getIncludedChanges().toMutableList()
                val includedUnversionedFiles = commitWorkflowUi.getIncludedUnversionedFiles()
                if (!includedUnversionedFiles.isEmpty()) {
                    for (filePath in includedUnversionedFiles) {
                        val change: Change = Change(null, CurrentContentRevision(filePath))
                        includedChanges.add(change)
                    }
                }
//                val filePatches = IdeaTextPatchBuilder.buildPatch(
//                    project, includedChanges, projectFileVcsRoot.toNioPath(), false, true
//                )

                val diffWriter = StringWriter()
//                UnifiedDiffWriter.write(
//                    null,
//                    projectFileVcsRoot.toNioPath(),
//                    filePatches,
//                    diffWriter,
//                    "\n",
//                    null,
//                    null
//                )
                return diffWriter.toString()
            } catch (e: VcsException) {
                throw RuntimeException("Unable to create git diff", e)
            } catch (e: IOException) {
                throw RuntimeException("Unable to create git diff", e)
            }
        } catch (e: InterruptedException) {
            throw RuntimeException(e)
        } catch (e: ExecutionException) {
            throw RuntimeException(e)
        }
    }
}