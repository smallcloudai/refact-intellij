package com.smallcloud.codify.account

import com.intellij.openapi.application.ApplicationManager
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.PlanType
import com.smallcloud.codify.utils.dispatch

object AccountManager {
    private var previousLoggedInState: Boolean = false

    var ticket: String?
        get() = AppSettingsState.instance.streamlinedLoginTicket
        set(newTicket) {
            if (newTicket != ticket) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .ticketChanged(newTicket)
                }
                check_logged_in_and_notify_if_need()
            }
        }

    var user: String?
        get() = AppSettingsState.instance.userLoggedIn
        set(newUser) {
            if (newUser != user) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .userChanged(newUser)
                }
                check_logged_in_and_notify_if_need()
            }
        }
    var apiKey: String?
        get() = AppSettingsState.instance.apiKey
        set(newApiKey) {
            if (newApiKey != apiKey) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .apiKeyChanged(newApiKey)
                }
                check_logged_in_and_notify_if_need()
            }
        }
    var activePlan: PlanType
        get() = AppSettingsState.instance.activePlan
        set(newPlan) {
            if (newPlan != activePlan) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .planStatusChanged(newPlan)
                }
            }
        }


    val isLoggedIn: Boolean
        get() {
            return !apiKey.isNullOrEmpty() and !user.isNullOrEmpty()
        }


    private fun loadFromSettings() {
        previousLoggedInState = isLoggedIn
    }

    fun startup() {
        loadFromSettings()
    }

    private fun check_logged_in_and_notify_if_need() {
        if (previousLoggedInState != isLoggedIn) {
            previousLoggedInState = isLoggedIn
            loginChangedNotify(isLoggedIn)
        }
    }

    private fun loginChangedNotify(isLoggedIn: Boolean) {
        dispatch {
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .isLoggedInChanged(isLoggedIn)
        }
    }

    fun logout() {
        apiKey = null
        user = null
    }
}
