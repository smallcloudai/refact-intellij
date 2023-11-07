package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
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
        val listener = LastEditorGetterListener()
        Disposer.register(PluginState.instance, listener)
        EditorFactory.getInstance().addEditorFactoryListener(listener, PluginState.instance)

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
