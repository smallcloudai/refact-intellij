package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.account.login
import com.smallcloud.refactai.io.ConnectivityManager
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.listeners.QuickLongthinkActionsService
import com.smallcloud.refactai.listeners.UninstallListener
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.privacy.PrivacyService
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import com.smallcloud.refactai.statistic.UsageStats
import com.smallcloud.refactai.struct.DeploymentMode

class Initializer : StartupActivity.Background, Disposable {

    override fun runActivity(project: Project) {
        initialize(project)
    }

    private fun initialize(project: Project) {
        ConnectivityManager.instance.startup()

        InferenceGlobalContext.instance.inferenceUri?.let {
            InferenceGlobalContext.instance.checkConnection(it)
        }
        if (InferenceGlobalContext.instance.canRequest()) {
            when (InferenceGlobalContext.instance.deploymentMode) {
                DeploymentMode.CLOUD -> {
                    if (!AppSettingsState.instance.startupLoggedIn) {
                        AppSettingsState.instance.startupLoggedIn = true
                        login()
                    } else {
                        ApplicationManager.getApplication().getService(LoginStateService::class.java)
                                .tryToWebsiteLogin(true)
                    }
                }

                DeploymentMode.SELF_HOSTED -> {
                    ApplicationManager.getApplication().getService(LoginStateService::class.java)
                            .tryToWebsiteLogin(true)
                }
            }
        }
        settingsStartup()
        notificationStartup()
        UsageStats.instance
        PrivacyService.instance.projectOpened(project)
        PluginInstaller.addStateListener(UninstallListener())
        UpdateChecker.instance
        QuickLongthinkActionsService.instance
    }

    override fun dispose() {
    }
}
