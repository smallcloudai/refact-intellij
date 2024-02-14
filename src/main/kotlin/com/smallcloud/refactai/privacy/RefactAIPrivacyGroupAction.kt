package com.smallcloud.refactai.privacy

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.refactAIRootSettingsID
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


private class ProjectViewPrivacySetterAction(
    private val privacy: Privacy,
    private val message: String
) : DumbAwareAction() {
    override fun actionPerformed(event: AnActionEvent) {
        val dataContext = event.dataContext
        val file = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.firstOrNull() ?: return
        if (file.isDirectory) {
            val nestedFiles = PrivacyService.instance.getAllNestedChildren(file)
            var allSamePrivacy = true
            nestedFiles.forEach { allSamePrivacy = allSamePrivacy && it.privacy!! == privacy  }
            if (!nestedFiles.isEmpty() && !allSamePrivacy &&
                Messages.showOkCancelDialog(RefactAIBundle.message("privacy.action.dialogMsg"), Resources.titleStr,
                    RefactAIBundle.message("privacy.action.dialogYes"),
                    RefactAIBundle.message("cancel"), Messages.getQuestionIcon()) != Messages.OK) {
                    return
                }
            }
        PrivacyService.instance.setPrivacy(file, privacy)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = message
    }
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}

class RefactAIPrivacyGroupAction : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ProjectViewPrivacySetterAction(Privacy.DISABLED,
                "${RefactAIBundle.message("privacy.addPrivacyRole")}: " +
                        RefactAIBundle.message("privacy.level0ShortDescription")
            ),
            ProjectViewPrivacySetterAction(Privacy.ENABLED,
                    "${RefactAIBundle.message("privacy.addPrivacyRole")}: " +
                            RefactAIBundle.message("privacy.level1Name")),
            ProjectViewPrivacySetterAction(Privacy.THIRDPARTY,
                    "${RefactAIBundle.message("privacy.addPrivacyRole")}: " +
                            RefactAIBundle.message("privacy.level2Name")),
            Separator(),
            object : DumbAwareAction() {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtilImpl.showSettingsDialog(e.project, refactAIRootSettingsID, null)
                }
                override fun update(e: AnActionEvent) {
                    e.presentation.text = RefactAIBundle.message("privacy.privacyRules")
                }
            }
        )
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isVisible = false

        val files: Array<VirtualFile>? = event.getData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY)
        if (!files.isNullOrEmpty()) {
            event.presentation.text = RefactAIBundle.message("privacy.contextMenu")
            event.presentation.isEnabled = InferenceGlobalContext.isCloud
            event.presentation.isVisible = true
            if (PrivacyService.instance.getPrivacy(files.first()) != Privacy.DISABLED) {
                event.presentation.icon = Resources.Icons.LOGO_RED_16x16
            } else {
                event.presentation.icon = Resources.Icons.HAND_12x12
            }
        }
    }
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }
}