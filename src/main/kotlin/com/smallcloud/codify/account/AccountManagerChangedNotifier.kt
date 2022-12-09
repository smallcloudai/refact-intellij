package com.smallcloud.codify.account

import com.intellij.util.messages.Topic
import com.smallcloud.codify.struct.PlanType

interface AccountManagerChangedNotifier {

    fun isLoggedInChanged(limited: Boolean) {}
    fun planStatusChanged(newPlan: PlanType) {}
    fun ticketChanged(newTicket: String?) {}
    fun userChanged(newUser: String?) {}
    fun apiKeyChanged(newApiKey: String?) {}


    companion object {
        val TOPIC = Topic.create("Account Manager Changed Notifier",
                AccountManagerChangedNotifier::class.java)
    }
}