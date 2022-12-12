package com.smallcloud.codify.modes

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.jetbrains.rd.util.getOrCreate
import com.smallcloud.codify.listeners.CaretListener
import com.smallcloud.codify.listeners.FocusListener
import com.smallcloud.codify.modes.completion.CompletionProvider
import java.lang.System.currentTimeMillis
import java.lang.System.identityHashCode


class ModeProvider(
    private val editor: Editor,
    private var modes: List<Mode> = listOf(CompletionMode()),
    private var activeMode: Mode? = null,
) : Disposable {
    init {
        modes = listOf()
        activeMode = modes[0]
    }

    fun switchMode() {
    }

    fun focusGained() {

    }

    fun focusLost() {

    }

    fun beforeDocumentChangeNonBulk(event: DocumentEvent) {

    }


    fun onTextChange(event: DocumentEvent) {

    }

    fun onCaretChange(event: CaretEvent) {

    }

    fun onTabPressed(dataContext: DataContext) {

    }

    fun onEscPressed(dataContext: DataContext) {

    }

    override fun dispose() {
        TODO("Not yet implemented")
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
                providersToTs[hashId] = currentTimeMillis()
                ModeProvider(editor)
            }
        }
    }
}
