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
import java.lang.System.currentTimeMillis
import java.lang.System.identityHashCode


class ModeProvider(
    editor: Editor,
    private val modes: List<Mode> = listOf(CompletionMode()),
    private var activeMode: Mode? = null,
    private val pluginState: PluginState = PluginState.instance,
) : Disposable {
    private val isEnabled: Boolean
        get() = pluginState.isEnabled

    init {
        activeMode = modes.firstOrNull()
    }

    fun modeInActiveState(): Boolean = activeMode?.isInActiveState() == true

    fun switchMode() {

    }

    fun beforeDocumentChangeNonBulk(event: DocumentEvent, editor: Editor) {
        if (!isEnabled) return
        if (event.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.beforeDocumentChangeNonBulk(event, editor)
    }


    fun onTextChange(event: DocumentEvent, editor: Editor) {
        if (!isEnabled) return
        if (event.newFragment.toString() == DUMMY_IDENTIFIER) return
        activeMode?.onTextChange(event, editor)
    }

    fun onCaretChange(event: CaretEvent) {
        if (!isEnabled) return
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
