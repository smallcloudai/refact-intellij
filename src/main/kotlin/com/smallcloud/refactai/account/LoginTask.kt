package com.smallcloud.refactai.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

private var loginTask: Future<*>? = null

fun runCounterTask() {
    if (loginTask != null && (!loginTask!!.isDone || !loginTask!!.isCancelled)) return
    var i = 0
    loginTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
        ApplicationManager.getApplication().getService(LoginStateService::class.java)
                .tryToWebsiteLogin(fromCounter = true)?.get()

        if (AccountManager.instance.isLoggedIn || i == Resources.loginCoolDown) {
            loginTask?.cancel(false)
        }
        i++
    }, 0, 1, TimeUnit.SECONDS)
}