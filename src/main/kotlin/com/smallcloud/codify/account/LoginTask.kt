package com.smallcloud.codify.account

import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.Topic
import com.smallcloud.codify.Resources
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
var loginCooldownCounter: Int = 0

fun runCounterTask() {
    if (loginTask != null && (!loginTask!!.isDone || !loginTask!!.isCancelled)) return

    var i = 1
    loginTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
        loginCooldownCounter = Resources.loginCooldown - (i % Resources.loginCooldown)
        ApplicationManager.getApplication().messageBus.syncPublisher(LoginCounterChangedNotifier.TOPIC)
            .counterChanged(loginCooldownCounter)

        if (i % Resources.loginCooldown == 0) {
            ApplicationManager.getApplication().getService(LoginStateService::class.java).tryToWebsiteLogin()
        }

        if (AccountManager.isLoggedIn || i == Resources.loginCooldown * 10) {
            loginTask?.cancel(false)
        }
        i++
    }, 0, 1, TimeUnit.SECONDS)
}