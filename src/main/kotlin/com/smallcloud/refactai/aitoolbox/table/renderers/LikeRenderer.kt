package com.smallcloud.refactai.aitoolbox.table.renderers

import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.aitoolbox.table.LongthinkTableModel
import com.smallcloud.refactai.aitoolbox.utils.getReasonForEntryFromState
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.UIManager
import javax.swing.table.TableCellRenderer

internal class LikeRenderer : JLabel(), TableCellRenderer {
    private data class CacheEntry(val isLiked: Boolean,
                                  val isSelected: Boolean,
                                  val isEnabled: Boolean,
                                  val foreground: Color)

    private var cachedIcons: MutableMap<CacheEntry, Icon> = mutableMapOf()

    init {
        isOpaque = true
        icon = Resources.Icons.LIKE_CHECKED_16x16
        text = "999+"
        preferredSize = Dimension(icon.iconWidth + 4, 0)
        horizontalAlignment = LEFT
        addPropertyChangeListener {
            if (it.propertyName == "UI") {
                cachedIcons.clear()
            }
        }
    }

    override fun getTableCellRendererComponent(
            table: JTable, value: Any?,
            isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (value == null) return this
        val model = table.model as LongthinkTableModel
        val longthink = model.elementAt(row)
        val reason = getReasonForEntryFromState(longthink.getFunctionByFilter())
        isEnabled = reason == null
        toolTipText = reason

        val realValue = value as Pair<*, *>
        val likes = realValue.first.toString()
        val isLiked = realValue.second.toString().toBoolean()

        if (isSelected) {
            foreground = table.selectionForeground
            background = table.selectionBackground
        } else {
            foreground = table.foreground
            background = table.background
        }

        val disabledForeground = UIManager.getColor("Label.disabledForeground")

        icon = cachedIcons.getOrPut(CacheEntry(isLiked, isSelected, isEnabled,
                if (isEnabled) foreground else disabledForeground)) {
            colorize(if (isLiked) Resources.Icons.LIKE_CHECKED_16x16 else Resources.Icons.LIKE_UNCHECKED_16x16,
                    if (isEnabled) foreground else disabledForeground)
        }

        text = if (likes.toInt() > 999) "999+" else likes
        font = table.font

        return this
    }
}