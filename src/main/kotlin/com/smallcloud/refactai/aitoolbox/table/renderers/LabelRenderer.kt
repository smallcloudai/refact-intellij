package com.smallcloud.refactai.aitoolbox.table.renderers

import com.intellij.util.ui.JBUI
import com.smallcloud.refactai.aitoolbox.table.LongthinkTableModel
import com.smallcloud.refactai.aitoolbox.utils.getReasonForEntryFromState
import java.awt.Component
import java.awt.Font
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableCellRenderer

internal class LabelRenderer : JLabel(), TableCellRenderer {
    private var boldFont: Font? = null
    init {
        isOpaque = true
        border = JBUI.Borders.empty(4, 10, 4, 0)
    }
    override fun getTableCellRendererComponent(
            table: JTable?, value: Any?,
            isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (table == null) {
            return this
        }
        if (boldFont == null) {
            boldFont = table.font //JBFont.create(Font(font.family, Font.BOLD, table.font.size))
        }
        if (isSelected) {
            foreground = table.selectionForeground
            background = table.selectionBackground
        } else {
            foreground = table.foreground
            background = table.background
        }
        val model = table.model as LongthinkTableModel
        val entry = if (row < model.rowCount) model.elementAt(row) else return this
        font = if (entry.catchAllHighlight || entry.catchAllSelection) boldFont else table.font
        text = value.toString()

        val longthink = model.elementAt(row)
        val reason = getReasonForEntryFromState(longthink.functions.first())
        isEnabled = reason == null
        toolTipText = reason
        return this
    }
}