package com.smallcloud.codify.account

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.notifications.emitLogin
import com.smallcloud.codify.utils.getLastUsedProject
import java.util.concurrent.Future


class LoginStateService: Disposable {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "CodifyLoginStateServiceScheduler", 1
    )
    private var lastTask: Future<*>? = null
    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"
    private var popupLoginMessageOnce: Boolean = false
    private var lastLoginTime: Long = 0
    private var loginCounter: Int = 0

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    fun tryToWebsiteLogin(force: Boolean = false, fromCounter: Boolean = false): Future<*>? {
        if (!fromCounter && System.currentTimeMillis() - lastLoginTime < 30_000) {
            return null
        }
        if (!fromCounter) {
            loginCounter++
            if (loginCounter > 3) {
                lastLoginTime = System.currentTimeMillis()
                loginCounter = 0
            }
        }
        lastTask = scheduler.submit {
            try {
                Logger.getInstance("check_login").warn("call")
                lastWebsiteLoginStatus = checkLogin(force)
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
            emitLogin(getLastUsedProject())
        }
    }

    override fun dispose() {
        lastTask?.cancel(true)
        scheduler.shutdown()
    }
}
