package com.smallcloud.refact

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.refact.account.LoginStateService
import com.smallcloud.refact.account.login
import com.smallcloud.refact.io.ConnectivityManager
import com.smallcloud.refact.listeners.QuickLongthinkActionsService
import com.smallcloud.refact.listeners.UninstallListener
import com.smallcloud.refact.notifications.notificationStartup
import com.smallcloud.refact.privacy.PrivacyService
import com.smallcloud.refact.settings.AppSettingsState
import com.smallcloud.refact.settings.settingsStartup

class Initializer : StartupActivity.Background, Disposable {

    override fun runActivity(project: Project) {
        initialize(project)
    }

    private fun initialize(project: Project) {
        ConnectivityManager.instance.startup()

        if (!AppSettingsState.instance.startupLoggedIn) {
            AppSettingsState.instance.startupLoggedIn = true
            login()
        } else {
            ApplicationManager.getApplication().getService(LoginStateService::class.java)
                .tryToWebsiteLogin(true)
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
