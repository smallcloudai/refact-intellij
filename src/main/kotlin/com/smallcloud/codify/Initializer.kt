package com.smallcloud.codify

import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.codify.account.LoginStateService
import com.smallcloud.codify.account.login
import com.smallcloud.codify.io.ConnectivityManager
import com.smallcloud.codify.listeners.UninstallListener
import com.smallcloud.codify.notifications.notificationStartup
import com.smallcloud.codify.privacy.PrivacyService
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.settings.settingsStartup

class Initializer : StartupActivity.Background {

    override fun runActivity(project: Project) {
        initialize(project)
    }

    private fun initialize(project: Project) {
        ConnectivityManager.instance.startup()

        if (!AppSettingsState.instance.startupLoggedIn) {
            AppSettingsState.instance.startupLoggedIn = true
            login()
        } else {
            ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin(true)
        }
        settingsStartup()
        notificationStartup()
        UsageStats.instance
        PrivacyService.instance.projectOpened(project)
        PluginInstaller.addStateListener(UninstallListener());
    }
}
