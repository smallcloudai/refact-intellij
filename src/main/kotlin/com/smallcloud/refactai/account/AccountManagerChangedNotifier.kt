package com.smallcloud.refactai.account

import com.intellij.util.messages.Topic

interface AccountManagerChangedNotifier {

    fun isLoggedInChanged(isLoggedIn: Boolean) {}
    fun planStatusChanged(newPlan: String?) {}
    fun userChanged(newUser: String?) {}
    fun apiKeyChanged(newApiKey: String?) {}
    fun meteringBalanceChanged(newBalance: Int?) {}


    companion object {
        val TOPIC = Topic.create("Account Manager Changed Notifier",
                AccountManagerChangedNotifier::class.java)
    }
}
