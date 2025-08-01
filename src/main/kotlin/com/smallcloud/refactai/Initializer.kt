package com.smallcloud.refactai

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.registry.Registry
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
            if (!JBCefApp.isSupported()) {
                emitInfo(RefactAIBundle.message("notifications.chatCanNotStartWarning"), false)
            }

            // notifications.chatCanFreezeWarning
            // Show warning for 2025.* IDE versions with JCEF out-of-process enabled
            if (JBCefApp.isSupported()) {
                val appInfo = ApplicationInfo.getInstance()
                val is2025 = appInfo.majorVersion == "2025"
                val outOfProc = try {
                    Registry.get("ide.browser.jcef.out-of-process.enabled").asBoolean()
                } catch (_: Throwable) {
                    false
                }
                if (is2025 && outOfProc) {
                    emitInfo(RefactAIBundle.message("notifications.chatCanFreezeWarning"), false)
                }
            }
        }
        getLSPProcessHolder(project)
        project.getService(LSPActiveDocNotifierService::class.java)
    }

    override fun dispose() {
    }

}

private val initialized = AtomicBoolean(false)