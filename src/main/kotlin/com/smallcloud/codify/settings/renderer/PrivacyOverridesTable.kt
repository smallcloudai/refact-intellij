package com.smallcloud.codify.settings.renderer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.smallcloud.codify.CodifyBundle
import com.smallcloud.codify.Resources
import com.smallcloud.codify.privacy.Privacy
import com.smallcloud.codify.privacy.PrivacyChangesNotifier
import icons.CollaborationToolsIcons
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import java.nio.file.Path
import java.util.*
import javax.swing.*
import javax.swing.table.TableCellRenderer
import kotlin.io.path.name
import com.smallcloud.codify.privacy.PrivacyService.Companion.instance as PrivacyServiceInstance
import com.smallcloud.codify.settings.PrivacyState.Companion.instance as PrivacyStateInstance

val privacyToString = mapOf(
    Privacy.DISABLED to "${CodifyBundle.message("privacy.level0Name")}: " +
            CodifyBundle.message("privacy.level0ShortDescription"),
    Privacy.ENABLED to CodifyBundle.message("privacy.level1Name"),
    Privacy.THIRDPARTY to CodifyBundle.message("privacy.level2Name")
)


internal class ButtonRenderer : LinkLabel<String>(), TableCellRenderer {
    init {
        isOpaque = true
        icon = CollaborationToolsIcons.Delete
        preferredSize = Dimension(CollaborationToolsIcons.Delete.iconWidth + 4, 0)
        horizontalAlignment = CENTER
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (isSelected) {
            setForeground(table.getSelectionForeground())
            setBackground(table.getSelectionBackground())
        } else {
            setForeground(table.getForeground())
            setBackground(UIManager.getColor("Button.background"))
        }
        setText(value?.toString() ?: "")
        return this
    }
}

internal class ComboBoxRenderer(minWidth: Int) : ComboBox<String>(DefaultComboBoxModel(Vector(privacyToString.values))),
    TableCellRenderer {
    init {
        isOpaque = true
        maximumSize = Dimension(minWidth, 0)
        minimumSize = Dimension(minWidth, 0)
        preferredSize = Dimension(minWidth, 0)
    }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?,
        isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ): Component {
        if (isSelected) {
            setForeground(table.getSelectionForeground())
            setBackground(table.getSelectionBackground())
        } else {
            setForeground(table.getForeground())
            setBackground(UIManager.getColor("Button.background"))
        }
        selectedItem = privacyToString[value as Privacy]
        return this
    }
}

private class ButtonEditor(checkBox: JBCheckBox?) : DefaultCellEditor(checkBox) {
    private var button = LinkLabel<String>()
    private var isPushed = false
    private var currentRow: Int = -1

    init {
        button.isOpaque = true
        button.icon = CollaborationToolsIcons.Delete

        button.addMouseListener(object : MouseListener {
            override fun mousePressed(e: MouseEvent?) {
                val rec = PrivacyStateInstance.privacyRecords[currentRow]
                val member = PrivacyServiceInstance.getMember(rec.filePath!!)
                val parent = member?.getParentWithPrivacy()
                val outPrivacy = if (parent?.privacy != null) parent.privacy else PrivacyStateInstance.defaultPrivacy
                val filePath = Path.of(rec.filePath)
                val message = CodifyBundle.message("rootSettings.overridesModel.message",
                    filePath.name, privacyToString[rec.privacy]!!, privacyToString[outPrivacy]!!)

                if (Messages.showOkCancelDialog(message,
                        Resources.codifyStr,
                        CodifyBundle.message("rootSettings.overridesModel.delete"),
                        CodifyBundle.message("cancel"),
                        Messages.getQuestionIcon()) == Messages.OK) {
                    PrivacyStateInstance.privacyRecords.removeAt(currentRow)
                    PrivacyStateInstance.restorePrivacyService()
                    fireEditingStopped()
                    ApplicationManager.getApplication()
                        .messageBus
                        .syncPublisher(PrivacyChangesNotifier.TOPIC)
                        .privacyChanged()
                }
            }

            override fun mouseClicked(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}

        })
    }

    override fun getTableCellEditorComponent(
        table: JTable, value: Any,
        isSelected: Boolean, row: Int, column: Int
    ): Component {
        currentRow = row
        if (isSelected) {
            button.foreground = table.selectionForeground
            button.background = table.selectionBackground
        } else {
            button.foreground = table.foreground
            button.background = table.background
        }
        isPushed = true
        return button
    }

    override fun getCellEditorValue(): Any {
        isPushed = false
        return true
    }

    override fun stopCellEditing(): Boolean {
        isPushed = false
        return super.stopCellEditing()
    }

    override fun fireEditingStopped() {
        super.fireEditingStopped()
    }
}

class PrivacyOverridesTable : JBTable(PrivacyOverridesTableModel()) {
    init {
        showVerticalLines = false
        tableHeader.reorderingAllowed = false
        tableHeader.resizingAllowed = false
        tableHeader.font = font
        tableHeader.columnModel.getColumn(0).headerValue =
            CodifyBundle.message("rootSettings.overridesModel.path")
        tableHeader.columnModel.getColumn(1).headerValue =
            CodifyBundle.message("rootSettings.overridesModel.codifyAccess")
        tableHeader.columnModel.getColumn(2).headerValue = null
        selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        border = JBUI.Borders.empty()
        visibleRowCount = 10

        val firstColumn = columnModel.getColumn(1)
        val comboBox = ComboBox(DefaultComboBoxModel(Vector(privacyToString.values)))
        firstColumn.cellEditor = DefaultCellEditor(comboBox)
        firstColumn.cellRenderer = ComboBoxRenderer(comboBox.minimumSize.width)
        firstColumn.preferredWidth = comboBox.minimumSize.width
        firstColumn.minWidth = comboBox.minimumSize.width
        firstColumn.maxWidth = comboBox.minimumSize.width

        val secondColumn = columnModel.getColumn(2)
        secondColumn.cellRenderer = ButtonRenderer()
        secondColumn.cellEditor = ButtonEditor(JBCheckBox())
        secondColumn.preferredWidth = CollaborationToolsIcons.Delete.iconWidth + 4
        secondColumn.minWidth = CollaborationToolsIcons.Delete.iconWidth + 4
        secondColumn.maxWidth = CollaborationToolsIcons.Delete.iconWidth + 4
    }
}