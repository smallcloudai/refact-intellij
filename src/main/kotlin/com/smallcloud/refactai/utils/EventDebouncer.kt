package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.util.Alarm

class EventDebouncer<T>(
    private val delayMs: Long,
    parentDisposable: Disposable,
    private val action: (T) -> Unit
) {
    private val alarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    @Volatile
    private var pendingValue: T? = null

    fun debounce(value: T) {
        pendingValue = value
        alarm.cancelAllRequests()
        alarm.addRequest({
            pendingValue?.let(action)
            pendingValue = null
        }, delayMs)
    }

    fun cancel() {
        alarm.cancelAllRequests()
        pendingValue = null
    }

    fun flush() {
        alarm.cancelAllRequests()
        pendingValue?.let(action)
        pendingValue = null
    }
}
