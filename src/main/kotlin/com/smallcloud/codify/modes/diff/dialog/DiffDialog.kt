package com.smallcloud.codify.modes.diff.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.smallcloud.codify.modes.diff.DiffIntentProvider
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseEvent
import java.awt.event.MouseListener
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants


class DiffDialog(project: Project?, private val fromHL: Boolean = false) : DialogWrapper(project, true) {
    private val msgTextField: JBTextField
    private val descriptionDiffStr: String = "What would you like to do with the selected code?"
    private val descriptionHLStr: String = "What would you like to do? (this action highlights code first)"
    private val descriptionLabel: JBLabel = JBLabel()
    private val historyList: JBList<String>
    private val historyScrollPane: JBScrollPane
    private val thirdPartyList: JBList<String>
    private val thirdPartyScrollPane: JBScrollPane
    private var _messageText: String? = null
    private var _isLongThinkModel: Boolean = false
    private var intents: List<String>
    private var thirdPartyFunctions: List<String>
    private lateinit var panel: JPanel
    private var previousIntent: String? = null

    init {
        title = "Codify"
        descriptionLabel.text = if (fromHL) descriptionHLStr else descriptionDiffStr

        val defaultIntents = DiffIntentProvider.instance.defaultIntents
        var historyIntents = DiffIntentProvider.instance.historyIntents
        intents = defaultIntents
        thirdPartyFunctions = DiffIntentProvider.instance.defaultThirdPartyFunctions
        if (historyIntents.isNotEmpty()) {
            intents = emptyList()
            historyIntents = historyIntents.filter { !it.isEmpty() }

            var i = 0
            while (i < historyIntents.size) {
                if (historyIntents[i] !in intents) {
                    intents = intents + listOf(historyIntents[i])
                }
                i++
            }

            defaultIntents.forEach {
                if (it !in intents) {
                    intents = intents + listOf(it)
                }
            }
        }
        msgTextField = JBTextField()
        historyList = JBList(intents)
        thirdPartyList = JBList(thirdPartyFunctions)

        historyList.also {
            it.selectionMode = ListSelectionModel.SINGLE_SELECTION
            it.border = JBUI.Borders.empty()
            it.visibleRowCount = if (!fromHL) 5 else 8
            it.addMouseListener(object : MouseListener {
                override fun mouseClicked(event: MouseEvent?) {
                    if (event == null) return
                    msgTextField.text = it.selectedValue
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        _messageText = it.selectedValue
                        _isLongThinkModel = false
                        doOKAction()
                    }
                    thirdPartyList.clearSelection()
                }

                override fun mousePressed(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseExited(e: MouseEvent?) {}
            })
            it.addListSelectionListener { e ->
                if (e == null) return@addListSelectionListener
                val selectedIndex = it.selectedIndex
                msgTextField.text = it.selectedValue
                thirdPartyList.clearSelection()
                it.selectedIndex = selectedIndex
            }
            it.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {}
                override fun keyPressed(e: KeyEvent?) {
                    if (e == null) return
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        _messageText = it.selectedValue
                        _isLongThinkModel = false
                        doOKAction()
                    }
                }
                override fun keyReleased(e: KeyEvent?) {
                    if (e == null) return
                    if (e.keyCode == KeyEvent.VK_UP && it.selectedIndex == 0) {
                        msgTextField.requestFocus()
                        it.clearSelection()
                        msgTextField.text = previousIntent
                        thirdPartyList.clearSelection()
                    }
                }
            })
        }
        thirdPartyList.also {
            it.selectionMode = ListSelectionModel.SINGLE_SELECTION
            it.border = JBUI.Borders.empty()
            it.visibleRowCount = 5
            it.addMouseListener(object : MouseListener {
                override fun mouseClicked(event: MouseEvent?) {
                    if (event == null) return
                    msgTextField.text = it.selectedValue
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        _messageText = it.selectedValue
                        _isLongThinkModel = true
                        doOKAction()
                    }
                    historyList.clearSelection()
                }
                override fun mousePressed(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseExited(e: MouseEvent?) {}
            })
            it.addListSelectionListener { e ->
                if (e == null) return@addListSelectionListener
                val selectedIndex = it.selectedIndex
                historyList.clearSelection()
                msgTextField.text = it.selectedValue
                it.selectedIndex = selectedIndex
            }
        }

        historyScrollPane = JBScrollPane(
            historyList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        thirdPartyScrollPane = JBScrollPane(
            thirdPartyList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        msgTextField.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent?) {}
            override fun keyPressed(e: KeyEvent?) {
                if (e == null) return
                if (e.keyCode == KeyEvent.VK_ENTER) {
                    _messageText = msgTextField.text
                    _isLongThinkModel = false
                    doOKAction()
                } else if (e.keyCode == KeyEvent.VK_DOWN) {
                    previousIntent = msgTextField.text
                    historyList.requestFocus()
                    historyList.selectedIndex = 0
                    thirdPartyList.clearSelection()
                }
            }

            override fun keyReleased(e: KeyEvent?) {}
        })
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return msgTextField
    }

    val messageText: String
        get() = if (_messageText == null) msgTextField.text else _messageText!!

    val isLongThinkModel: Boolean
        get() = _isLongThinkModel

    override fun createCenterPanel(): JComponent {
        panel = FormBuilder.createFormBuilder().run {
            addComponent(descriptionLabel, 1)
            addComponentFillVertically(msgTextField, 1)
            addComponent(JBLabel("History:"), 6)
            addComponent(historyScrollPane, 1)
            addComponentFillVertically(JPanel(), 0)
            if (!fromHL) {
                addComponentFillVertically(JPanel(), 0)
                addComponent(JBLabel("Use big model:"), 6)
                addComponent(thirdPartyScrollPane, 6)
                addComponentFillVertically(JPanel(), 0)
            }
            this
        }.panel
        return panel
    }
}