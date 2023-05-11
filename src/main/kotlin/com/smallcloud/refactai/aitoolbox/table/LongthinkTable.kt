package com.smallcloud.refactai.aitoolbox.table

import com.intellij.icons.AllIcons
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.smallcloud.refactai.Resources.Icons.BOOKMARK_CHECKED_16x16
import com.smallcloud.refactai.Resources.Icons.DESCRIPTION_16x16
import com.smallcloud.refactai.aitoolbox.LongthinkAction
import com.smallcloud.refactai.aitoolbox.table.renderers.IconRenderer
import com.smallcloud.refactai.aitoolbox.table.renderers.LabelRenderer
import com.smallcloud.refactai.aitoolbox.table.renderers.LikeRenderer
import com.smallcloud.refactai.aitoolbox.utils.getReasonForEntryFromState
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import java.awt.Point
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import javax.swing.ListSelectionModel

class LongthinkTable(
        data: List<LongthinkFunctionVariation>,
        fromHL: Boolean,
) : JBTable(LongthinkTableModel(data, fromHL)) {
    private var descriptionFunc: () -> Unit = {}
    override fun getComponentPopupMenu(): JPopupMenu {
        if (mousePosition == null || model.rowCount == 0) return JPopupMenu()
        val p: Point = mousePosition
        val row = rowAtPoint(p)
        val model = model as LongthinkTableModel
        val longthink = model.elementAt(row)
        val reason = getReasonForEntryFromState(longthink.getFunctionByFilter())
        selectedValue = longthink

        val action = LongthinkAction()
        val popup = JPopupMenu().also { popup ->
            popup.add(JMenuItem("Run", AllIcons.Debugger.ThreadRunning).also {
                it.isEnabled = reason == null
                it.addActionListener {
                    action.doActionPerformed(longthink.getFunctionByFilter())
                }
            })
            popup.add(JMenuItem("Describe", DESCRIPTION_16x16).also {
                it.addActionListener {
                    descriptionFunc()
                }
            })
        }
        return popup
    }

    fun setupDescriptionColumn(f: () -> Unit, toolTipText: String) {
        descriptionFunc = f
        columnModel.getColumn(3).also {
            val renderer = (it.cellEditor as com.smallcloud.refactai.aitoolbox.table.renderers.IconRenderer)
            renderer.mousePressedFunction = descriptionFunc
            renderer.toolTipText = toolTipText
        }
    }

    init {
        showVerticalLines = false
        tableHeader = null
        setShowGrid(false)
        columnSelectionAllowed = false
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        border = JBUI.Borders.empty()
        visibleRowCount = 10
        font = super.getFont().deriveFont(14f)

        columnModel.getColumn(0).also {
            it.cellRenderer = LabelRenderer()
            it.minWidth = 200
        }

        columnModel.getColumn(2).also {
            val renderer = LikeRenderer()
            it.cellRenderer = renderer
            val maxLikeLength = renderer.getFontMetrics(renderer.font).stringWidth("999+")
            it.preferredWidth = renderer.icon.iconWidth + 4 + maxLikeLength
            it.minWidth = renderer.icon.iconWidth + 4 + maxLikeLength
            it.maxWidth = renderer.icon.iconWidth + 4 + maxLikeLength
        }

        columnModel.getColumn(1).also {
            val renderer = IconRenderer(BOOKMARK_CHECKED_16x16)
            it.cellRenderer = renderer
            it.preferredWidth = renderer.originalIcon.iconWidth + 4
            it.minWidth = renderer.originalIcon.iconWidth + 4
            it.maxWidth = renderer.originalIcon.iconWidth + 4
        }

        columnModel.getColumn(3).also {
            val renderer = IconRenderer(DESCRIPTION_16x16)
            it.cellRenderer = renderer
            it.cellEditor = renderer
            it.preferredWidth = renderer.originalIcon.iconWidth + 4
            it.minWidth = renderer.originalIcon.iconWidth + 4
            it.maxWidth = renderer.originalIcon.iconWidth + 4
        }
    }

    var selectedValue: LongthinkFunctionVariation
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

    fun filter(str: String, filterBy: ((LongthinkFunctionVariation) -> Boolean)? = null) {
        (model as LongthinkTableModel).filter(str, filterBy)
    }
}