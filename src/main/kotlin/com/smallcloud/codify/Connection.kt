package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.smallcloud.codify.utils.dispatch

interface ConnectionChangedNotifier {
    fun statusChanged(newStatus: ConnectionStatus) {}
    fun lastErrorMsgChanged(newMsg: String?) {}

    companion object {
        val TOPIC = Topic.create(
            "Connection Changed Notifier",
            ConnectionChangedNotifier::class.java
        )
    }
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR
}

object Connection {
    var status: ConnectionStatus = ConnectionStatus.CONNECTED
        set(newStatus) {
            if (field != newStatus) {
                field = newStatus
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ConnectionChangedNotifier.TOPIC)
                        .statusChanged(field)
                }
            }
        }
    var last_error_msg: String? = null
        get() = field
        set(newMsg) {
            if (field != newMsg) {
                field = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(ConnectionChangedNotifier.TOPIC)
                        .lastErrorMsgChanged(field)
                }
            }
        }
}
