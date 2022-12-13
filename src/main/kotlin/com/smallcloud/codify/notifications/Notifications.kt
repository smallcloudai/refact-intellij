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

private fun addDisableEnable(notification: Notification) {
    if (SMCPlugin.instance.isEnable) {
        notification.addAction(NotificationAction.createSimple("Disable") {
            SMCPlugin.instance.isEnable = false
            notification.expire()
        })
    } else {
        notification.addAction(NotificationAction.createSimple("Enable") {
            SMCPlugin.instance.isEnable = true
            notification.expire()
        })
    }
}

fun emitLogin(project: Project) {
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
    }).notify(project)
}

fun emitRegular(project: Project) {
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    addDisableEnable(notification)
    notification.notify(project)
}

fun emitInfo(msg: String) {
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    addDisableEnable(notification)
    notification.notify(project)
}

fun emitError(msg: String) {
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    val notification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", msg, NotificationType.ERROR)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    addDisableEnable(notification)
    notification.notify(project)
}
