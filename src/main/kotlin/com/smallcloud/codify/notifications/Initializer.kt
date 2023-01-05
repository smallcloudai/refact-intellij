package com.smallcloud.codify.notifications

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.ExtraInfoChangedNotifier
import com.smallcloud.codify.PluginState

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
}
