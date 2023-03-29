package com.smallcloud.refactai.modes

import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.rd.util.getOrCreate
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.listeners.GlobalCaretListener
import com.smallcloud.refactai.listeners.GlobalFocusListener
import com.smallcloud.refactai.modes.completion.CompletionMode
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import com.smallcloud.refactai.modes.diff.DiffMode
import com.smallcloud.refactai.modes.highlight.HighlightMode
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.UsageStats
import java.lang.System.currentTimeMillis
import java.lang.System.identityHashCode
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit


enum class ModeType {
    Completion,
    Diff,
    Highlight
}


class ModeProvider(
    private val editor: Editor,
    private val modes: Map<ModeType, Mode> = mapOf(
        ModeType.Completion to CompletionMode(),
        ModeType.Diff to DiffMode(),
        ModeType.Highlight to HighlightMode()
    ),
    private var activeMode: Mode? = null,
    private val pluginState: PluginState = PluginState.instance,
) : Disposable, InferenceGlobalContextChangedNotifier {
    private val isEnabled: Boolean
        get() = pluginState.isEnabled

    @Transient
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus
    private val beforeTextChangeEventsQueue: ConcurrentLinkedQueue<DocumentEventExtra> = ConcurrentLinkedQueue()
    private val onTextChangeEventsQueue: ConcurrentLinkedQueue<DocumentEventExtra> = ConcurrentLinkedQueue()
    private val eventDebounceScheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCEventDebounceScheduler", 1
    )
    private val stats: UsageStats
        get() = ApplicationManager.getApplication().getService(UsageStats::class.java)

    init {
        activeMode = modes[ModeType.Completion]
        messageBus.connect(this).subscribe(
            InferenceGlobalContextChangedNotifier.TOPIC, this
        )
        eventDebounceScheduler.scheduleWithFixedDelay({
            checkAndSendEvents()
        }, 0, 2, TimeUnit.MILLISECONDS)
    }

    private fun checkAndSendEvents() {
        if (beforeTextChangeEventsQueue.isEmpty()) {
            return
        }

        val now = currentTimeMillis()
        val oldestBeforeTextEvent = beforeTextChangeEventsQueue.first()
        if (now - oldestBeforeTextEvent.ts < 20) {
            return
        }

        val beforeEvents: MutableList<DocumentEventExtra> = mutableListOf()
        val onTextEvents: MutableList<DocumentEventExtra> = mutableListOf()
        beforeTextChangeEventsQueue.forEach { beforeEvents.add(it) }
        onTextChangeEventsQueue.forEach { onTextEvents.add(it) }
        beforeTextChangeEventsQueue.clear()
        onTextChangeEventsQueue.clear()

        val (beforeEvent, afterEvent) = EventAdapter.eventProcess(beforeEvents, onTextEvents)

        try {
            beforeEvent?.let { activeMode?.beforeDocumentChangeNonBulk(it) }
            afterEvent?.let { activeMode?.onTextChange(it) }
        } catch (e: Exception) {
            InferenceGlobalContext.status = ConnectionStatus.ERROR
            InferenceGlobalContext.lastErrorMsg = e.message
            stats.addStatistic(
                false, UsageStatistic("uncaught exceptions"), "none",
                e.toString()
            )
        }
    }

    fun modeInActiveState(): Boolean = activeMode?.isInActiveState() == true

    fun isInCompletionMode(): Boolean =
        activeMode === modes[ModeType.Completion]

    fun isInDiffMode(): Boolean = activeMode === modes[ModeType.Diff]
    fun isInHighlightMode(): Boolean = activeMode === modes[ModeType.Highlight]
    fun getCompletionMode(): Mode = modes[ModeType.Completion]!!

    fun getDiffMode(): DiffMode = (modes[ModeType.Diff] as DiffMode?)!!
    fun getHighlightMode(): HighlightMode = (modes[ModeType.Highlight] as HighlightMode?)!!

    fun switchMode(newMode: ModeType = ModeType.Completion) {
        if (activeMode == modes[newMode]) return
        activeMode?.cleanup(editor)
        activeMode = modes[newMode]
    }

    fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        if (!isEnabled) return
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        beforeTextChangeEventsQueue.add(DocumentEventExtra(event, editor, currentTimeMillis()))
    }

    fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
        if (!isEnabled) return
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        onTextChangeEventsQueue.add(DocumentEventExtra(event, editor, currentTimeMillis(), force))
    }

    fun onCaretChange(event: CaretEvent) {
        if (!isEnabled) return
        activeMode?.onCaretChange(event)
    }

    fun focusGained() {
        if (!isEnabled) return
    }

    fun focusLost() {
        if (!isEnabled) return
    }

    fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (!isEnabled) return
        activeMode?.onTabPressed(editor, caret, dataContext)
    }

    fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        if (!isEnabled) return
        activeMode?.onEscPressed(editor, caret, dataContext)
    }

    override fun dispose() {
    }

    companion object {
        private const val MAX_EDITORS: Int = 8
        private var modeProviders: LinkedHashMap<Int, ModeProvider> = linkedMapOf()
        private var providersToTs: LinkedHashMap<Int, Long> = linkedMapOf()

        fun getOrCreateModeProvider(editor: Editor): ModeProvider {
            val hashId = identityHashCode(editor)
            if (modeProviders.size > MAX_EDITORS) {
                val toRemove = providersToTs.minByOrNull { it.value }?.key
                providersToTs.remove(toRemove)
                modeProviders.remove(toRemove)
            }
            return modeProviders.getOrCreate(hashId) {
                val modeProvider = ModeProvider(editor)
                providersToTs[hashId] = currentTimeMillis()
                editor.caretModel.addCaretListener(GlobalCaretListener())
                ObjectUtils.consumeIfCast(editor, EditorEx::class.java) {
                    it.addFocusListener(GlobalFocusListener(), modeProvider)
                }
                modeProvider
            }
        }
    }
}
