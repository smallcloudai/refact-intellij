package com.smallcloud.codify.listeners

import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.project.Project
import com.smallcloud.codify.modes.ModeProvider

class CaretListener(
    private val project: Project,
) : CaretListener {

    override fun caretPositionChanged(event: CaretEvent) {
        val provider = ModeProvider.getOrCreateModeProvider(event.editor)
        provider.onCaretChange(event)
    }
}
