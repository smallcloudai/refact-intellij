package com.smallcloud.codify.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.Resources.defaultCodifyUrl
import java.net.URLConnection
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class ConnectivityManager : Disposable {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "CodifyConnectivityManagerScheduler", 1
    )
    private var task: Future<*>? = null

    fun startup() {
        task = scheduler.scheduleWithFixedDelay(
            {
                try {
                    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED /*||
                        InferenceGlobalContext.status == ConnectionStatus.ERROR*/) {
                        val url = defaultCodifyUrl.toURL()
                        val connection: URLConnection = url.openConnection()
                        connection.connect()
                        InferenceGlobalContext.reconnect()
                    }
                } catch (e: Exception) {
                    // Do nothing
                }
            }, 1, 1, TimeUnit.SECONDS
        )
    }

    companion object {
        @JvmStatic
        val instance: ConnectivityManager
            get() = ApplicationManager.getApplication().getService(ConnectivityManager::class.java)
    }

    override fun dispose() {
        task?.cancel(true)
        task?.get()
        task = null
    }
}