package com.smallcloud.refactai.aitoolbox.table.renderers

import com.intellij.openapi.observable.util.whenMousePressed
import com.intellij.openapi.ui.putUserData
import com.smallcloud.refactai.aitoolbox.LongthinkKey
import com.smallcloud.refactai.aitoolbox.table.LongthinkTableModel
import com.smallcloud.refactai.aitoolbox.utils.getReasonForEntryFromState
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

open class IconRenderer(
        val originalIcon: Icon,
        var mousePressedFunction: () -> Unit = {},
        var toolTipText: String? = null
) : AbstractCellEditor(), TableCellEditor, TableCellRenderer {
    private data class CacheEntry(val isSelected: Boolean,
                                  val isEnabled: Boolean,
                                  val foreground: Color)

    private var cachedIcons: MutableMap<CacheEntry, Icon> = mutableMapOf()

    override fun getTableCellRendererComponent(
            table: JTable, value: Any?,
            isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        val component = JLabel()
        val model = table.model as LongthinkTableModel
        val entry = if (row < model.rowCount) model.elementAt(row) else return component
        (component as JComponent).putUserData(LongthinkKey, entry)
        component.isOpaque = true
        component.icon = originalIcon
        component.preferredSize = Dimension(component.icon.iconWidth + 4, 0)
        component.horizontalAlignment = SwingConstants.CENTER
        component.addPropertyChangeListener {
            if (it.propertyName == "UI") {
                cachedIcons.clear()
            }
        }

        if (value == null) return component

        val need: Boolean = when (value) {
            is Boolean -> {
                value
            }

            is Int -> {
                value != 0
            }

            else -> {
                false
            }
        }

        if (isSelected) {
            component.foreground = table.selectionForeground
            component.background = table.selectionBackground
        } else {
            component.foreground = table.foreground
            component.background = table.background
        }
        val reason = getReasonForEntryFromState(entry.getFunctionByFilter())
        val isEnabled = reason == null
        val disabledForeground = UIManager.getColor("Label.disabledForeground")

        component.isEnabled = isEnabled
        component.toolTipText = if (toolTipText != null) toolTipText else reason
        component.icon = if (need) cachedIcons.getOrPut(CacheEntry(isSelected, isEnabled,
                if (isEnabled) component.foreground else disabledForeground)) {
            colorize(originalIcon, if (isEnabled) component.foreground else disabledForeground)
        } else null

        return component
    }

    override fun getCellEditorValue(): Any {
        return ""
    }

    override fun getTableCellEditorComponent(table: JTable, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
        return getTableCellRendererComponent(table, value, true, true, row, column).apply {
            whenMousePressed {
                mousePressedFunction()
            }
        }
    }
}