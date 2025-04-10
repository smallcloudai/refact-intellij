package com.smallcloud.refactai.notifications

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationListener.Adapter
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.refactAIAdvancedSettingsID
import com.smallcloud.refactai.Resources.refactAIRootSettingsID
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.settings.AppSettingsState.Companion.acceptedCompletionCounter
import com.smallcloud.refactai.utils.getLastUsedProject
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import java.net.URL
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.event.HyperlinkEvent
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder
import com.smallcloud.refactai.settings.AppSettingsState.Companion.instance as AppSettingsState

private var lastNotification: Notification? = null
private var lastRegularNotification: Notification? = null
private var lastRateUsNotification: Notification? = null
private var rateUsFuture: Future<*>? = null
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

    if (!AppSettingsState.rateUsNotification) {
        rateUsFuture = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            {
                if (acceptedCompletionCounter.get() >= 30) {
                    emitRateUs()
                }
                if (AppSettingsState.rateUsNotification) {
                    rateUsFuture?.cancel(true)
                }
            }, 2, 2, TimeUnit.MINUTES
        )
    }
}

fun emitRateUs() {
    if (lastRateUsNotification != null) {
        return
    }
    val project = getLastUsedProject()
    val notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group").createNotification(
            Resources.titleStr, RefactAIBundle.message("notifications.rateUs"), NotificationType.INFORMATION,
        ).also { it.setListener(object : Adapter() {
            override fun hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
                val url: URL = event.url
                BrowserUtil.browse(url)
                AppSettingsState.rateUsNotification = true
                notification.expire()
            }
        })}
    notification.icon = Resources.Icons.LOGO_RED_16x16
    notification.notify(project)
    lastRateUsNotification = notification
}

private fun getVirtualFile(editor: Editor): VirtualFile? {
    return FileDocumentManager.getInstance().getFile(editor.document)
}

fun emitRegular(project: Project, editor: Editor) {
    removeLastRegularNotification()
    val notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group").createNotification(
            Resources.titleStr, NotificationType.INFORMATION
        )
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        notification.expire()
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
        ShowSettingsUtilImpl.showSettingsDialog(project, refactAIAdvancedSettingsID, null)
        notification.expire()
    })

    notification.notify(project)
    lastRegularNotification = notification
}

fun emitInfo(msg: String, needToDeleteLast: Boolean = true) {
    if (needToDeleteLast) removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.INFORMATION).setListener(object : Adapter() {
            override fun hyperlinkActivated(notification: Notification, event: HyperlinkEvent) {
                val url: URL = event.url
                BrowserUtil.browse(url)
                notification.expire()
            }
        })
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        notification.expire()
    })
    notification.notify(project)
}

fun emitError(msg: String) {
    removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.ERROR)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.showSettingsDialog(project, refactAIRootSettingsID, null)
        notification.expire()
    })
    notification.notify(project)
    lastNotification = notification
}
