package com.smallcloud.codify.modes


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.DocumentEvent

interface Mode {
    fun beforeDocumentChangeNonBulk(event: DocumentEvent?, editor: Editor)
    fun onTextChange(event: DocumentEvent?, editor: Editor, force: Boolean)
    fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    fun onCaretChange(event: CaretEvent)
    fun isInActiveState(): Boolean
    fun cleanup()
}
