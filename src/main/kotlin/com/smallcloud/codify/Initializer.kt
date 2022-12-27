package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.codify.account.LoginStateService
import com.smallcloud.codify.notifications.notificationStartup
import com.smallcloud.codify.settings.settingsStartup

class Initializer : StartupActivity.Background {

    override fun runActivity(project: Project) {
        initialize()
    }

    private fun initialize() {
        ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin(true)
        settingsStartup()
        notificationStartup()
        UsageStats.instance
    }
}
