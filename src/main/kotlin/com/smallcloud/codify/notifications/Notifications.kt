package com.smallcloud.codify.notifications

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.smallcloud.codify.Resources
import com.smallcloud.codify.account.login
import com.smallcloud.codify.settings.AppRootConfigurable


fun emit_login(project: Project) {
    val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Codify Notification Group")
            .createNotification("Login to Codify", NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Login") {
        login()
        notification.expire()
    }).addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    }).notify(project);
}
