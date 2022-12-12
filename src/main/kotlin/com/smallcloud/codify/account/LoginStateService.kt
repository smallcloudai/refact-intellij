package com.smallcloud.codify.account

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class LoginStateService {
    private var websiteCounter: Int = 0
    private var websiteTask: Future<*>? = null
    private var inferenceTask: Future<*>? = null

    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"

    init {
        websiteTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            this::try_to_website_login, 0, 10000, TimeUnit.MILLISECONDS
        )
        inferenceTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            this::try_to_inference_login, 1, 1, TimeUnit.HOURS
        )
    }

    fun getLastWebsiteLoginStatus(): String {
        return lastWebsiteLoginStatus
    }

    fun getLastInferenceLoginStatus(): String {
        return lastInferenceLoginStatus
    }

    private fun try_to_website_login() {
        try {
            Logger.getInstance("check_login").warn("call")
            lastWebsiteLoginStatus = checkLogin(need_force())
        } catch (e: Exception) {
            logError("check_login exception: $e")
        }
    }

    private fun try_to_inference_login() {
        try {
            Logger.getInstance("inference_login").warn("call")
            lastInferenceLoginStatus = inferenceLogin()
        } catch (e: Exception) {
            logError("inference_login exception: $e")
        }
    }

    private fun need_force(): Boolean {
        val c = 15
        val need = (websiteCounter % c) == 0
        websiteCounter = (websiteCounter + 1) % c
        return need || Connection.status != ConnectionStatus.CONNECTED
    }
}
