package com.smallcloud.codify.modes.diff.dialog.comboBox

import com.intellij.openapi.observable.util.whenTextChanged
import com.intellij.openapi.ui.ComboBox
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.JTextComponent

val SEPARATOR = "SEPARATOR"

class ComboBoxWOBtnArray : ComboBox<String> {
    constructor(list: List<String>) : super(DefaultComboBoxModelReplace(list))

    private var previousSelected: Int = 0


    init {
        setEditable(true)
        renderer = ComboBoxRenderer()
        this.remove(getComponent(0))

        (model as DefaultComboBoxModelReplace<String>).insertElementAt("", 0)
        selectedIndex = 0

        val editor = getEditor().getEditorComponent() as JTextComponent
        editor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                if ((previousSelected == 0 && selectedIndex == 0) || (previousSelected == -1 && selectedIndex == -1)) {
                    (model as DefaultComboBoxModelReplace<String>).replaceElementAt(editor.text, 0)
                    selectedIndex = 0
                    popup?.hide()
                }
                previousSelected = selectedIndex
            }

            override fun removeUpdate(e: DocumentEvent?) {}
            override fun changedUpdate(e: DocumentEvent?) {}
        })
    }

    fun whenTextChanged(listener: (DocumentEvent) -> Unit) {
        val editor = getEditor().getEditorComponent() as JTextComponent
        editor.whenTextChanged(null, listener)
    }
}
