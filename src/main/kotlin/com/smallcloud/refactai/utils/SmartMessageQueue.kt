package com.smallcloud.refactai.utils

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.Alarm
import com.smallcloud.refactai.panes.sharedchat.Events

class SmartMessageQueue(
    private val maxCommands: Int = 200,
    private val flushDebounceMs: Long = 16L,
    parentDisposable: Disposable
) : Disposable {

    private val logger = Logger.getInstance(SmartMessageQueue::class.java)
    private val lock = Any()

    private var latestConfig: Events.Config.Update? = null
    private var latestActiveFile: Events.ActiveFile.ActiveFileToChat? = null
    private var latestSnippet: Events.Editor.SetSnippetToChat? = null
    private var latestProject: Events.CurrentProject.SetCurrentProject? = null

    private val commands = ArrayDeque<Events.ToChat<*>>(maxCommands)
    private val flushAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable)
    private var flushPending = false
    private var disposed = false
    private var flushCallback: ((List<Events.ToChat<*>>) -> Unit)? = null
    private var readyCheck: (() -> Boolean)? = null

    fun setFlushCallback(callback: (List<Events.ToChat<*>>) -> Unit) {
        flushCallback = callback
    }

    fun setReadyCheck(check: () -> Boolean) {
        readyCheck = check
    }

    fun enqueue(message: Events.ToChat<*>) {
        synchronized(lock) {
            if (disposed) return

            when (message) {
                is Events.Config.Update -> latestConfig = message
                is Events.ActiveFile.ActiveFileToChat -> latestActiveFile = message
                is Events.Editor.SetSnippetToChat -> latestSnippet = message
                is Events.CurrentProject.SetCurrentProject -> latestProject = message
                else -> {
                    if (commands.size >= maxCommands) {
                        commands.removeFirst()
                        logger.debug("Command queue full, dropped oldest")
                    }
                    commands.addLast(message)
                }
            }
            scheduleFlush()
        }
    }

    private fun scheduleFlush() {
        synchronized(lock) {
            if (flushPending || disposed) return
            flushPending = true
        }
        flushAlarm.addRequest({ doFlush() }, flushDebounceMs)
    }

    private fun doFlush() {
        val check = readyCheck
        val shouldReschedule = synchronized(lock) {
            flushPending = false
            disposed || (check != null && !check())
        }
        if (shouldReschedule) {
            scheduleFlush()
            return
        }
        val messages = drain()
        if (messages.isNotEmpty()) {
            flushCallback?.invoke(messages)
        }
    }

    fun flushNow() {
        flushAlarm.cancelAllRequests()
        doFlush()
    }

    fun drain(): List<Events.ToChat<*>> {
        synchronized(lock) {
            val result = mutableListOf<Events.ToChat<*>>()
            latestConfig?.let { result.add(it) }
            latestActiveFile?.let { result.add(it) }
            latestSnippet?.let { result.add(it) }
            latestProject?.let { result.add(it) }
            result.addAll(commands)

            latestConfig = null
            latestActiveFile = null
            latestSnippet = null
            latestProject = null
            commands.clear()
            return result
        }
    }

    fun isEmpty(): Boolean = synchronized(lock) {
        latestConfig == null && latestActiveFile == null &&
        latestSnippet == null && latestProject == null && commands.isEmpty()
    }

    override fun dispose() {
        synchronized(lock) {
            disposed = true
            flushAlarm.cancelAllRequests()
            commands.clear()
            latestConfig = null
            latestActiveFile = null
            latestSnippet = null
            latestProject = null
        }
    }
}
