package com.smallcloud.codify.modes.diff.dialog

import com.intellij.ui.table.JBTable
import com.intellij.util.IconUtil.colorize
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.smallcloud.codify.Resources.Icons.BOOKMARK_CHECKED_16x16
import com.smallcloud.codify.Resources.Icons.COIN_16x16
import com.smallcloud.codify.Resources.Icons.LIKE_CHECKED_16x16
import com.smallcloud.codify.Resources.Icons.LIKE_UNCHECKED_16x16
import com.smallcloud.codify.struct.LongthinkFunctionEntry
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.table.TableCellRenderer

internal class IconRenderer(
    val originalIcon: Icon,
    private val needToColorize: Boolean = true
) : JLabel(), TableCellRenderer {
    private var selectedCheckedIcon: Icon? = null
    private var unselectedCheckedIcon: Icon? = null

    init {
        isOpaque = true
        icon = originalIcon
        preferredSize = Dimension(icon.iconWidth + 4, 0)
        horizontalAlignment = CENTER
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (value == null) return this
        if (selectedCheckedIcon == null || unselectedCheckedIcon == null) {
            if (needToColorize) {
                selectedCheckedIcon = colorize(icon, table.selectionForeground)
                unselectedCheckedIcon = colorize(icon, table.foreground)
            } else {
                selectedCheckedIcon = icon
                unselectedCheckedIcon = icon
            }
        }

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
            foreground = table.selectionForeground
            background = table.selectionBackground
            icon = if (need) {
                selectedCheckedIcon
            } else {
                null
            }
        } else {
            foreground = table.foreground
            background = table.background
            icon = if (need) {
                unselectedCheckedIcon
            } else {
                null
            }
        }
        return this
    }
}

internal class LikeRenderer : JLabel(), TableCellRenderer {
    private var selectedCheckedIcon: Icon? = null
    private var unselectedCheckedIcon: Icon? = null
    private var selectedUncheckedIcon: Icon? = null
    private var unselectedUncheckedIcon: Icon? = null

    init {
        isOpaque = true
        icon = LIKE_CHECKED_16x16
        text = "999+"
        preferredSize = Dimension(icon.iconWidth + 4, 0)
        horizontalAlignment = CENTER
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (value == null) return this
        if (selectedCheckedIcon == null || unselectedCheckedIcon == null ||
            selectedUncheckedIcon == null || unselectedUncheckedIcon == null
        ) {
            selectedCheckedIcon = colorize(LIKE_CHECKED_16x16, table.selectionForeground)
            unselectedCheckedIcon = colorize(LIKE_CHECKED_16x16, table.foreground)
            selectedUncheckedIcon = colorize(LIKE_UNCHECKED_16x16, table.selectionForeground)
            unselectedUncheckedIcon = colorize(LIKE_UNCHECKED_16x16, table.foreground)
        }


        val realValue = value as Pair<*, *>
        val likes = realValue.first.toString()
        val isLiked = realValue.second.toString().toBoolean()

        if (isSelected) {
            foreground = table.selectionForeground
            background = table.selectionBackground
            icon = if (isLiked) {
                selectedCheckedIcon
            } else {
                selectedUncheckedIcon
            }
        } else {
            foreground = table.foreground
            background = table.background
            icon = if (isLiked) {
                unselectedCheckedIcon
            } else {
                unselectedUncheckedIcon
            }
        }
        text = if (likes.toInt() > 999) "999+" else likes
        return this
    }
}

internal class LabelRenderer : JLabel(), TableCellRenderer {
    init {
        isOpaque = true
    }
    override fun getTableCellRendererComponent(
        table: JTable?, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component? {
        if (table == null) {
            return this
        }
        if (isSelected) {
            foreground = table.selectionForeground
            background = table.selectionBackground
        } else {
            foreground = table.foreground
            background = table.background
        }
        val entry = (table.model as LongthinkTableModel).elementAt(row)
        font = if (entry.catchAllHighlight || entry.catchAllSelection) {
            JBFont.create(Font(font.family, Font.BOLD, font.size));
        } else {
            table.font
        }

        text = value.toString()
        return this
    }
}

class LongthinkTable(
    data: List<LongthinkFunctionEntry>,
    fromHL: Boolean
) : JBTable(LongthinkTableModel(data, fromHL)) {
    init {
        showVerticalLines = false
        tableHeader = null
        setShowGrid(false)
        columnSelectionAllowed = false
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        border = JBUI.Borders.empty()
        visibleRowCount = 10

        val labelColumn = columnModel.getColumn(0).also {
            it.cellRenderer = LabelRenderer()
            it.minWidth = 200
        }

        val likeColumn = columnModel.getColumn(3).also {
            val renderer = LikeRenderer()
            it.cellRenderer = renderer
            val maxLikeLength = renderer.getFontMetrics(renderer.font).stringWidth("999+")
            it.preferredWidth = renderer.icon.iconWidth + 4 + maxLikeLength
            it.minWidth = renderer.icon.iconWidth + 4 + maxLikeLength
            it.maxWidth = renderer.icon.iconWidth + 4 + maxLikeLength
        }

        val bookmarkColumn = columnModel.getColumn(2).also {
            val renderer = IconRenderer(BOOKMARK_CHECKED_16x16)
            it.cellRenderer = renderer
            it.preferredWidth = renderer.originalIcon.iconWidth + 4
            it.minWidth = renderer.originalIcon.iconWidth + 4
            it.maxWidth = renderer.originalIcon.iconWidth + 4
        }

        val meteringColumn = columnModel.getColumn(1).also {
            val renderer = IconRenderer(COIN_16x16)
            it.cellRenderer = renderer
            it.preferredWidth = renderer.originalIcon.iconWidth + 4
            it.minWidth = renderer.originalIcon.iconWidth + 4
            it.maxWidth = renderer.originalIcon.iconWidth + 4
        }

        minimumSize = Dimension(
            labelColumn.minWidth + bookmarkColumn.minWidth
                    + likeColumn.minWidth + meteringColumn.minWidth, 0
        )
    }

    var selectedValue: LongthinkFunctionEntry
        get() {
            return (model as LongthinkTableModel).elementAt(selectedRow)
        }
        set(newVal) {
            val row = (model as LongthinkTableModel).indexOf(newVal)
            selectionModel.setSelectionInterval(row, row)
        }

    val selectedIndex: Int
        get() {
            return selectedRow
        }

    fun filter(str: String?) {
        (model as LongthinkTableModel).filter(str)
    }
}