package com.smallcloud.codify

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.codify.notifications.notification_startup
import com.smallcloud.codify.settings.settings_startup


class Initializer : PreloadingActivity(), StartupActivity {
    override fun preload(indicator: ProgressIndicator) {
        initialize()
    }

    override fun runActivity(project: Project) {
        initialize()
    }
    private fun initialize() {
        settings_startup()
        notification_startup()
    }
}