package com.smallcloud.codify.account

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class LoginStateService {
    private var inferenceTask: Future<*>? = null

    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"

    init {
        inferenceTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            this::tryToInferenceLogin, 1, 1, TimeUnit.HOURS
        )
    }

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    fun tryToWebsiteLogin(force: Boolean = false) {
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                Logger.getInstance("check_login").warn("call")
                lastWebsiteLoginStatus = checkLogin(force)
            } catch (e: Exception) {
                e.message?.let { logError("check_login exception", it) }
            }
        }
    }

    fun tryToInferenceLogin() {
        try {
            Logger.getInstance("inference_login").warn("call")
            lastInferenceLoginStatus = inferenceLogin()
        } catch (e: Exception) {
            e.message?.let { logError("inference_login exception", it) }
        }
    }
}
