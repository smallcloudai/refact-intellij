package com.smallcloud.codify.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.PlanType
import com.smallcloud.codify.utils.dispatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object AccountManager {
    private var _previous_logged_in_state: Boolean = false

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
    var active_plan: PlanType
        get() = AppSettingsState.instance.activePlan
        set(newPlan) {
            if (newPlan != active_plan) {
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .planStatusChanged(newPlan)
                }
            }
        }


    val is_logged_in: Boolean
        get() {
            return !apiKey.isNullOrEmpty() and !user.isNullOrEmpty()
        }


    private fun load_from_settings(settings: AppSettingsState) {
        _previous_logged_in_state = is_logged_in
    }

    fun startup(settings: AppSettingsState) {
        load_from_settings(settings)
    }

    private fun check_logged_in_and_notify_if_need() {
        if (_previous_logged_in_state != is_logged_in) {
            _previous_logged_in_state = is_logged_in
            login_changed_notify(is_logged_in)
        }
    }

    private fun login_changed_notify(is_logged_in: Boolean) {
        dispatch {
            ApplicationManager.getApplication()
                    .messageBus
                    .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                    .isLoggedInChanged(is_logged_in)
        }
    }

    fun logout() {
        apiKey = null
        user = null
    }
}