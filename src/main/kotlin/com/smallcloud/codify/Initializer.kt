package com.smallcloud.codify

import com.intellij.openapi.application.PreloadingActivity
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.notifications.startup


class Initializer : PreloadingActivity() {
    override fun preload(indicator: ProgressIndicator) {
        initialize()
    }

    private fun initialize() {
        SMCPlugin.startup()
        AccountManager.startup()
        startup()
    }
}