package com.smallcloud.refactai.modes.diff.dialog

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.IconUtil.colorize
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.modes.diff.DiffIntentProvider
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.struct.LocalLongthinkInfo
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.smallcloud.refactai.privacy.PrivacyService.Companion.instance as PrivacyService
import com.smallcloud.refactai.settings.ExtraState.Companion.instance as ExtraState
import com.smallcloud.refactai.statistic.ExtraInfoService.Companion.instance as ExtraInfoService


private enum class Mode {
    FILTER,
    HISTORY,
}

private const val GLOBAL_MARGIN = 15

class DiffDialog(
    private val editor: Editor,
    private val fromHL: Boolean = false,
    private val startPosition: LogicalPosition = LogicalPosition(0, 0),
    private val finishPosition: LogicalPosition = LogicalPosition(0, 0),
    private val selectedText: String = "",

) :
    DialogWrapper(editor.project, true) {
    private val msgTextField: JBTextField
    private val warningPrefixText = RefactAIBundle.message("aiToolbox.selectCodeFirstTo")
    private val warning: JBLabel = JBLabel(warningPrefixText)
    private val meteringBalanceLabel: JBLabel = JBLabel((AccountManager.meteringBalance / 100).toString()).apply {
        toolTipText = RefactAIBundle.message("aiToolbox.meteringBalance")
        icon = colorize(Resources.Icons.COIN_16x16, foreground)
    }
    private val descriptionLabel: JBLabel = JBLabel()
    private val thirdPartyList: LongthinkTable
    private val thirdPartyScrollPane: JBScrollPane
    private val longthinkLabel: JBLabel = JBLabel().apply {
        font = JBUI.Fonts.create(font.family, 18)
        val b = border
        border = CompoundBorder(b, EmptyBorder(JBUI.insets(0, GLOBAL_MARGIN, 0, 0)))
    }
    private val longthinkDescriptionPane: JEditorPane = JEditorPane().apply {
        editorKit = HTMLEditorKitBuilder().withWordWrapViewFactory().build()
        isFocusable = true
        isEditable = false
        isOpaque = false
        margin = JBUI.insets(GLOBAL_MARGIN)
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) {
                caretPosition = 0
            }

            override fun removeUpdate(e: DocumentEvent?) {
                caretPosition = 0
            }

            override fun changedUpdate(e: DocumentEvent?) {
                caretPosition = 0
            }

        })
    }

    private val longthinkDescriptionScrollPane: JBScrollPane
    private var _entry: LongthinkFunctionEntry = LongthinkFunctionEntry("")
    private var thirdPartyFunctions: List<LongthinkFunctionEntry>
    private lateinit var panel: JPanel
    private var previousIntent: String = ""
    private var historyIndex = -1
    private var runButton: JButton = JButton(
        "Run",
        AllIcons.Debugger.ThreadRunning
    )
    private var likeButton: LinkLabel<String> = LinkLabel(null, Resources.Icons.LIKE_CHECKED_24x24)
    private var bookmarkButton: LinkLabel<String> = LinkLabel(null, Resources.Icons.BOOKMARK_CHECKED_24x24)
    private val activeMode: Mode
        get() {
            return if (historyIndex >= 0) {
                Mode.HISTORY
            } else {
                Mode.FILTER
            }
        }

    private fun getReasonForEntry(entry: LongthinkFunctionEntry): String? {
        val vFile = FileDocumentManager.getInstance().getFile(editor.document)
        if (entry.thirdParty && PrivacyService.getPrivacy(vFile) < Privacy.THIRDPARTY) {
            return RefactAIBundle.message("aiToolbox.reasons.thirdParty")
        }
        if (getFilteredIntent(entry.intent).endsWith("?")) return null
        if (vFile != null && !entry.supportsLanguages.match(vFile.name)) {
            return RefactAIBundle.message("aiToolbox.reasons.supportLang")
        }
        if (fromHL) {
            if (!entry.supportHighlight) {
                return RefactAIBundle.message("aiToolbox.reasons.selectCodeFirst",
                    entry.selectedLinesMin, entry.selectedLinesMax)
            }
            if (msgTextField.text.isEmpty() && (entry.catchAny())) {
                return RefactAIBundle.message("aiToolbox.reasons.writeSomething")
            }
        } else {
            val lines = finishPosition.line - startPosition.line + 1
            if (!entry.supportSelection) {
                return RefactAIBundle.message("aiToolbox.reasons.onlyForHL")
            }
            if (entry.selectedLinesMax < lines) {
                return RefactAIBundle.message("aiToolbox.reasons.linesGreater", entry.selectedLinesMax)
            }
            if (entry.selectedLinesMin > lines) {
                return RefactAIBundle.message("aiToolbox.reasons.linesLess", entry.selectedLinesMin)
            }
            if (msgTextField.text.isEmpty() && (entry.catchAny())) {
                return RefactAIBundle.message("aiToolbox.reasons.writeSomething")
            }
        }

        return null
    }

    private fun getFilteredIntent(intent: String): String {
        var filteredIntent = intent
        while (filteredIntent.isNotEmpty() && filteredIntent.last().isWhitespace()) {
            filteredIntent = filteredIntent.dropLast(1)
        }
        return filteredIntent
    }

    override fun doOKAction() {
        if (getReasonForEntry(entry) == null) {
            entry = entry.copy().apply {
                intent = entry.modelFixedIntent.ifEmpty {
                    if (catchAny()) {
                        msgTextField.text
                    } else {
                        label
                    }
                }
            }

            val filteredIntent = getFilteredIntent(entry.intent)
            if (filteredIntent.endsWith("?") || entry.model == Resources.openChatModel) {
                RefactAIToolboxPaneFactory.gptChatPanes?.send(filteredIntent, selectedText)
                super.doCancelAction()
            } else {
                super.doOKAction()
            }
        }
    }

    private fun getDefaultEntry(): LongthinkFunctionEntry {
        return try {
            (thirdPartyList.model as LongthinkTableModel).elementAt(0).apply { intent = msgTextField.text }
        } catch (e: Exception) {
            LongthinkFunctionEntry(msgTextField.text)
        }
    }

    init {
        isResizable = false
        title = RefactAIBundle.message("aiToolbox.title")
        descriptionLabel.text = RefactAIBundle.message("aiToolbox.descriptionStr")

        val historyIntents = DiffIntentProvider.instance.historyIntents
        thirdPartyFunctions = DiffIntentProvider.instance.defaultThirdPartyFunctions
        var lastSelectedIndex = 0

        thirdPartyList = LongthinkTable(thirdPartyFunctions, fromHL)
        msgTextField = object : JBTextField() {
            private var hint: String = "↓ commands; ↑ history"

            init {
                addKeyListener(object : KeyListener {
                    override fun keyTyped(e: KeyEvent?) {}

                    override fun keyReleased(e: KeyEvent?) {
                        if (e?.keyCode == KeyEvent.VK_ENTER) {
                            doOKAction()
                        } else if (e?.keyCode == KeyEvent.VK_BACK_SPACE ||
                            e?.keyCode == KeyEvent.VK_DELETE
                        ) {
                            thirdPartyList.filter(text)
                            thirdPartyList.selectionModel.setSelectionInterval(0, 0)
                        }
                    }

                    override fun keyPressed(e: KeyEvent?) {
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
                                entry = getDefaultEntry()
                            } else if (historyIndex == -2) {
                                previousIntent = text
                                thirdPartyList.requestFocus()
                                thirdPartyList.selectionModel.setSelectionInterval(lastSelectedIndex, lastSelectedIndex)
                                return
                            }
                        }
                    }
                })
                document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) {
                        if (activeMode == Mode.FILTER) {
                            previousIntent = text
                            thirdPartyList.filter(text)
                            thirdPartyList.selectionModel.setSelectionInterval(0, 0)
                        }
                    }

                    override fun removeUpdate(e: DocumentEvent?) {
                        if (activeMode == Mode.FILTER) {
                            previousIntent = text
                            thirdPartyList.filter(text)
                            thirdPartyList.selectionModel.setSelectionInterval(0, 0)
                            entry = getDefaultEntry()
                        }
                    }

                    override fun changedUpdate(e: DocumentEvent?) {
                        if (activeMode == Mode.FILTER) {
                            previousIntent = text
                            thirdPartyList.filter(text)
                            thirdPartyList.selectionModel.setSelectionInterval(0, 0)
                            entry = getDefaultEntry()
                        }
                    }

                })
            }

            override fun paintComponent(pG: Graphics) {
                val g = pG.create() as Graphics2D
                super.paintComponent(pG)
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
            it.selectionModel.addListSelectionListener { e ->
                if (it.selectedIndex == -1) return@addListSelectionListener
                if (e == null) return@addListSelectionListener
                try {
                    val tempEntry = it.selectedValue.apply { intent = msgTextField.text }
                    entry = tempEntry
                } catch (e: Exception) {
                    Logger.getInstance(DiffDialog::class.java).warn(e.message)
                }
            }
            it.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {
                    if (e?.keyChar?.isLetterOrDigit() == true) {
                        msgTextField.requestFocus()
                        msgTextField.dispatchEvent(e)
                        lastSelectedIndex = 0
                    }
                }

                override fun keyPressed(e: KeyEvent?) {
                    if (e?.keyCode == KeyEvent.VK_ENTER && getReasonForEntry(it.selectedValue) == null) {
                        entry = it.selectedValue
                        doOKAction()
                    }
                }

                override fun keyReleased(e: KeyEvent?) {
                    if (e == null) return
                    if (e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN) {
                        if (e.keyCode == KeyEvent.VK_UP && lastSelectedIndex == thirdPartyList.selectedIndex) {
                            msgTextField.requestFocus()
                            msgTextField.text = previousIntent
                            historyIndex = -1
                            entry = getDefaultEntry()
                        } else lastSelectedIndex = thirdPartyList.selectedIndex
                    }
                }
            })
            it.addFocusListener(object : FocusListener {
                override fun focusGained(e: FocusEvent?) {
                    historyIndex = -2
                    msgTextField.text = previousIntent
                }

                override fun focusLost(e: FocusEvent?) {}
            })
        }

        thirdPartyScrollPane = JBScrollPane(
            thirdPartyList,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply {
            maximumSize = Dimension(300, 99999)
            minimumSize = Dimension(300, 0)
            preferredSize = Dimension(300, 0)
        }
        longthinkDescriptionScrollPane = JBScrollPane(
            longthinkDescriptionPane,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).apply {
            maximumSize = Dimension(600, 400)
            minimumSize = Dimension(600, 400)
            preferredSize = Dimension(600, 400)
        }
        runButton.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent?) {}
            override fun mousePressed(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {
                doOKAction()
            }
        })

        bookmarkButton.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent?) {}
            override fun mousePressed(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {
                entry.isBookmarked = !entry.isBookmarked
                (thirdPartyList.model as LongthinkTableModel).filter(msgTextField.text)
                bookmarkButton.icon =
                    if (entry.isBookmarked) Resources.Icons.BOOKMARK_CHECKED_24x24 else Resources.Icons.BOOKMARK_UNCHECKED_24x24
                ExtraState.insertLocalLongthinkInfo(entry.entryName, LocalLongthinkInfo.fromEntry(entry))
            }
        })
        likeButton.addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent?) {}
            override fun mousePressed(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {
                entry.isLiked = !entry.isLiked
                if (entry.isLiked) {
                    entry.likes++
                } else {
                    entry.likes--
                }
                (thirdPartyList.model as LongthinkTableModel).isLikedChanged(entry)
                (thirdPartyList.model as LongthinkTableModel).filter(msgTextField.text)
                likeButton.icon =
                    if (entry.isLiked) Resources.Icons.LIKE_CHECKED_24x24 else Resources.Icons.LIKE_UNCHECKED_24x24
                ExtraState.insertLocalLongthinkInfo(entry.entryName, LocalLongthinkInfo.fromEntry(entry))
                ExtraInfoService.addLike(entry.entryName, entry.isLiked)
            }
        })
        init()
        buttonMap[okAction]?.isVisible = false
        buttonMap[cancelAction]?.isVisible = false
        okAction.isEnabled = true
        cancelAction.isEnabled = true
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return msgTextField
    }

    private var lastCopyEntry: LongthinkFunctionEntry = LongthinkFunctionEntry()
    var entry: LongthinkFunctionEntry
        get() = _entry
        set(newVal) {
            if (activeMode != Mode.HISTORY && newVal == lastCopyEntry) return
            _entry = newVal
            val reason = getReasonForEntry(_entry)
            if (activeMode == Mode.HISTORY) {
                msgTextField.text = entry.intent
                thirdPartyList.clearSelection()
            } else {
                thirdPartyList.selectedValue = entry
            }
            if (reason != null) {
                runButton.isEnabled = false
                warning.isVisible = true
                warning.text = reason
            } else {
                runButton.isEnabled = true
                warning.isVisible = false
            }
            likeButton.icon = if (_entry.isLiked) Resources.Icons.LIKE_CHECKED_24x24 else
                Resources.Icons.LIKE_UNCHECKED_24x24
            bookmarkButton.icon = if (_entry.isBookmarked) Resources.Icons.BOOKMARK_CHECKED_24x24 else
                Resources.Icons.BOOKMARK_UNCHECKED_24x24
            longthinkDescriptionPane.text = entry.miniHtml
            longthinkLabel.text = entry.label
            lastCopyEntry = _entry.copy()
        }

    init {
        entry = getDefaultEntry()
    }

    override fun createCenterPanel(): JComponent {
        panel = FormBuilder.createFormBuilder().run {
            val internal = JPanel().run {
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                add(thirdPartyScrollPane)
                add(Box.createRigidArea(Dimension(5, 0)))
                add(FormBuilder.createFormBuilder().run {
                    val controlPanel = JPanel(BorderLayout())
                    val likeBookmark = JPanel()
                    val layout = BoxLayout(likeBookmark, BoxLayout.X_AXIS)
                    likeBookmark.layout = layout
                    likeBookmark.add(likeButton)
                    likeBookmark.add(Box.createRigidArea(Dimension(5, 0)))
                    likeBookmark.add(bookmarkButton)
                    controlPanel.add(likeBookmark, BorderLayout.LINE_END)

                    addComponent(longthinkLabel)
                    addComponent(controlPanel)
                    addComponent(longthinkDescriptionScrollPane)
                    this
                }.panel)
                this
            }

            addComponent(descriptionLabel, 1)
            addComponentFillVertically(msgTextField, 1)
            addComponentFillVertically(JPanel(), 6)
            addComponent(JBLabel("${RefactAIBundle.message("aiToolbox.availableFunctions")}:"), 6)
            addComponent(internal, 6)
            addComponent(JPanel(BorderLayout()).also {
                val meteringBalance = JPanel()
                meteringBalance.layout = BoxLayout(meteringBalance, BoxLayout.X_AXIS)
                meteringBalance.add(meteringBalanceLabel)
                it.add(meteringBalance, BorderLayout.LINE_START)

                val runWarning = JPanel()
                runWarning.layout = BoxLayout(runWarning, BoxLayout.X_AXIS)
                runWarning.add(warning)
                runWarning.add(Box.createRigidArea(Dimension(5, 0)))
                runWarning.add(runButton)
                it.add(runWarning, BorderLayout.LINE_END)
            }, 6)
            addComponentFillVertically(JPanel(), 6)
            this
        }.panel
        return panel
    }
}
