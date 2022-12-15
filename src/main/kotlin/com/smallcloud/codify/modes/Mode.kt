package com.smallcloud.codify.modes


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent

abstract class Mode {
    abstract fun beforeDocumentChangeNonBulk(event: DocumentEvent, editor: Editor)
    abstract fun onTextChange(event: DocumentEvent, editor: Editor)
    abstract fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    abstract fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    abstract fun isInActiveState(): Boolean
}
