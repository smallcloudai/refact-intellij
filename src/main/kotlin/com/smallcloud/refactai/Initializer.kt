package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.smallcloud.refactai.io.CloudMessageService
import com.smallcloud.refactai.listeners.ACTION_ID_
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.lsp.LSPActiveDocNotifierService
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.initialize
import com.smallcloud.refactai.notifications.emitInfo
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.panes.sharedchat.ChatPaneInvokeAction
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import com.smallcloud.refactai.utils.isJcefCanStart
import java.util.concurrent.atomic.AtomicBoolean
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

class Initializer : ProjectActivity, Disposable {
    override suspend fun execute(project: Project) {
        val shouldInitialize = !(initialized.getAndSet(true) || ApplicationManager.getApplication().isUnitTestMode)
        if (shouldInitialize) {
            Logger.getInstance("SMCInitializer").info("Bin prefix = ${Resources.binPrefix}")
            initialize()
            if (AppSettingsState.instance.isFirstStart) {
                AppSettingsState.instance.isFirstStart = false
                invokeLater { ChatPaneInvokeAction().actionPerformed() }
            }
            settingsStartup()
            notificationStartup()
            PluginInstaller.addStateListener(UninstallListener())
            UpdateChecker.instance

            ApplicationManager.getApplication().getService(CloudMessageService::class.java)
            if (!isJcefCanStart()) {
                emitInfo(RefactAIBundle.message("notifications.chatCanNotStartWarning"), false)
            }
        }
        getLSPProcessHolder(project)
        project.getService(LSPActiveDocNotifierService::class.java)
        PrivacyService.instance.projectOpened(project)
    }

    override fun dispose() {
    }

}

private val initialized = AtomicBoolean(false)