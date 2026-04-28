package com.smallcloud.refactai.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.refactAIAdvancedSettingsID
import com.smallcloud.refactai.Resources.refactAIRootSettingsID
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.utils.getLastUsedProject
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

private var lastNotification: Notification? = null
private var lastRegularNotification: Notification? = null
private fun removeLastNotification() {
    lastNotification?.apply {
        expire()
        hideBalloon()
    }
}

private fun removeLastRegularNotification() {
    lastRegularNotification?.apply {
        expire()
        hideBalloon()
    }
}

fun startup() {
    val focusListener = object : FocusListener {
        override fun focusGained(e: FocusEvent?) {}
        override fun focusLost(e: FocusEvent?) {
            removeLastRegularNotification()
        }
    }

    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
            event.editor.contentComponent.addFocusListener(focusListener)
        }

        override fun editorReleased(event: EditorFactoryEvent) {
            event.editor.contentComponent.removeFocusListener(focusListener)
        }

    }, PluginState.instance)
}

fun emitRegular(project: Project, editor: Editor) {
    removeLastRegularNotification()
    val notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group").createNotification(
            Resources.titleStr, NotificationType.INFORMATION
        )
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        notification.expire()
        AppExecutorUtil.getAppExecutorService().execute {
            ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        }
    })

    notification.addAction(NotificationAction.createSimple(
        if (InferenceGlobalContext.useAutoCompletion) RefactAIBundle.message("notifications.pause") else RefactAIBundle.message(
            "notifications.play"
        )
    ) {
        InferenceGlobalContext.useAutoCompletion = !InferenceGlobalContext.useAutoCompletion
        notification.expire()
    })


    val chat = ToolWindowManager.getInstance(project).getToolWindow("Refact")
    if (chat != null) {
        val chatShortcut = KeymapUtil.getShortcutText("ActivateRefactChatToolWindow")
        notification.addAction(NotificationAction.createSimple("Chat ($chatShortcut)") {
            chat.activate {
                RefactAIToolboxPaneFactory.chat?.requestFocus()
            }
            notification.expire()
        })
    }


    notification.notify(project)
    lastRegularNotification = notification
}

fun emitWarning(project: Project, msg: String) {
    removeLastRegularNotification()
    val notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group").createNotification(
            Resources.titleStr, msg, NotificationType.INFORMATION
        )
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        notification.expire()
        AppExecutorUtil.getAppExecutorService().execute {
            ShowSettingsUtilImpl.showSettingsDialog(project, refactAIAdvancedSettingsID, null)
        }
    })

    notification.notify(project)
    lastRegularNotification = notification
}

fun emitInfo(msg: String, needToDeleteLast: Boolean = true) {
    if (needToDeleteLast) removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        notification.expire()
        AppExecutorUtil.getAppExecutorService().execute {
            ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        }
    })
    notification.notify(project)
}

@Suppress("DialogTitleCapitalization")
fun emitInfoWithDocLink(msg: String, docUrl: String, needToDeleteLast: Boolean = true) {
    if (needToDeleteLast) removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Open Documentation") {
        BrowserUtil.browse(docUrl)
        notification.expire()
    })
    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        notification.expire()
        AppExecutorUtil.getAppExecutorService().execute {
            ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        }
    })
    notification.notify(project)
}

@Suppress("DialogTitleCapitalization")
fun emitChat(project: Project, msg: String, chatId: String? = null) {
    removeLastNotification()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple("Open Chat") {
        notification.expire()
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Refact")
        toolWindow?.activate {
            val panes = RefactAIToolboxPaneFactory.chat
            panes?.requestFocus()
            if (chatId != null) {
                panes?.switchToThread(chatId)
            }
        }
    })
    notification.notify(project)
    lastNotification = notification
}

fun emitError(msg: String) {
    removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.ERROR)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        notification.expire()
        AppExecutorUtil.getAppExecutorService().execute {
            ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        }
    })
    notification.notify(project)
    lastNotification = notification
}
