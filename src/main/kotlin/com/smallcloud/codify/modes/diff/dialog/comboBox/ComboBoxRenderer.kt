package com.smallcloud.codify.modes.diff.dialog.comboBox

import java.awt.Component
import java.awt.FlowLayout
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JSeparator
import javax.swing.ListCellRenderer
import javax.swing.border.EmptyBorder

class ComboBoxRenderer : JLabel(), ListCellRenderer<String> {
    var separator: JSeparator

    init {
        setOpaque(true)
        separator = JSeparator(JSeparator.HORIZONTAL)
        layout = FlowLayout()
    }


    override fun getListCellRendererComponent(
        list: JList<out String>?,
        value: String?,
        index: Int,
        isSelected: Boolean,
        cellHasFocus: Boolean
    ): Component {
        val str = value?.toString() ?: ""
        if (SEPARATOR.equals(str)) {
            return separator
        }
        if (list != null) {
            if (isSelected) {
                setBackground(list.getSelectionBackground())
                setForeground(list.getSelectionForeground())
            } else {
                setBackground(list.getBackground())
                setForeground(list.getForeground())
            }
            setFont(list.getFont())
            setText(str)
        }
        return this
    }
}