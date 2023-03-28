package com.smallcloud.refactai.notifications

import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.login
import com.smallcloud.refactai.listeners.AIToolboxInvokeAction
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyChangesNotifier
import com.smallcloud.refactai.settings.AppRootConfigurable
import com.smallcloud.refactai.utils.getLastUsedProject
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import com.smallcloud.refactai.privacy.PrivacyService.Companion.instance as PrivacyServiceInstance

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
    ApplicationManager.getApplication().messageBus.connect(PluginState.instance)
        .subscribe(PrivacyChangesNotifier.TOPIC, object : PrivacyChangesNotifier {
            override fun privacyChanged() {
                removeLastRegularNotification()
            }
        })


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

private fun getVirtualFile(editor: Editor): VirtualFile? {
    return FileDocumentManager.getInstance().getFile(editor.document)
}

private fun addDisableEnablePrivacy(
    notification: Notification, virtualFile: VirtualFile, currentPrivacy: Privacy
) {
    if (currentPrivacy != Privacy.DISABLED) {
        notification.addAction(NotificationAction.createSimple(
            RefactAIBundle.message("notifications.disableAccess")
        ) {
            PrivacyServiceInstance.setPrivacy(virtualFile, Privacy.DISABLED)
            notification.expire()
        })
    }
    if (currentPrivacy == Privacy.DISABLED) {
        notification.addAction(NotificationAction.createSimple(
            RefactAIBundle.message("notifications.enableAccess", Resources.titleStr)
        ) {
            PrivacyServiceInstance.setPrivacy(virtualFile, Privacy.ENABLED)
            notification.expire()
        })
    }
}

fun emitLogin(project: Project) {
    removeLastNotification()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(
            RefactAIBundle.message("notifications.loginTo", Resources.titleStr),
            NotificationType.INFORMATION
        )
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.login")) {
        login()
        notification.expire()
    }).addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    }).notify(project)
    lastNotification = notification
}

private fun getStatusPrivacyString(currentPrivacy: Privacy): String {
    return when (currentPrivacy) {
        Privacy.DISABLED -> RefactAIBundle.message("privacy.level0Status")
        Privacy.ENABLED -> RefactAIBundle.message("privacy.level1Name")
        Privacy.THIRDPARTY -> RefactAIBundle.message("privacy.level2Name")
    }
}

fun emitRegular(project: Project, editor: Editor) {
    removeLastRegularNotification()
    val file = getVirtualFile(editor)
    val currentPrivacy = PrivacyServiceInstance.getPrivacy(file)
    val notification =
        NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group").createNotification(
            Resources.titleStr,
            "Privacy: ${getStatusPrivacyString(currentPrivacy)}",
            NotificationType.INFORMATION
        )
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })

    val chat = ToolWindowManager.getInstance(project).getToolWindow("Refact Chat")
    if (chat != null) {
        val chatShortcut = KeymapUtil.getShortcutText("ActivateRefactChatToolWindow")
        notification.addAction(NotificationAction.createSimple("Chat ($chatShortcut)") {
            chat.activate{
                RefactAIToolboxPaneFactory.gptChatPanes?.requestFocus()
            }
            notification.expire()
        })
    }
    val f1Shortcut = KeymapUtil.getShortcutText("RefactAIToolboxAction")
    notification.addAction(DumbAwareAction.create("AI Toolbox ($f1Shortcut)") {
        it.presentation.putClientProperty(Key(CommonDataKeys.EDITOR.name), editor)
        AIToolboxInvokeAction().actionPerformed(it)
        notification.expire()
    })


//    if (file != null) addDisableEnablePrivacy(notification, file, currentPrivacy)
    notification.notify(project)
    lastRegularNotification = notification
}

fun emitInfo(msg: String) {
    removeLastNotification()
    val project = getLastUsedProject()
    val notification = NotificationGroupManager.getInstance().getNotificationGroup("Refact AI Notification Group")
        .createNotification(Resources.titleStr, msg, NotificationType.INFORMATION)
    notification.icon = Resources.Icons.LOGO_RED_16x16

    notification.addAction(NotificationAction.createSimple(RefactAIBundle.message("notifications.settingsAndPrivacy")) {
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
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
        ShowSettingsUtilImpl.getInstance().showSettingsDialog(project, AppRootConfigurable::class.java)
        notification.expire()
    })
    notification.notify(project)
    lastNotification = notification
}
