package com.smallcloud.codify.modes

import com.intellij.openapi.editor.event.CaretEvent


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.event.DocumentEvent

abstract class Mode {
    abstract fun focusGained()
    abstract fun focusLost()
    abstract fun beforeDocumentChangeNonBulk(event: DocumentEvent)
    abstract fun onTextChange(event: DocumentEvent)
    abstract fun onCaretChange(event: CaretEvent)
    abstract fun onTabPressed(dataContext: DataContext)
    abstract fun onEscPressed(dataContext: DataContext)
}
