package com.smallcloud.refactai.account

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.notifications.emitLogin
import com.smallcloud.refactai.struct.DeploymentMode
import com.smallcloud.refactai.utils.getLastUsedProject
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager

const val LOGIN_TIMEOUT = 30_000

class LoginStateService: Disposable {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCLoginStateServiceScheduler", 1
    )
    private var lastTask: Future<*>? = null
    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"
    private var popupLoginMessageOnce: Boolean = false
    private var lastLoginTime: Long = 0
    private var blockedLoginTime: Long = 0
    private var loginCounter: Int = 0


    private fun resetLoginStats() {
        loginCounter = 0
        lastLoginTime = 0
        blockedLoginTime = 0
    }
    init {
        ApplicationManager.getApplication()
                .messageBus
                .connect(PluginState.instance)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                    override fun deploymentModeChanged(newMode: DeploymentMode) {
                        resetLoginStats()
                    }
                })
    }

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    fun tryToWebsiteLogin(force: Boolean = false, fromCounter: Boolean = false): Future<*>? {
        val now = System.currentTimeMillis()
        if (!fromCounter && now - blockedLoginTime < LOGIN_TIMEOUT) {
            return null
        }
        if (!fromCounter) {
            if (now - lastLoginTime > LOGIN_TIMEOUT) {
                loginCounter = 0
            }

            loginCounter++
            if (loginCounter > 3) {
                blockedLoginTime = now
                loginCounter = 0
            }
        }
        lastLoginTime = now

        lastTask = scheduler.submit {
            try {
                Logger.getInstance("check_login").warn("call")
                lastWebsiteLoginStatus = checkLogin(force)
                if (lastWebsiteLoginStatus == "OK") {
                    finishedGood()
                }
                emitLoginIfNeeded()
            } catch (e: Exception) {
                e.message?.let { logError("check_login exception", it) }
                emitLoginIfNeeded()
            } finally {
                popupLoginMessageOnce = true
            }
        }
        return lastTask
    }

    private fun emitLoginIfNeeded() {
        if (!popupLoginMessageOnce && lastWebsiteLoginStatus.isEmpty()) {
            AppExecutorUtil.getAppScheduledExecutorService().schedule({
                if (!AccountManager.isLoggedIn) {
                    emitLogin(getLastUsedProject())
                }
            }, 7, TimeUnit.SECONDS)
        }
    }

    override fun dispose() {
        lastTask?.cancel(true)
        scheduler.shutdown()
    }
}
