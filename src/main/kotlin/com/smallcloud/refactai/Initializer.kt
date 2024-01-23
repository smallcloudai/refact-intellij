package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.account.login
import com.smallcloud.refactai.io.ConnectivityManager
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.lspProjectInitialize
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import com.smallcloud.refactai.statistic.UsageStats
import com.smallcloud.refactai.struct.DeploymentMode
import java.util.concurrent.atomic.AtomicBoolean


class Initializer : StartupActivity, Disposable {
     private fun execute(project: Project) {
        val shouldInitialize = !(initialized.getAndSet(true) || ApplicationManager.getApplication().isUnitTestMode)
        if (shouldInitialize) {
            Logger.getInstance("SMCInitializer").info("Bin prefix = ${Resources.binPrefix}")
            ConnectivityManager.instance.startup()

            if (InferenceGlobalContext.instance.canRequest()) {
                when (InferenceGlobalContext.instance.deploymentMode) {
                    DeploymentMode.CLOUD, DeploymentMode.SELF_HOSTED -> {
                        if (!AppSettingsState.instance.startupLoggedIn) {
                            AppSettingsState.instance.startupLoggedIn = true
                            login()
                        } else {
                            ApplicationManager.getApplication().getService(LoginStateService::class.java)
                                .tryToWebsiteLogin(true)
                        }
                    }

                    else -> {}
                }
            }
            settingsStartup()
            notificationStartup()
            UsageStats.instance
            PluginInstaller.addStateListener(UninstallListener())
            UpdateChecker.instance
            LSPProcessHolder.instance.startup()
        }
        PrivacyService.instance.projectOpened(project)
        lspProjectInitialize(ProjectRootManager.getInstance(project).contentRoots.map { it.toString() })
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
