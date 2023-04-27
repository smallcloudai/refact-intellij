package com.smallcloud.refactai.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.smallcloud.refactai.Resources
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit


interface LoginCounterChangedNotifier {
    fun counterChanged(value: Int) {}

    companion object {
        val TOPIC = Topic.create(
            "Internal Login Counter Changed Notifier", LoginCounterChangedNotifier::class.java
        )
    }
}

private var loginTask: Future<*>? = null
var loginCoolDownCounter: Int = 0

fun runCounterTask() {
    if (loginTask != null && (!loginTask!!.isDone || !loginTask!!.isCancelled)) return

    var i = 1
    loginTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
        loginCoolDownCounter = Resources.loginCoolDown - (i % Resources.loginCoolDown)
        ApplicationManager.getApplication().messageBus.syncPublisher(LoginCounterChangedNotifier.TOPIC)
            .counterChanged(loginCoolDownCounter)

        if (i % Resources.loginCoolDown == 0) {
            ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin(fromCounter = true)
        }

        if (AccountManager.instance.isLoggedIn || i == Resources.loginCoolDown * 10) {
            loginTask?.cancel(false)
        }
        i++
    }, 0, 1, TimeUnit.SECONDS)
}