package com.smallcloud.codify.notifications

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.smallcloud.codify.Resources
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.account.login
import com.smallcloud.codify.settings.AppRootConfigurable

private var lastNotification: Notification? = null
private fun removeLastNotification() {
    lastNotification?.expire()
    lastNotification?.hideBalloon()
}

private fun addDisableEnable(notification: Notification) {
    if (PluginState.instance.isEnabled) {
        notification.addAction(NotificationAction.createSimple("Disable") {
            PluginState.instance.isEnabled = false
            notification.expire()
        })
    } else {
        notification.addAction(NotificationAction.createSimple("Enable") {
            PluginState.instance.isEnabled = true
            notification.expire()
        })
    }
}

fun emitLogin(project: Project) {
    removeLastNotification()
    lastNotification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Login to Codify", NotificationType.INFORMATION)
    lastNotification!!.icon = Resources.Icons.LOGO_RED_16x16

    lastNotification!!.addAction(NotificationAction.createSimple("Login") {
        login()
        lastNotification!!.expire()
    }).addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        lastNotification!!.expire()
    }).notify(project)
}

fun emitRegular(project: Project) {
    removeLastNotification()
    lastNotification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", NotificationType.INFORMATION)
    lastNotification!!.icon = Resources.Icons.LOGO_RED_16x16

    lastNotification!!.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        lastNotification!!.expire()
    })
    addDisableEnable(lastNotification!!)
    lastNotification!!.notify(project)
}

fun emitInfo(msg: String) {
    removeLastNotification()
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    lastNotification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", msg, NotificationType.INFORMATION)
    lastNotification!!.icon = Resources.Icons.LOGO_RED_16x16

    lastNotification!!.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        lastNotification!!.expire()
    })
    addDisableEnable(lastNotification!!)
    lastNotification!!.notify(project)
}

fun emitError(msg: String) {
    removeLastNotification()
    val project = ProjectManager.getInstance().openProjects.firstOrNull() ?: return
    lastNotification = NotificationGroupManager.getInstance()
        .getNotificationGroup("Codify Notification Group")
        .createNotification("Codify", msg, NotificationType.ERROR)
    lastNotification!!.icon = Resources.Icons.LOGO_RED_16x16

    lastNotification!!.addAction(NotificationAction.createSimple("Settings") {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        lastNotification!!.expire()
    })
    addDisableEnable(lastNotification!!)
    lastNotification!!.notify(project)
}
