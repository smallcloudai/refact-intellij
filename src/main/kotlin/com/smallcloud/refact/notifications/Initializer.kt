package com.smallcloud.refact.notifications

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.refact.ExtraInfoChangedNotifier
import com.smallcloud.refact.PluginState

fun notificationStartup() {
    ApplicationManager.getApplication()
        .messageBus
        .connect(PluginState.instance)
        .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
            override fun loginMessageChanged(newMsg: String?) {
                if (newMsg != null)
                    emitInfo(newMsg)
            }
            override fun inferenceMessageChanged(newMsg: String?) {
                if (newMsg != null)
                    emitInfo(newMsg)
            }
        })
    startup()
}
