package com.smallcloud.codify

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.smallcloud.codify.notifications.notification_startup
import com.smallcloud.codify.settings.settings_startup


class Initializer : PreloadingActivity() {
    override fun preload(indicator: ProgressIndicator) {
        initialize()
    }

    private fun initialize() {
        SMCPlugin.startup()
        notification_startup()
        settings_startup()
    }
}