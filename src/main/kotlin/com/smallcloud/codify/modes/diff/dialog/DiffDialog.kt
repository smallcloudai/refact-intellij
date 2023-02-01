package com.smallcloud.codify.modes.diff.dialog

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.smallcloud.codify.CodifyBundle
import com.smallcloud.codify.Resources
import com.smallcloud.codify.modes.diff.DiffIntendEntry
import com.smallcloud.codify.modes.diff.DiffIntentProvider
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.*
import javax.swing.*


class DiffDialog(private val editor: Editor, private val fromHL: Boolean = false) :
    DialogWrapper(editor.project, true) {
    private val msgTextField: JBTextField
    private val warningPrefixText = CodifyBundle.message("diffDialog.selectCodeFirstTo")
    private val warning: JBLabel = JBLabel(warningPrefixText)
    private val descriptionDiffStr: String = CodifyBundle.message("diffDialog.descriptionDiffStr")
    private val descriptionHLStr: String = CodifyBundle.message("diffDialog.descriptionHLStr")
    private val descriptionLabel: JBLabel = JBLabel()
    private val thirdPartyList: JBList<DiffIntendEntry>
    private val thirdPartyScrollPane: JBScrollPane
    private var _entry: DiffIntendEntry = DiffIntendEntry("")
    private var thirdPartyFunctions: List<DiffIntendEntry>
    private lateinit var panel: JPanel
    private var previousIntent: String = ""

    private fun canUseEntry(entry: DiffIntendEntry): Boolean {
        return if (fromHL) {
            entry.supportHighlight
        } else {
            entry.supportSelection
        }
    }

    init {
        isResizable = false
        title = Resources.codifyStr
        descriptionLabel.text = if (fromHL) descriptionHLStr else descriptionDiffStr
        warning.foreground = JBUI.CurrentTheme.Table.BACKGROUND

        val historyIntents = DiffIntentProvider.instance.historyIntents
        thirdPartyFunctions = DiffIntentProvider.instance.defaultThirdPartyFunctions
        var lastSelectedIndex = 0

        thirdPartyList = object : JBList<DiffIntendEntry>(thirdPartyFunctions) {
            init {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                border = JBUI.Borders.empty()
                visibleRowCount = 6
                cellRenderer =
                    object : DefaultListCellRenderer() {
                        override fun getListCellRendererComponent(
                            list: JList<*>,
                            value: Any,
                            index: Int,
                            isSelected: Boolean,
                            cellHasFocus: Boolean
                        ): Component {
                            val c: Component
                            if (thirdPartyFunctions[index].metering > 0) {
                                foreground = editor.colorsScheme.defaultForeground
                                var suffix = " \uD83E\uDDE0"
                                if (thirdPartyFunctions[index].thirdParty) {
                                    suffix += " (coming up)"
                                }
                                c = super.getListCellRendererComponent(
                                    list, (value as DiffIntendEntry).intend + suffix,
                                    index, isSelected, cellHasFocus
                                )
                            } else {
                                foreground = JBColor.GRAY
                                c = super.getListCellRendererComponent(
                                    list, (value as DiffIntendEntry).intend,
                                    index, isSelected, cellHasFocus
                                )
                            }
                            return c
                        }

                    }
                selectionModel = object : DefaultListSelectionModel() {
                    override fun setSelectionInterval(index0: Int, index1: Int) {
                        if (true/*canUseEntry(thirdPartyFunctions[index0])*/)
                            super.setSelectionInterval(index0, index1)
                    }
                }
//                actionMap.put("selectNextRow", object : AbstractAction() {
//                    override fun actionPerformed(e: ActionEvent?) {
//                        val index: Int = selectedIndex
//                        for (i in index + 1 until thirdPartyFunctions.size) {
//                            if (canUseEntry(thirdPartyFunctions[i])) {
//                                selectedIndex = i
//                                break
//                            }
//                        }
//                    }
//                })
//                actionMap.put("selectPreviousRow", object : AbstractAction() {
//                    override fun actionPerformed(e: ActionEvent?) {
//                        val index: Int = selectedIndex
//                        for (i in index - 1 downTo 0) {
//                            if (canUseEntry(thirdPartyFunctions[i])) {
//                                selectedIndex = i
//                                break
//                            }
//                        }
//                    }
//                })
            }
        }
        msgTextField = object : JBTextField() {
            private var hint: String = "↓ commands; ↑ history"
            var historyIndex = -1

            init {
                addKeyListener(object : KeyListener {
                    override fun keyTyped(e: KeyEvent?) {}
                    override fun keyReleased(e: KeyEvent?) {}
                    override fun keyPressed(e: KeyEvent?) {
                        entry = DiffIntendEntry(text)
                        if (e?.keyCode == KeyEvent.VK_UP || e?.keyCode == KeyEvent.VK_DOWN) {
                            if (e.keyCode == KeyEvent.VK_UP) {
                                historyIndex++
                            } else if (e.keyCode == KeyEvent.VK_DOWN) {
                                historyIndex--
                            }
                            historyIndex = minOf(maxOf(historyIndex, -2), historyIntents.size - 1)
                            if (historyIndex > -1) {
                                entry = historyIntents[historyIndex]
                            } else if (historyIndex == -1) {
                                text = previousIntent
                                entry = DiffIntendEntry("")
                            } else if (historyIndex == -2) {
                                previousIntent = text
                                thirdPartyList.requestFocus()
                                thirdPartyList.selectedIndex = lastSelectedIndex
                            }
                        }
                    }
                })
                addFocusListener(object : FocusListener {
                    override fun focusGained(e: FocusEvent?) {
                        entry = DiffIntendEntry(text)
                        thirdPartyList.clearSelection()
                    }

                    override fun focusLost(e: FocusEvent?) {}

                })
            }

            override fun paintComponent(pG: Graphics) {
                val g = pG.create() as Graphics2D
                val oldForeground = foreground
                val oldText = text
                if (!canUseEntry(entry) || entry.thirdParty) {
                    foreground = JBUI.CurrentTheme.Label.disabledForeground()
                }
                if (entry.metering > 0) {
                    text = "$text \uD83E\uDDE0"
                }
                if (entry.thirdParty) {
                    text = "$text (coming up)"
                }
                super.paintComponent(pG)
                foreground = oldForeground
                text = oldText
                if (hint.isEmpty() || text.isNotEmpty()) {
                    return
                }

                g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON
                )
                g.color = JBUI.CurrentTheme.Label.disabledForeground()
                g.font = editor.component.font
                g.drawString(
                    hint,
                    margin.left + insets.left,
                    pG.fontMetrics.maxAscent + margin.top + insets.top
                )
            }

        }
        thirdPartyList.also {
            it.addMouseListener(object : MouseListener {
                override fun mouseClicked(event: MouseEvent?) {
                    if (event == null) return
                    if (event.button == MouseEvent.BUTTON1) {
                        entry = it.selectedValue
                        lastSelectedIndex = it.selectedIndex
                    }
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        doOKAction()
                    }
                }

                override fun mousePressed(e: MouseEvent?) {}
                override fun mouseReleased(e: MouseEvent?) {}
                override fun mouseEntered(e: MouseEvent?) {}
                override fun mouseExited(e: MouseEvent?) {}
            })
            it.addListSelectionListener { e ->
                if (e == null) return@addListSelectionListener
                try {
                    entry = it.selectedValue
//                    msgTextField.text = entry.intend
                } catch (e: Exception) {
                    Logger.getInstance(DiffDialog::class.java).warn(e.message)
                }
            }
            it.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {}
                override fun keyPressed(e: KeyEvent?) {
                    if (e?.keyCode == KeyEvent.VK_ENTER && canUseEntry(it.selectedValue)) {
                        entry = it.selectedValue
                    }
                }

                override fun keyReleased(e: KeyEvent?) {
                    if (e == null) return
                    if (e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN) {
                        if (e.keyCode == KeyEvent.VK_UP && lastSelectedIndex == thirdPartyList.selectedIndex) {
                            msgTextField.requestFocus()
                            msgTextField.text = previousIntent
                            msgTextField.historyIndex = -1
                            entry = DiffIntendEntry(msgTextField.text)
                            thirdPartyList.clearSelection()
                        } else lastSelectedIndex = thirdPartyList.selectedIndex
                    }
                }
            })
        }

        thirdPartyScrollPane = JBScrollPane(
            thirdPartyList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        )
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return msgTextField
    }

    var entry: DiffIntendEntry
        get() = _entry
        set(newVal) {
            if (newVal == _entry) return
            _entry = newVal
            val canUse = canUseEntry(_entry) && !_entry.thirdParty
            okAction.isEnabled = canUse
            msgTextField.text = entry.intend
            if (!canUse) {
                warning.foreground = JBColor.RED
                warning.text = "$warningPrefixText ${_entry.intend}"
            } else {
                warning.foreground = JBUI.CurrentTheme.Table.BACKGROUND
            }
        }

    override fun createCenterPanel(): JComponent {
        panel = FormBuilder.createFormBuilder().run {
            addComponent(descriptionLabel, 1)
            addComponentFillVertically(msgTextField, 1)
            addComponentToRightColumn(warning, 0)
            addComponentFillVertically(JPanel(), 6)
            addComponent(JBLabel("${CodifyBundle.message("diffDialog.usefulCommands")}:"), 6)
            addComponent(thirdPartyScrollPane, 6)
            addComponentFillVertically(JPanel(), 6)
            this
        }.panel
        return panel
    }

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }
}