package com.smallcloud.codify.notifications

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.AccountManager

fun notificationStartup() {
    ApplicationManager.getApplication()
        .messageBus
        .connect(SMCPlugin.instance)
        .subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectOpened(project: Project) {
                if (!AccountManager.isLoggedIn)
                    emitLogin(project)
            }
        })
}
