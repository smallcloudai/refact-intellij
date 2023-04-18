package com.smallcloud.refactai.privacy

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.vfs.VirtualFile
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.io.InferenceGlobalContext

abstract class ActionUnderPrivacy : DumbAwareAction(Resources.Icons.LOGO_RED_16x16) {
    abstract fun setup(e: AnActionEvent)
    final override fun update(e: AnActionEvent) {
        setup(e)
        val file = getFile(e.dataContext)
        val isEnabled = file != null && (PrivacyService.instance.getPrivacy(file) != Privacy.DISABLED
                || InferenceGlobalContext.hasUserInferenceUri())
        isEnabledInModalContext = isEnabled
        e.presentation.isEnabledAndVisible = isEnabled
    }

    private fun getFile(dataContext: DataContext): VirtualFile? {
        return CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext)?.firstOrNull()
    }
}