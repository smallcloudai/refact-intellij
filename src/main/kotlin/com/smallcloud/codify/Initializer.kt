package com.smallcloud.codify

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.codify.notifications.notificationStartup
import com.smallcloud.codify.settings.settingsStartup

class Initializer : StartupActivity.Background {

    override fun runActivity(project: Project) {
        initialize()
    }

    private fun initialize() {
        settingsStartup()
        notificationStartup()
    }
}
