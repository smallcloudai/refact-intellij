package com.smallcloud.codify.notifications

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.smallcloud.codify.Resources
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.login
import com.smallcloud.codify.settings.AppRootConfigurable

private fun add_disable_enable(project: Project, notification: Notification) {
    if (SMCPlugin.instant.is_enable) {
        notification.addAction(NotificationAction.createSimple("Disable") {
            SMCPlugin.instant.is_enable = false
            notification.expire()
        })
    } else {
        notification.addAction(NotificationAction.createSimple("Enable") {
            SMCPlugin.instant.is_enable = true
            notification.expire()
        })
    }
}
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

fun emit_regular(project: Project) {
    val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Codify Notification Group")
            .createNotification("Codify", NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    add_disable_enable(project, notification)
    notification.notify(project);
}

fun emit_info(msg: String) {
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Codify Notification Group")
            .createNotification("Codify", msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    add_disable_enable(project, notification)
    notification.notify(project);
}
fun emit_error(msg: String) {
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Codify Notification Group")
            .createNotification("Codify", msg, NotificationType.ERROR)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    add_disable_enable(project, notification)
    notification.notify(project);
}