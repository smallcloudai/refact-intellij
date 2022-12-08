package com.smallcloud.codify.account

import com.intellij.util.messages.Topic

interface LoginStatusChangedNotifier {
    fun isLoginChanged(limited: Boolean)
    companion object {
        val LOGIN_STATUS_CHANGED_TOPIC = Topic.create("Login Status Changed Notifier",
                LoginStatusChangedNotifier::class.java)
    }
}