package com.smallcloud.refactai.notifications

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.refactai.ExtraInfoChangedNotifier
import com.smallcloud.refactai.PluginState

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
