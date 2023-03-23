package com.smallcloud.refact.modes


import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.smallcloud.refact.modes.completion.structs.DocumentEventExtra

interface Mode {
    var needToRender: Boolean
    fun beforeDocumentChangeNonBulk(event: DocumentEventExtra)
    fun onTextChange(event: DocumentEventExtra)
    fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext)
    fun onCaretChange(event: CaretEvent)
    fun isInActiveState(): Boolean
    fun show()
    fun hide()
    fun cleanup()
}
