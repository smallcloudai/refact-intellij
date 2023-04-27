package com.smallcloud.refactai.account

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.refactai.settings.AppSettingsState

class AccountManager: Disposable {
    private var previousLoggedInState: Boolean = false

    var ticket: String?
        get() = AppSettingsState.instance.streamlinedLoginTicket
        set(newTicket) {
            if (newTicket == ticket) return
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .ticketChanged(newTicket)
            AppSettingsState.instance.streamlinedLoginTicketWasCreatedTs = if (newTicket == null) null else
                System.currentTimeMillis()
            checkLoggedInAndNotifyIfNeed()
        }

    val ticketCreatedTs: Long?
        get() = AppSettingsState.instance.streamlinedLoginTicketWasCreatedTs

    var user: String?
        get() = AppSettingsState.instance.userLoggedIn
        set(newUser) {
            if (newUser == user) return
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .userChanged(newUser)
            checkLoggedInAndNotifyIfNeed()
        }
    var apiKey: String?
        get() = AppSettingsState.instance.apiKey
        set(newApiKey) {
            if (newApiKey == apiKey) return
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .apiKeyChanged(newApiKey)
            checkLoggedInAndNotifyIfNeed()
        }
    var activePlan: String? = null
        set(newPlan) {
            if (newPlan == field) return
            field = newPlan
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .planStatusChanged(newPlan)
        }

    val isLoggedIn: Boolean
        get() {
            return !apiKey.isNullOrEmpty() && !user.isNullOrEmpty()
        }

    var meteringBalance: Int = 0

    private fun loadFromSettings() {
        previousLoggedInState = isLoggedIn
    }

    fun startup() {
        loadFromSettings()
    }

    private fun checkLoggedInAndNotifyIfNeed() {
        if (previousLoggedInState == isLoggedIn) return
        previousLoggedInState = isLoggedIn
        loginChangedNotify(isLoggedIn)
    }

    private fun loginChangedNotify(isLoggedIn: Boolean) {
        ApplicationManager.getApplication()
                .messageBus
                .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                .isLoggedInChanged(isLoggedIn)
    }

    fun logout() {
        apiKey = null
        user = null
    }

    override fun dispose() {}

    companion object {
        @JvmStatic
        val instance: AccountManager
            get() = ApplicationManager.getApplication().getService(AccountManager::class.java)
    }
}
