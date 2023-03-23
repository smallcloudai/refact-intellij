package com.smallcloud.refactai.privacy

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources


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
}

class RefactAIPrivacyGroupAction : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ProjectViewPrivacySetterAction(Privacy.DISABLED,
                "${RefactAIBundle.message("privacy.level0Name")}: " +
                        RefactAIBundle.message("privacy.level0ShortDescription")
            ),
            ProjectViewPrivacySetterAction(Privacy.ENABLED, RefactAIBundle.message("privacy.level1Name")),
            ProjectViewPrivacySetterAction(Privacy.THIRDPARTY, RefactAIBundle.message("privacy.level2Name")),
        )
    }

    override fun update(event: AnActionEvent) {
        val files: Array<VirtualFile> = event.getData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        event.presentation.text = RefactAIBundle.message("rootSettings.overridesModel.cloudAccess")
        if (PrivacyService.instance.getPrivacy(files.first()) != Privacy.DISABLED) {
            event.presentation.icon = Resources.Icons.LOGO_RED_16x16
        } else {
            event.presentation.icon = Resources.Icons.HAND_12x12
        }
    }
}