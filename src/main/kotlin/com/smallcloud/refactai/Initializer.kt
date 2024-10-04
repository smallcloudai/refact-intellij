package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.lsp.LSPActiveDocNotifierService
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.panes.sharedchat.ChatPaneInvokeAction
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import java.util.concurrent.atomic.AtomicBoolean
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder


class Initializer : StartupActivity, Disposable {
    private fun execute(project: Project) {
        val shouldInitialize = !(initialized.getAndSet(true) || ApplicationManager.getApplication().isUnitTestMode)
        if (shouldInitialize) {
            Logger.getInstance("SMCInitializer").info("Bin prefix = ${Resources.binPrefix}")
            if (AppSettingsState.instance.isFirstStart) {
                AppSettingsState.instance.isFirstStart = false
                ChatPaneInvokeAction().actionPerformed()
            }
            settingsStartup()
            notificationStartup()
            PluginInstaller.addStateListener(UninstallListener())
            UpdateChecker.instance
        }
        getLSPProcessHolder(project)
        project.getService(LSPActiveDocNotifierService::class.java)
        PrivacyService.instance.projectOpened(project)
    }

    override fun dispose() {
    }

    companion object {
        private val initialized = AtomicBoolean(false)
    }

    override fun runActivity(project: Project) {
        execute(project)
    }
}
