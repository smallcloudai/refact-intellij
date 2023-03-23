package com.smallcloud.refact.listeners

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.smallcloud.refact.modes.ModeProvider

class GlobalFocusListener : FocusChangeListener {
    override fun focusGained(editor: Editor) {
        Logger.getInstance("FocusListener").debug("focusGained")
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.focusGained()
    }

    override fun focusLost(editor: Editor) {
        Logger.getInstance("FocusListener").debug("focusLost")
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.focusLost()
    }
}
