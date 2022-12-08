package com.smallcloud.codify.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.settings.AppSettingsState
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private enum class PlanState {
    UNKNOWN,
    TRIAL,
    SELF_HOSTED,
    STANDARD
}


object AccountManager {
    private var planStatus: PlanState = PlanState.UNKNOWN
    private val settings = AppSettingsState.instance
    private var task: Future<*>? = null
    private var isLogin = false

    val is_login: Boolean
        get() {return isLogin}


    fun startup() {
        if (task != null) return

        check_login()
        isLogin = check_logined()
        task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
                {
                    try {
                        check_login()
                    } catch (e: Exception) {
                        Logger.getInstance(SMCPlugin::class.java).warn("check_login exception: $e")
                    }
                    update_and_notify()
                }, 10000, 10000, TimeUnit.MILLISECONDS)
    }

    private fun check_logined(): Boolean {
        return !settings.token.isNullOrEmpty() && !settings.userLogged.isNullOrEmpty()
    }

    private fun login_changed_notify(is_logined: Boolean) {
        ApplicationManager.getApplication()
                .invokeLater {
                    ApplicationManager.getApplication()
                            .messageBus
                            .syncPublisher(LoginStatusChangedNotifier.LOGIN_STATUS_CHANGED_TOPIC)
                            .isLoginChanged(is_logined)
                }
    }

    private fun force_change_is_login(is_login: Boolean) {
        if (isLogin != is_login) {
            login_changed_notify(is_login)
            isLogin = is_login
        }
    }
    fun logout() {
        settings.token = null
        settings.userLogged = null
        force_change_is_login(false)
    }

    private fun update_and_notify() {
        val new_login_state = check_logined()
        force_change_is_login(new_login_state)
    }
}