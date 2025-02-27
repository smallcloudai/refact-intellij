package com.smallcloud.refactai.modes

import com.intellij.openapi.editor.Editor
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ObjectUtils
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBus
import com.intellij.util.xmlb.annotations.Transient
import com.jetbrains.rd.util.getOrCreate
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.listeners.GlobalCaretListener
import com.smallcloud.refactai.listeners.GlobalFocusListener
import com.smallcloud.refactai.modes.completion.StubCompletionMode
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import com.smallcloud.refactai.modes.diff.DiffMode
import com.smallcloud.refactai.modes.diff.DiffModeWithSideEffects
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.UsageStats
import java.lang.System.currentTimeMillis
import java.lang.System.identityHashCode
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


enum class ModeType {
    Completion,
    Diff,
    DiffWithSideEffects,
}

class ModeProvider(
    private val editor: Editor,
    private val modes: MutableMap<ModeType, Mode> = mutableMapOf(
        ModeType.Completion to StubCompletionMode(),
        ModeType.Diff to DiffMode()
    ),
    private var activeMode: Mode? = null,
) : Disposable, InferenceGlobalContextChangedNotifier {

    @Transient
    private val messageBus: MessageBus = ApplicationManager.getApplication().messageBus

    init {
        activeMode = modes[ModeType.Completion]
        messageBus.connect(this).subscribe(
            InferenceGlobalContextChangedNotifier.TOPIC, this
        )
    }

    fun modeInActiveState(): Boolean = activeMode?.isInActiveState() == true

    fun isInCompletionMode(): Boolean =
        activeMode === modes[ModeType.Completion]
    fun isDiffMode(): Boolean = activeMode == modes[ModeType.Diff] || activeMode == modes[ModeType.DiffWithSideEffects]
    fun getCompletionMode(): Mode = modes[ModeType.Completion]!!

    fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.beforeDocumentChangeNonBulk(DocumentEventExtra(event, editor, currentTimeMillis()))
    }

    fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.onTextChange(DocumentEventExtra(event, editor, currentTimeMillis(), force))
    }

    fun onCaretChange(event: CaretEvent) {
        activeMode?.onCaretChange(event)
    }

    fun focusGained() {}

    fun focusLost() {}

    fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        activeMode?.onTabPressed(editor, caret, dataContext)
    }

    fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        activeMode?.onEscPressed(editor, caret, dataContext)
    }

    override fun dispose() {
    }

    fun switchMode(newMode: ModeType = ModeType.Completion) {
        if (activeMode == modes[newMode]) return
        activeMode?.cleanup(editor)
        activeMode = modes[newMode]
    }

    fun removeSideEffects() {
        activeMode?.cleanup(editor)
        modes.remove(ModeType.DiffWithSideEffects)
        activeMode = null
    }

    fun addSideEffects(onTab: (Editor, Caret?, DataContext) -> Unit, onEsc: (Editor, Caret?, DataContext) -> Unit): DiffModeWithSideEffects {
        fun <T> handleFn(fn: T): T {
            removeSideEffects()
            return fn
        }
        val mode = DiffModeWithSideEffects(handleFn(onTab), handleFn(onEsc))
        modes.set(ModeType.DiffWithSideEffects, mode)
        this.switchMode(ModeType.DiffWithSideEffects)
        return mode
    }



    fun getDiffMode(): DiffMode = (modes[ModeType.Diff] as DiffMode?)!!

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
                    try {
                        it.addFocusListener(GlobalFocusListener(), modeProvider)
                    } catch (e: UnsupportedOperationException) {
                        // nothing
                    }
                }
                modeProvider
            }
        }
    }
}

