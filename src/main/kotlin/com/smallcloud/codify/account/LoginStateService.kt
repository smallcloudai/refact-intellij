package com.smallcloud.codify.account

import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.Connection
import com.smallcloud.codify.ConnectionStatus
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


class LoginStateService {
    private var _website_counter: Int = 0
    private var _website_task: Future<*>? = null
    private var _inference_task: Future<*>? = null

    private var lastWebsiteLoginStatus: String = "OK"
    private var lastInferenceLoginStatus: String = "OK"

    init {
        _website_task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            this::try_to_website_login, 0, 10000, TimeUnit.MILLISECONDS
        )
        _inference_task = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(
            this::try_to_inference_login, 1, 1, TimeUnit.HOURS
        )
    }

    fun getLastWebsiteLoginStatus() : String {
        return lastWebsiteLoginStatus
    }
    fun getLastInferenceLoginStatus() : String {
        return lastInferenceLoginStatus
    }

    private fun try_to_website_login() {
        try {
            Logger.getInstance("check_login").warn("call")
            lastWebsiteLoginStatus = check_login(need_force())
        } catch (e: Exception) {
            log_error("check_login exception: $e")
        }
    }

    private fun try_to_inference_login() {
        try {
            Logger.getInstance("inference_login").warn("call")
            lastInferenceLoginStatus = inference_login()
        } catch (e: Exception) {
            log_error("inference_login exception: $e")
        }
    }

    private fun need_force(): Boolean {
        val c = 15
        val need = (_website_counter % c) == 0
        _website_counter = (_website_counter + 1) % c
        return need || Connection.status != ConnectionStatus.CONNECTED
    }
}