package com.smallcloud.refactai.io


import com.intellij.util.messages.Topic

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
    PENDING,
    DISCONNECTED,
    ERROR
}
