package com.smallcloud.codify.modes

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
import com.smallcloud.codify.listeners.CaretListener
import com.smallcloud.codify.listeners.FocusListener
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

    fun switchMode() {

    }

    fun focusGained() {
        if (!isEnabled) return
        activeMode?.focusGained()
    }


    fun focusLost() {
        if (!isEnabled) return
        activeMode?.focusLost()
    }

    fun beforeDocumentChangeNonBulk(event: DocumentEvent, editor: Editor) {
        if (!isEnabled) return
        activeMode?.beforeDocumentChangeNonBulk(event, editor)
    }


    fun onTextChange(event: DocumentEvent, editor: Editor) {
        if (!isEnabled) return
        activeMode?.onTextChange(event, editor)
    }

    fun onCaretChange(event: CaretEvent) {
        if (!isEnabled) return
        activeMode?.onCaretChange(event)
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
                editor.caretModel.addCaretListener(CaretListener())
                ObjectUtils.consumeIfCast(editor, EditorEx::class.java) {
                    it.addFocusListener(FocusListener(), modeProvider)
                }
                modeProvider
            }
        }
    }
}
