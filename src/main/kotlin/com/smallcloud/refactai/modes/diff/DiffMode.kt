package com.smallcloud.refactai.modes.diff

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.CaretEvent
import com.smallcloud.refactai.modes.Mode
import com.smallcloud.refactai.modes.ModeProvider.Companion.getOrCreateModeProvider
import com.smallcloud.refactai.modes.ModeType
import com.smallcloud.refactai.modes.completion.structs.DocumentEventExtra
import dev.gitlive.difflib.DiffUtils

class DiffMode(
    override var needToRender: Boolean = true
) : Mode {
    private val app = ApplicationManager.getApplication()
    private var diffLayout: DiffLayout? = null


    private fun cancel(editor: Editor?) {
        app.invokeLater {
            diffLayout?.cancelPreview()
            diffLayout = null
        }
        if (editor != null && !Thread.currentThread().stackTrace.any { it.methodName == "switchMode" }) {
            getOrCreateModeProvider(editor).switchMode()
        }
    }

    override fun beforeDocumentChangeNonBulk(event: DocumentEventExtra) {
        cancel(event.editor)
    }

    override fun onTextChange(event: DocumentEventExtra) {
    }

    override fun onTabPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        diffLayout?.applyPreview()
        diffLayout = null
    }

    override fun onEscPressed(editor: Editor, caret: Caret?, dataContext: DataContext) {
        cancel(editor)
    }

    override fun onCaretChange(event: CaretEvent) {}

    fun isInRenderState(): Boolean {
        return (diffLayout != null && !diffLayout!!.rendered)
    }

    override fun isInActiveState(): Boolean {
        return isInRenderState() || diffLayout != null
    }

    override fun show() {
        TODO("Not yet implemented")
    }

    override fun hide() {
        TODO("Not yet implemented")
    }

    override fun cleanup(editor: Editor) {
        cancel(editor)
    }

    fun actionPerformed(
        editor: Editor,
        content: String
    ) {
        val selectionModel = editor.selectionModel
        val startSelectionOffset: Int = selectionModel.selectionStart
        val endSelectionOffset: Int = selectionModel.selectionEnd

        val indent = selectionModel.selectedText?.takeWhile { it ==' ' || it == '\t' }
        val indentedCode = content.prependIndent(indent?: "")

        selectionModel.removeSelection()
        // doesn't seem to take focus
        // editor.contentComponent.requestFocus()
        getOrCreateModeProvider(editor).switchMode(ModeType.Diff)
        diffLayout?.cancelPreview()
        val diff = DiffLayout(editor, content)
        val originalText = editor.document.text
        val newText = originalText.replaceRange(startSelectionOffset, endSelectionOffset, indentedCode)
        val patch = DiffUtils.diff(originalText.split("\n"), newText.split("\n"))

        diffLayout = diff.update(patch)

        app.invokeLater {
            editor.contentComponent.requestFocusInWindow()
        }
    }
}