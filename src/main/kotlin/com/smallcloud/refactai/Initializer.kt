package com.smallcloud.refactai

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.smallcloud.refactai.io.CloudMessageService
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.lsp.LSPActiveDocNotifierService
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.initialize
import com.smallcloud.refactai.notifications.emitInfo
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.panes.sharedchat.ChatPaneInvokeAction
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import java.util.concurrent.atomic.AtomicBoolean
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

class Initializer : ProjectActivity, Disposable {
    private val logger = Logger.getInstance("SMCInitializer")

    override suspend fun execute(project: Project) {
        val shouldInitialize = !(initialized.getAndSet(true) || ApplicationManager.getApplication().isUnitTestMode)
        if (shouldInitialize) {
            logger.info("Bin prefix = ${Resources.binPrefix}")
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

            checkJcefStatus()
        }
        getLSPProcessHolder(project)
        project.getService(LSPActiveDocNotifierService::class.java)
    }

    private fun checkJcefStatus() {
        if (!JBCefApp.isSupported()) {
            emitInfo(RefactAIBundle.message("notifications.chatCanNotStartWarning"), false)
            return
        }

        val configFailed = JcefConfigurer.configurationFailed()
        if (configFailed != null) {
            logger.warn("JCEF auto-configuration failed: $configFailed")
            emitInfo(RefactAIBundle.message("notifications.chatCanFreezeWarning"), false)
            return
        }

        if (JcefConfigurer.wasAutoConfigured()) {
            logger.info("JCEF was auto-configured successfully")
            return
        }

        if (JcefConfigurer.isAffectedVersion() && JcefConfigurer.isOutOfProcessEnabled()) {
            logger.warn("Affected JCEF version detected, auto-config may not have applied")
            emitInfo(RefactAIBundle.message("notifications.chatCanFreezeWarning"), false)
        }
    }

    override fun dispose() {
    }
}

private val initialized = AtomicBoolean(false)