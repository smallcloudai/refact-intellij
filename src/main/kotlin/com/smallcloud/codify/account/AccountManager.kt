package com.smallcloud.codify.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.settings.AppSettingsState
import com.smallcloud.codify.struct.PlanType
import com.smallcloud.codify.utils.dispatch
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

object AccountManager {
    private var _active_plan: PlanType = PlanType.UNKNOWN
    private var _task: Future<*>? = null
    private var _ticket: String? = null
    private var _apiKey: String? = null
    private var _user: String? = null
    private var _previous_logged_in_state: Boolean = false

    var ticket: String?
        get() = _ticket
        set(newTicket) {
            if (newTicket != _ticket) {
                _ticket = newTicket
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .ticketChanged(_ticket)
                }
                check_logged_in_and_notify_if_need()
            }
        }

    var user: String?
        get() = _user
        set(newUser) {
            if (newUser != _user) {
                _user = newUser
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .userChanged(_user)
                }
                check_logged_in_and_notify_if_need()
            }
        }
    var apiKey: String?
        get() = _apiKey
        set(newApiKey) {
            if (newApiKey != _apiKey) {
                _apiKey = newApiKey
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .apiKeyChanged(_apiKey)
                }
                check_logged_in_and_notify_if_need()
            }
        }
    var active_plan: PlanType
        get() = _active_plan
        set(newPlan) {
            if (newPlan != _active_plan) {
                _active_plan = newPlan
                dispatch {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(AccountManagerChangedNotifier.TOPIC)
                            .planStatusChanged(_active_plan)
                }
            }
        }


    val is_logged_in: Boolean
        get() {
            return !_apiKey.isNullOrEmpty() and !_user.isNullOrEmpty()
        }


    private fun load_from_settings(settings: AppSettingsState) {
        _ticket = settings.streamlined_login_ticket
        _user = settings.user_logged_in
        _apiKey = settings.apiKey
        _active_plan = settings.active_plan
        _previous_logged_in_state = is_logged_in
    }

    fun startup(settings: AppSettingsState) {
        if (_task != null) return

        load_from_settings(settings)

        _task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                {
                    try {
                        check_login()
                    } catch (e: Exception) {
                        Logger.getInstance(SMCPlugin::class.java).warn("check_login exception: $e")
                    }
                }, 10000, 10000, TimeUnit.MILLISECONDS)
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