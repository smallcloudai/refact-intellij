package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.account.login
import com.smallcloud.refactai.io.ConnectivityManager
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.listeners.LastEditorGetterListener
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import com.smallcloud.refactai.statistic.UsageStats
import com.smallcloud.refactai.struct.DeploymentMode

class Initializer : ProjectActivity, Disposable {
    override suspend fun execute(project: Project) {
        initialize(project)
    }
    private fun initialize(project: Project) {
        val listener = LastEditorGetterListener.instance
//        Disposer.register(PluginState.instance, listener)

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
        PrivacyService.instance.projectOpened(project)
        PluginInstaller.addStateListener(UninstallListener())
        UpdateChecker.instance
        LSPProcessHolder.instance.startup()
    }

    override fun dispose() {
    }

}
