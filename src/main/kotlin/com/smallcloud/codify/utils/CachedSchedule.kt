package com.smallcloud.codify.utils

import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit


data class RunnableInfo(
    val runnable: Runnable,
    val delay: Long,
    val unit: TimeUnit
)


class CachedSchedule(
    private val msToWait: Long = 20
) {
    private var cachedRunnable: ArrayDeque<RunnableInfo> = ArrayDeque()
    private val scheduler = AppExecutorUtil.getAppScheduledExecutorService()

    fun schedule(
        delay: Long = 0,
        unit: TimeUnit = TimeUnit.MILLISECONDS,
        command: Runnable
    ) {
        synchronized(this) {
            cachedRunnable.addLast(RunnableInfo(command, delay, unit))
            scheduler.submit { executeTheLast() }
        }
    }

    private fun executeTheLast() {
        Thread.sleep(msToWait)
        synchronized(this) {
            if (cachedRunnable.isEmpty()) {
                return
            }
            val runnable = cachedRunnable.removeLast()
            cachedRunnable.clear()
            scheduler.schedule(runnable.runnable, runnable.delay, runnable.unit)
        }
    }
}