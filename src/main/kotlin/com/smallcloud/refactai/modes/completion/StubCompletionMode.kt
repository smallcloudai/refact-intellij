package com.smallcloud.refactai.modes.completion

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.smallcloud.refactai.modes.Mode
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra


class StubCompletionMode(
    override var needToRender: Boolean = true
) : Mode {
    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {}

    override fun onTextChange(event: DocumentEventExtra) {}

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {}

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {}

    override fun onCaretChange(event: CaretEvent) {}

    override fun isInActiveState(): Boolean {
        return false
    }

    override fun show() {}

    override fun hide() {}

    override fun cleanup(editor: Editor) {}

}
