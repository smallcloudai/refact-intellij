package com.smallcloud.codify

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic
import com.smallcloud.codify.utils.dispatch

interface ConnectionChangedNotifier {

    fun statusChanged(newStatus: ConnectionStatus) {}
    fun lastErrorMsgChanged(newMsg: String?) {}

    companion object {
        val TOPIC = Topic.create("Connection Changed Notifier",
                ConnectionChangedNotifier::class.java)
    }
}

enum class ConnectionStatus {
    CONNECTED,
    DISCONNECTED,
    ERROR
}

object Connection {
    private var _status: ConnectionStatus = ConnectionStatus.CONNECTED
    private var _lastErrorMsg: String? = null

    var status: ConnectionStatus
        get() = _status
        set(newStatus) {
            if (_status != newStatus) {
                _status = newStatus
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(ConnectionChangedNotifier.TOPIC)
                            .statusChanged(_status)
                }
            }
        }
    var last_error_msg: String?
        get() = _lastErrorMsg
        set(newMsg) {
            if (_lastErrorMsg != newMsg) {
                _lastErrorMsg = newMsg
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(ConnectionChangedNotifier.TOPIC)
                            .lastErrorMsgChanged(_lastErrorMsg)
                }
            }
        }

}