package com.smallcloud.refactai.io

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.Resources.defaultCloudUrl
import java.net.URLConnection
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class ConnectivityManager : Disposable {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCConnectivityManagerScheduler", 1
    )
    private var task: Future<*>? = null

    fun startup() {
        task = scheduler.scheduleWithFixedDelay(
            {
                try {
                    if (InferenceGlobalContext.status == ConnectionStatus.DISCONNECTED /*||
                        InferenceGlobalContext.status == ConnectionStatus.ERROR*/) {
                        val url = defaultCloudUrl.toURL()
                        val connection: URLConnection = url.openConnection()
                        connection.connect()
                    }
                } catch (e: Exception) {
                    // Do nothing
                }
            }, 1, 20, TimeUnit.SECONDS
        )
    }

    companion object {
        @JvmStatic
        val instance: ConnectivityManager
            get() = ApplicationManager.getApplication().getService(ConnectivityManager::class.java)
    }

    override fun dispose() {
        task?.cancel(true)
        task = null
        scheduler.shutdownNow()
    }
}