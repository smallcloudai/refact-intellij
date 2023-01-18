package com.smallcloud.codify.modes

import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.ObjectUtils
import com.jetbrains.rd.util.getOrCreate
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.listeners.GlobalCaretListener
import com.smallcloud.codify.listeners.GlobalFocusListener
import com.smallcloud.codify.modes.completion.CompletionMode
import com.smallcloud.codify.modes.diff.DiffMode
import com.smallcloud.codify.modes.highlight.HighlightMode
import java.lang.System.currentTimeMillis
import java.lang.System.identityHashCode


enum class ModeType {
    Completion,
    Diff,
    Highlight
}


class ModeProvider(
    editor: Editor,
    private var modes: Map<ModeType, Mode> = mapOf(
        ModeType.Completion to CompletionMode(),
        ModeType.Diff to DiffMode(),
        ModeType.Highlight to HighlightMode()
    ),
    private var activeMode: Mode? = null,
    private val pluginState: PluginState = PluginState.instance,
) : Disposable {
    private val isEnabled: Boolean
        get() = pluginState.isEnabled

    init {
        activeMode = modes[ModeType.Completion]
    }

    fun modeInActiveState(): Boolean = activeMode?.isInActiveState() == true

    fun isInCompletionMode(): Boolean = activeMode === modes[ModeType.Completion]
    fun isInDiffMode(): Boolean = activeMode === modes[ModeType.Diff]
    fun isInHighlightMode(): Boolean = activeMode === modes[ModeType.Highlight]
    fun getCompletionMode(): Mode = modes[ModeType.Completion]!!
    fun getDiffMode(): DiffMode = (modes[ModeType.Diff] as DiffMode?)!!
    fun getHighlightMode(): HighlightMode = (modes[ModeType.Highlight] as HighlightMode?)!!

    fun switchMode(newMode: ModeType = ModeType.Completion) {
        if (activeMode == modes[newMode]) return
        activeMode?.cleanup()
        activeMode = modes[newMode]
    }

    fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor) {
        if (!isEnabled) return
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.beforeDocumentChangeNonBulk(event, editor)
    }


    fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean) {
        if (!isEnabled) return
        if (event?.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.onTextChange(event, editor, force)
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
