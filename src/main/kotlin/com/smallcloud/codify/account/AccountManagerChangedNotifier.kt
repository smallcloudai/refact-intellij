package com.smallcloud.codify.account

import com.intellij.util.messages.Topic

interface AccountManagerChangedNotifier {

    fun isLoggedInChanged(limited: Boolean) {}
    fun planStatusChanged(newPlan: String?) {}
    fun ticketChanged(newTicket: String?) {}
    fun userChanged(newUser: String?) {}
    fun apiKeyChanged(newApiKey: String?) {}


    companion object {
        val TOPIC = Topic.create("Account Manager Changed Notifier",
                AccountManagerChangedNotifier::class.java)
    }
}
