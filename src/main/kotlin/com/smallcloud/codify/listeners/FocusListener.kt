package com.smallcloud.codify.listeners

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FocusChangeListener
import com.intellij.openapi.project.Project
import com.smallcloud.codify.modes.ModeProvider

class FocusListener(
    private val project: Project,
) : FocusChangeListener {

    override fun focusGained(editor: Editor) {
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.focusGained()
    }

    override fun focusLost(editor: Editor) {
        val provider = ModeProvider.getOrCreateModeProvider(editor)
        provider.focusLost()
    }
}
