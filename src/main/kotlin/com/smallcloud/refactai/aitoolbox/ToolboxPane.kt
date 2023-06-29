package com.smallcloud.refactai.aitoolbox

import com.intellij.icons.AllIcons
import com.intellij.ide.actions.ShowSettingsUtilImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.ColorUtil.*
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.getLabelForeground
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.aitoolbox.table.LongthinkTable
import com.smallcloud.refactai.aitoolbox.table.LongthinkTableModel
import com.smallcloud.refactai.aitoolbox.table.renderers.colorize
import com.smallcloud.refactai.aitoolbox.utils.getFilteredIntent
import com.smallcloud.refactai.aitoolbox.utils.getReasonForEntryFromState
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.listeners.SelectionChangedNotifier
import com.smallcloud.refactai.panes.RefactAIToolboxPaneFactory
import com.smallcloud.refactai.settings.AppRootConfigurable
import com.smallcloud.refactai.settings.ExtraState
import com.smallcloud.refactai.statistic.ExtraInfoService
import com.smallcloud.refactai.struct.DeploymentMode
import com.smallcloud.refactai.struct.LocalLongthinkInfo
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import com.smallcloud.refactai.utils.getLastUsedProject
import com.smallcloud.refactai.utils.makeLinksPanel
import org.jdesktop.swingx.HorizontalLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.event.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.aitoolbox.LongthinkFunctionProvider.Companion.instance as LongthinkFunctionProvider
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


private const val GLOBAL_MARGIN = 15
private const val LEFT_RIGHT_GLOBAL_MARGIN = 10

class ToolboxPane(parent: Disposable) {
    private val action = LongthinkAction()
    private val filterTextField: JBTextField
    private var longthinkList: LongthinkTable
    private val longthinkScrollPane: JBScrollPane
    private var previousIntent: String = ""
    private var lastFocusedComponent: Component
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
    private var longthinkDescriptionScrollPane: JBScrollPane = JBScrollPane()
    private val browser = if (JBCefApp.isSupported()) JBCefBrowser.createBuilder()
            .setOffScreenRendering(false)
            .setEnableOpenDevToolsMenuItem(true)
            .build().also {
                it.component.border = JBUI.Borders.empty()
            } else null

    private val runButton = JButton("Run", AllIcons.Debugger.ThreadRunning).apply {
        addActionListener {
            doOKAction()
        }
    }

    private val meteringBalanceLabel: JBLabel = JBLabel().apply {
        fun setup(balance: Int?) {
            if (balance != null) {
                text = (balance / 100).toString()
            }
            isVisible = balance != null
        }
        setup(AccountManager.meteringBalance)
        toolTipText = RefactAIBundle.message("aiToolbox.meteringBalance")
        icon = colorize(Resources.Icons.COIN_16x16, foreground)
        ApplicationManager.getApplication()
                .messageBus.connect(parent)
                .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                    override fun meteringBalanceChanged(newBalance: Int?) {
                        setup(newBalance)
                    }
                })
    }

    private val filtersPanel = JPanel(HorizontalLayout(3)).apply {
        border = JBUI.Borders.empty(0, 3)
    }
    private fun filterFunc(variation: LongthinkFunctionVariation): Boolean {
        if (State.activeFilters.isEmpty()) return true
        return variation.availableFilters.contains(State.activeFilters.first())
    }


    private fun filterLongthinkList(text: String) {
        longthinkList.filter(text) { filterFunc(it) }
        longthinkList.selectionModel.setSelectionInterval(0, 0)
    }

    private val historyIntents: List<LongthinkFunctionEntry>
        get() {
            return LongthinkFunctionProvider.historyIntents
        }

    private var lastSelectedIndex = 0

    init {
        val longthinkVariations = LongthinkFunctionProvider.functionVariations
        longthinkList = LongthinkTable(longthinkVariations, State.haveSelection)
        filterTextField = object : JBTextField() {
            private var hint: String = "↓ commands; ↑ history"

            init {
                font = super.getFont().deriveFont(14f)
                emptyText.text = hint
                emptyText.setFont(font)

                val newSize = Dimension(maximumSize.width, preferredSize.height)
                maximumSize = newSize
                minimumSize = newSize
                preferredSize = newSize
                addKeyListener(object : KeyListener {
                    override fun keyTyped(e: KeyEvent?) {}
                    override fun keyReleased(e: KeyEvent?) {
                        if (e?.keyCode == KeyEvent.VK_ENTER) {
                            doOKAction()
                        } else if (e?.keyCode == KeyEvent.VK_BACK_SPACE ||
                                e?.keyCode == KeyEvent.VK_DELETE
                        ) {
                            filterLongthinkList(text)
                        } else if (e?.keyCode == KeyEvent.VK_ESCAPE && isDescriptionVisible) {
                            isDescriptionVisible = false
                        }
                    }

                    override fun keyPressed(e: KeyEvent?) {
                        if (e?.keyCode == KeyEvent.VK_UP || e?.keyCode == KeyEvent.VK_DOWN) {
                            if (e.keyCode == KeyEvent.VK_UP) {
                                State.historyIndex++
                            } else if (e.keyCode == KeyEvent.VK_DOWN) {
                                State.historyIndex--
                            }
                            State.historyIndex = minOf(maxOf(State.historyIndex, -2), historyIntents.size - 1)
                            if (State.historyIndex > -1) {
                                entry = historyIntents[State.historyIndex]
                            } else if (State.historyIndex == -1) {
                                text = previousIntent
                                functionVariation = getDefaultEntry()
                            } else if (State.historyIndex == -2) {
                                previousIntent = text
                                longthinkList.requestFocus()
                                longthinkList.selectionModel.setSelectionInterval(lastSelectedIndex, lastSelectedIndex)
                                return
                            }
                        }
                    }
                })
                document.addDocumentListener(object : DocumentListener {
                    override fun insertUpdate(e: DocumentEvent?) {
                        if (State.activeMode == Mode.FILTER) {
                            previousIntent = text
                            filterLongthinkList(text)
                        }
                        State.currentIntent = text
                        isDescriptionVisible = false
                    }

                    override fun removeUpdate(e: DocumentEvent?) {
                        if (State.activeMode == Mode.FILTER) {
                            previousIntent = text
                            filterLongthinkList(text)
                            functionVariation = getDefaultEntry()
                        }
                        State.currentIntent = text
                        isDescriptionVisible = false
                    }

                    override fun changedUpdate(e: DocumentEvent?) {
                        if (State.activeMode == Mode.FILTER) {
                            previousIntent = text
                            filterLongthinkList(text)
                            functionVariation = getDefaultEntry()
                        }
                        State.currentIntent = text
                        isDescriptionVisible = false
                    }
                })
            }
        }.also {
            it.addFocusListener(object : FocusListener {
                override fun focusGained(e: FocusEvent?) {
                    lastFocusedComponent = it
                }
                override fun focusLost(e: FocusEvent?) {}
            })
        }
        longthinkList.also {
            it.setupDescriptionColumn({ openDescriptionForEntry() },
                    RefactAIBundle.message("aiToolbox.clickForDetails"))
            it.addMouseListener(object : MouseListener {
                override fun mouseClicked(event: MouseEvent?) {
                    if (event == null) return
                    if (event.clickCount == 2 && event.button == MouseEvent.BUTTON1) {
                        doOKAction()
                        return
                    }
                    if (event.clickCount == 1) {
                        try {
                            functionVariation = it.selectedValue
                            lastSelectedIndex = it.selectedIndex
                        } catch (_: Exception) {
                            Logger.getInstance(ToolboxPane::class.java).warn("Entry not found")
                        }
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
                    val tempEntry = it.selectedValue.apply { intent = filterTextField.text }
                    functionVariation = tempEntry
                } catch (e: Exception) {
                    Logger.getInstance(ToolboxPane::class.java).warn(e.message)
                }
            }
            it.addKeyListener(object : KeyListener {
                override fun keyTyped(e: KeyEvent?) {
                    if (e?.keyChar?.isLetterOrDigit() == true) {
                        filterTextField.requestFocus()
                        filterTextField.dispatchEvent(e)
                        lastSelectedIndex = 0
                    }
                }

                override fun keyPressed(e: KeyEvent?) {
                    if (e?.keyCode == KeyEvent.VK_ENTER && getReasonForEntryFromState() == null) {
                        functionVariation = it.selectedValue
                        doOKAction()
                    }
                }

                override fun keyReleased(e: KeyEvent?) {
                    if (e == null) return
                    if (e.keyCode == KeyEvent.VK_UP || e.keyCode == KeyEvent.VK_DOWN) {
                        if (e.keyCode == KeyEvent.VK_UP && lastSelectedIndex == it.selectedIndex) {
                            filterTextField.requestFocus()
                            filterTextField.text = previousIntent
                            State.historyIndex = -1
                            functionVariation = getDefaultEntry()
                        } else lastSelectedIndex = it.selectedIndex
                    }
                }
            })
            it.addFocusListener(object : FocusListener {
                override fun focusGained(e: FocusEvent?) {
                    State.historyIndex = -2
                    filterTextField.text = previousIntent
                    lastFocusedComponent = longthinkList
                }

                override fun focusLost(e: FocusEvent?) {}
            })
        }
        longthinkScrollPane = JBScrollPane(
                longthinkList,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        ).also {
            it.preferredSize = it.maximumSize
        }
        if (browser != null) {
            longthinkDescriptionScrollPane = JBScrollPane(
                    browser.component,
                    ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            ).also {
                it.border = JBUI.Borders.empty()
            }
        }

        lastFocusedComponent = filterTextField
        class LongthinkFilterPanel(val text: String) : JPanel() {
            var isActive = State.activeFilters.contains(text)
            private var mouseInside: Boolean = false

            init {
                add(JLabel(text).apply {
                    foreground = JBColor.background()
                    font = super.getFont().deriveFont(Font.BOLD, 10f)
                })
                maximumSize = Dimension(preferredSize.width, preferredSize.height)
                minimumSize = Dimension(preferredSize.width, preferredSize.height)
                background = JBColor.lazy {
                    val isDark = isDark(EditorColorsManager.getInstance().globalScheme.defaultBackground)
                    if (mouseInside) return@lazy if (isDark) UIManager.getColor("Table.selectionForeground") else
                        JBColor.foreground()
                    if (isActive) return@lazy if (isDark) brighter(JBColor.foreground(), 2) else
                        darker(UIManager.getColor("Table.disabledForeground"), 6)
                    return@lazy if (isDark) darker(JBColor.foreground(), 4) else
                        brighter(UIManager.getColor("Table.disabledForeground"), 2)
                }
                addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent?) {
                        if (e == null) return
                        if (e.button == MouseEvent.BUTTON1) {
                            State.activeFilters.add(text)
                            isActive = true
                            updateUI()
                            modelChooserCB.selectedItem = text
                            (longthinkList.model as LongthinkTableModel).currentData.forEach {
                                it.activeFilter = text
                            }
                            filtersPanel.components.forEach {
                                if (it is LongthinkFilterPanel) {
                                    if (this@LongthinkFilterPanel != it) {
                                        it.isActive = false
                                        State.activeFilters.remove(it.text)
                                        it.updateUI()
                                    }
                                }
                            }

                            filterLongthinkList(filterTextField.text)
                        }
                    }

                    override fun mouseEntered(e: MouseEvent?) {
                        mouseInside = true
                        updateUI()
                    }
                    override fun mouseExited(e: MouseEvent?) {
                        mouseInside = false
                        updateUI()
                    }
                })
            }
        }
        filtersPanel.removeAll()
        LongthinkFunctionProvider.intentFilters.forEach {
            filtersPanel.add(LongthinkFilterPanel(it))
        }

        ApplicationManager.getApplication().messageBus
                .connect(parent)
                .subscribe(LongthinkFunctionProviderChangedNotifier.TOPIC, object : LongthinkFunctionProviderChangedNotifier {
                    override fun longthinkFunctionsChanged(functions: List<LongthinkFunctionEntry>) {
                        val model = longthinkList.model as LongthinkTableModel
                        model.data = LongthinkFunctionProvider.functionVariations
                        filterLongthinkList(filterTextField.text)
                    }

                    override fun longthinkFiltersChanged(filters: List<String>) {
                        State.activeFilters.clear()
                        if (filters.isNotEmpty()) State.activeFilters.add(filters.first())
                        filtersPanel.removeAll()
                        filters.forEach {
                            filtersPanel.add(LongthinkFilterPanel(it))
                        }
                        val model = longthinkList.model as LongthinkTableModel
                        model.data = LongthinkFunctionProvider.functionVariations
                        filterLongthinkList(filterTextField.text)
                    }
                })
        ApplicationManager.getApplication().messageBus
                .connect(parent)
                .subscribe(SelectionChangedNotifier.TOPIC, object : SelectionChangedNotifier {
                    override fun isSelectionChanged(isSelection: Boolean) {
                        (longthinkList.model as LongthinkTableModel).fromHL = !isSelection
                        filterLongthinkList(filterTextField.text)
                    }
                })
    }

    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }
    private val placeholder = JPanel().also { it ->
        it.layout = BorderLayout()
        it.add(JPanel().apply {
            layout = VerticalFlowLayout(VerticalFlowLayout.MIDDLE)

            add(JBLabel(RefactAIBundle.message("aiToolbox.panes.toolbox.placeholder")).also { label ->
                label.verticalAlignment = JBLabel.CENTER
                label.horizontalAlignment = JBLabel.CENTER
                label.isEnabled = false
            })
            add(LinkLabel<String>("Settings", null).also { label ->
                label.verticalAlignment = JBLabel.CENTER
                label.horizontalAlignment = JBLabel.CENTER
                label.addMouseListener(object : MouseListener {
                    override fun mouseClicked(e: MouseEvent?) {}
                    override fun mousePressed(e: MouseEvent?) {}
                    override fun mouseEntered(e: MouseEvent?) {}
                    override fun mouseExited(e: MouseEvent?) {}
                    override fun mouseReleased(e: MouseEvent?) {
                        ShowSettingsUtilImpl.getInstance().showSettingsDialog(getLastUsedProject(),
                                AppRootConfigurable::class.java)
                    }
                })
            })
        }, BorderLayout.CENTER)
    }

    private fun setupPanes(isAvailable: Boolean) {
        invokeLater {
            holder.removeAll()
            if (isAvailable) {
                holder.add(toolpaneComponent)
            } else {
                holder.add(placeholder)
            }
        }
    }
    init {
        setupPanes(InferenceGlobalContext.isSelfHosted ||
                (InferenceGlobalContext.isCloud && AccountManager.isLoggedIn))
        ApplicationManager.getApplication()
                .messageBus
                .connect(parent)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC,
                        object : InferenceGlobalContextChangedNotifier {
                            override fun deploymentModeChanged(newMode: DeploymentMode) {
                                setupPanes(when (newMode) {
                                    DeploymentMode.SELF_HOSTED -> {
                                        true
                                    }
                                    DeploymentMode.CLOUD -> {
                                        AccountManager.isLoggedIn
                                    }
                                })
                            }
                        })
        ApplicationManager.getApplication()
                .messageBus
                .connect(parent)
                .subscribe(AccountManagerChangedNotifier.TOPIC,
                        object : AccountManagerChangedNotifier {
                            override fun isLoggedInChanged(isLoggedIn: Boolean) {
                                setupPanes(InferenceGlobalContext.isSelfHosted ||
                                        (InferenceGlobalContext.isCloud && isLoggedIn))
                            }
                        })
    }

    private var modelChooserCB = ComboBox<String>().apply {
        addItemListener {
            val filter = it.item as String
            functionVariation.activeFilter = if (filter == "refact") "" else filter
            entry = functionVariation.getFunctionByFilter(functionVariation.activeFilter)
        }
    }

    private var likeButton: LinkLabel<String> = LinkLabel<String>(null, Resources.Icons.LIKE_CHECKED_24x24).apply {
        addMouseListener(object : MouseListener {
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
                (longthinkList.model as LongthinkTableModel).isLikedChanged(functionVariation)
                (longthinkList.model as LongthinkTableModel).filter(filterTextField.text) { filterFunc(it) }
                icon = if (entry.isLiked) Resources.Icons.LIKE_CHECKED_24x24 else Resources.Icons.LIKE_UNCHECKED_24x24
                ExtraState.instance.insertLocalLongthinkInfo(entry.entryName, LocalLongthinkInfo.fromEntry(entry))
                ExtraInfoService.instance.addLike(entry.entryName, entry.isLiked)
            }
        })
    }
    private var bookmarkButton: LinkLabel<String> = LinkLabel<String>(null, Resources.Icons.BOOKMARK_CHECKED_24x24).apply {
        addMouseListener(object : MouseListener {
            override fun mouseClicked(e: MouseEvent?) {}
            override fun mousePressed(e: MouseEvent?) {}
            override fun mouseEntered(e: MouseEvent?) {}
            override fun mouseExited(e: MouseEvent?) {}
            override fun mouseReleased(e: MouseEvent?) {
                entry.isBookmarked = !entry.isBookmarked
                (longthinkList.model as LongthinkTableModel).filter(filterTextField.text) { filterFunc(it) }
                icon = if (entry.isBookmarked) Resources.Icons.BOOKMARK_CHECKED_24x24 else Resources.Icons.BOOKMARK_UNCHECKED_24x24
                ExtraState.instance.insertLocalLongthinkInfo(entry.entryName, LocalLongthinkInfo.fromEntry(entry))
            }
        })
    }

    private val htmlStyle: String
        get() {
            val backgroundColor = toHtmlColor(JBColor.background())
            val fontColor = toHtmlColor(getLabelForeground())
            val fontSizePx = JLabel().run {
                val metric = getFontMetrics(font)
                metric.ascent
            }

            return "<style>\n" +
                    "    body {\n" +
                    "     background-color: $backgroundColor !important;\n" +
                    "     color: $fontColor !important;\n" +
                    "     font-size: ${fontSizePx}px !important;\n" +
                    "     font-family: system-ui !important;\n" +
                    "     box-sizing: border-box;\n" +
                    "     padding-left: ${LEFT_RIGHT_GLOBAL_MARGIN};\n" +
                    "     padding-right: ${LEFT_RIGHT_GLOBAL_MARGIN};\n" +
                    "     margin: 0;" +
                    "    }\n" +
                    "</style>"
        }

    private fun openDescriptionForEntry() {
        isDescriptionVisible = true
    }

    private fun doOKAction() {
        val filteredIntent = getFilteredIntent(filterTextField.text)
        if (getReasonForEntryFromState(entry) == null || filteredIntent.endsWith("?")) {
            val entry = entry.copy().apply {
                intent = entry.modelFixedIntent.ifEmpty {
                    if (catchAny()) {
                        filterTextField.text
                    } else {
                        label
                    }
                }
            }

            if (filteredIntent.endsWith("?") || entry.catchQuestionMark) {
                RefactAIToolboxPaneFactory.chat?.preview(filteredIntent,
                        State.editor?.selectionModel?.selectedText ?: "")
                RefactAIToolboxPaneFactory.focusChat()
            } else {
                action.doActionPerformed(entry)
            }
            isDescriptionVisible = false
            filterTextField.text = ""
            lastSelectedIndex = 0
            State.historyIndex = -1
            LongthinkFunctionProvider.pushFrontHistoryIntent(entry)
        }
    }

    private fun getDefaultEntry(): LongthinkFunctionVariation {
        return try {
            (longthinkList.model as LongthinkTableModel).elementAt(0).apply { intent = filterTextField.text }
        } catch (e: Exception) {
            LongthinkFunctionVariation(listOf(LongthinkFunctionEntry(filterTextField.text)), listOf(""))
        }
    }

    private val longthinkLabel: JBLabel = JBLabel().apply {
        font = JBUI.Fonts.create(font.family, 18)
        val b = border
        border = CompoundBorder(b, EmptyBorder(JBUI.insetsLeft(LEFT_RIGHT_GLOBAL_MARGIN)))
    }

    private val mainComponent: JComponent = FormBuilder.createFormBuilder().apply {
        addComponent(filtersPanel)
        addComponent(longthinkScrollPane, GLOBAL_MARGIN)
    }.panel

    private val descriptionComponent: JComponent = FormBuilder.createFormBuilder().apply {
        addComponent(JPanel().apply {
            layout = BorderLayout()
            add(JButton("Back").apply {
                addActionListener {
                    isDescriptionVisible = false
                }
            }, BorderLayout.WEST)
            add(runButton, BorderLayout.EAST)
        })
        addComponent(JPanel(BorderLayout()).apply {
            val likeBookmark = JPanel()
            val layout = BoxLayout(likeBookmark, BoxLayout.X_AXIS)
            likeBookmark.layout = layout
            likeBookmark.add(likeButton)
            likeBookmark.add(Box.createRigidArea(Dimension(5, 0)))
            likeBookmark.add(bookmarkButton)
            add(likeBookmark, BorderLayout.LINE_END)
            add(modelChooserCB, BorderLayout.LINE_START)
        })
        addComponent(longthinkLabel)
        addComponent(longthinkDescriptionScrollPane, GLOBAL_MARGIN)
    }.panel.also {
        it.isVisible = false
    }

    private val toolpaneComponent = JPanel(VerticalFlowLayout()).apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        add(filterTextField)
        add(mainComponent)
        add(descriptionComponent)
        add(JPanel().apply {
            layout = BorderLayout()
            border = JBUI.Borders.empty(LEFT_RIGHT_GLOBAL_MARGIN)
            add(meteringBalanceLabel, BorderLayout.WEST)
            add(makeLinksPanel(), BorderLayout.EAST)
        })
    }

    private var isDescriptionVisible: Boolean
        get() {
            return descriptionComponent.isVisible
        }
        set(newVal) {
            mainComponent.isVisible = !newVal
            descriptionComponent.isVisible = newVal
        }

    fun getComponent(): JComponent {
        return holder
    }

    private var lastCopyEntry: LongthinkFunctionEntry = LongthinkFunctionEntry()

    private var functionVariation: LongthinkFunctionVariation =
            LongthinkFunctionVariation(listOf(LongthinkFunctionEntry()), listOf(""))
        set(newVal) {
            field = newVal
            (modelChooserCB.model as DefaultComboBoxModel).removeAllElements()
            (modelChooserCB.model as DefaultComboBoxModel).addAll(field.availableFilters.map { if (it == "") "refact" else it })
            modelChooserCB.selectedItem = if (field.activeFilter == "") "refact" else field.activeFilter
            modelChooserCB.isVisible = field.availableFilters.size > 1
            entry = field.getFunctionByFilter()
        }


    var entry: LongthinkFunctionEntry
        get() = State.entry
        set(newVal) {
            if (State.activeMode != Mode.HISTORY && newVal == lastCopyEntry) return
            State.entry = newVal
            val reason = getReasonForEntryFromState(entry)
            if (State.activeMode == Mode.HISTORY) {
                filterTextField.text = entry.intent
                longthinkList.clearSelection()
            } else {
//                longthinkList.selectedValue = entry
            }
            if (reason != null) {
                runButton.isEnabled = false
                runButton.toolTipText = reason
            } else {
                runButton.isEnabled = true
                runButton.toolTipText = null
            }
            likeButton.icon = if (State.entry.isLiked) Resources.Icons.LIKE_CHECKED_24x24 else
                Resources.Icons.LIKE_UNCHECKED_24x24
            bookmarkButton.icon = if (State.entry.isBookmarked) Resources.Icons.BOOKMARK_CHECKED_24x24 else
                Resources.Icons.BOOKMARK_UNCHECKED_24x24
            longthinkDescriptionPane.text = entry.miniHtml
            browser?.loadHTML(entry.miniHtml + htmlStyle)
            longthinkLabel.text = entry.label
            lastCopyEntry = State.entry.copy()
        }

    fun requestFocus() {
        lastFocusedComponent.requestFocus()
    }
    fun isFocused(): Boolean {
        return filterTextField.isFocusOwner || longthinkList.isFocusOwner
    }
}