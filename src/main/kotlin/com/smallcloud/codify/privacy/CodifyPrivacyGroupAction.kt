package com.smallcloud.codify.privacy

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.codify.CodifyBundle
import com.smallcloud.codify.Resources
import com.smallcloud.codify.settings.PrivacyState


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
                Messages.showOkCancelDialog(CodifyBundle.message("privacy.action.dialogMsg"), Resources.codifyStr,
                    CodifyBundle.message("privacy.action.dialogYes"),
                    CodifyBundle.message("cancel"), Messages.getQuestionIcon()) != Messages.OK) {
                    return
                }
            }
        PrivacyService.instance.setPrivacy(file, privacy)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = message
    }
}

class CodifyPrivacyGroupAction : ActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
        return arrayOf(
            ProjectViewPrivacySetterAction(Privacy.DISABLED,
                "${CodifyBundle.message("privacy.level0Name")}: " +
                        CodifyBundle.message("privacy.level0ShortDescription")
            ),
            ProjectViewPrivacySetterAction(Privacy.ENABLED, CodifyBundle.message("privacy.level1Name")),
            ProjectViewPrivacySetterAction(Privacy.THIRDPARTY, CodifyBundle.message("privacy.level2Name")),
        )
    }

    override fun update(event: AnActionEvent) {
        val files: Array<VirtualFile> = event.getData<Array<VirtualFile>>(CommonDataKeys.VIRTUAL_FILE_ARRAY) ?: return
        event.presentation.text = CodifyBundle.message("rootSettings.overridesModel.codifyAccess")
        if (PrivacyService.instance.getPrivacy(files.first()) != Privacy.DISABLED) {
            event.presentation.icon = Resources.Icons.LOGO_RED_16x16
        } else {
            event.presentation.icon = AllIcons.Diff.Lock
        }
    }
}