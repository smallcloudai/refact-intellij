package com.smallcloud.codify.modes.diff.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.smallcloud.codify.modes.diff.DiffIntendProvider
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener


class DiffDialog(project: Project?, fromHL: Boolean = false) : DialogWrapper(project, true) {
    private val msgTextField: JBTextField
    private val descriptionDiffStr: String = "What would you like to do with the selected code?"
    private val descriptionHLStr: String = "What would you like to do? (this action highlights code first)"
    private val descriptionLabel: JBLabel = JBLabel()
    private val historyList: JBList<String>
    private val historyScrollPane: JBScrollPane
    private var _messageText: String? = null
    private var intends: List<String>
    private lateinit var panel: JPanel
    private var previousIntend: String? = null

    init {
        title = "Codify"
        descriptionLabel.text = if (fromHL) descriptionHLStr else descriptionDiffStr

        val defaultIntends = DiffIntendProvider.instance.defaultIntends
        var historyIntends = DiffIntendProvider.instance.historyIntends
        intends = defaultIntends
        if (historyIntends.isNotEmpty()) {
            intends = emptyList()
            historyIntends = historyIntends.filter { !it.isEmpty() }

            var i = 0
            while (i < historyIntends.size) {
                if (historyIntends[i] !in intends) {
                    intends = intends + listOf(historyIntends[i])
                }
                i++
            }

            defaultIntends.forEach {
                if (it !in intends) {
                    intends = intends + listOf(it)
                }
            }
        }
        msgTextField = JBTextField()
        historyList = JBList<String>(intends)
        historyList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        historyList.border = JBUI.Borders.empty()
        historyList.visibleRowCount = 8
        historyList.addMouseListener(object : MouseListener {
            override fun mouseClicked(event: MouseEvent?) {
                if (event != null) {
                    msgTextField.text = historyList.selectedValue
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        _messageText = historyList.selectedValue
                        doOKAction()
                    }
                }
            }

            override fun mousePressed(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
        })
        historyList.addListSelectionListener(object : ListSelectionListener {
            override fun valueChanged(e: ListSelectionEvent?) {
                if (e != null) {
                    msgTextField.text = historyList.selectedValue
                }
            }
        })
        historyList.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {
                if (e != null) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        _messageText = historyList.selectedValue
                        doOKAction()
                    }
                }
            }

            override fun keyReleased(e: KeyEvent?) {
                if (e != null) {
                    if (e.keyCode == KeyEvent.VK_UP && historyList.selectedIndex == 0) {
                        msgTextField.requestFocus()
                        historyList.clearSelection()
                        msgTextField.text = previousIntend
                    }
                }
            }
        })
        historyScrollPane = JBScrollPane(
            historyList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        msgTextField.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {
                if (e != null) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        _messageText = msgTextField.text
                        doOKAction()
                    } else if (e.keyCode == KeyEvent.VK_DOWN) {
                        previousIntend = msgTextField.text
                        historyList.requestFocus()
                        historyList.selectedIndex = 0
                    }
                }
            }

            override fun keyReleased(e: KeyEvent?) {}
        })
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent? {
        return msgTextField
    }

    val messageText: String
        get() = if (_messageText == null) msgTextField.text else _messageText!!

    override fun createCenterPanel(): JComponent {
        panel = FormBuilder.createFormBuilder().run {
            addComponent(descriptionLabel, 1)
            addComponentFillVertically(msgTextField, 1)
            addComponent(JBLabel("History:"), 6)
            addComponent(historyScrollPane, 1)
            addComponentFillVertically(JPanel(), 0)
        }.panel
        return panel
    }
}