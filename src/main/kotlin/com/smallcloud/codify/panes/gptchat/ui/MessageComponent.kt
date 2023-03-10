package com.smallcloud.codify.panes.gptchat.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.smallcloud.codify.panes.gptchat.structs.ParsedText
import org.jdesktop.swingx.VerticalLayout
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.accessibility.AccessibleContext
import javax.swing.JEditorPane
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.text.AttributeSet
import javax.swing.text.html.StyleSheet

class MessageComponent(val question: List<ParsedText>,
                       val me: Boolean) : JBPanel<MessageComponent>() {
    private val myList = JPanel(VerticalLayout())
    private var rawTexts = emptyList<String?>()

    init {
        isDoubleBuffered = true
        isOpaque = true
        background = if (me) JBColor(0xEAEEF7, 0x45494A) else JBColor(0xE0EEF7, 0x2d2f30 /*2d2f30*/)
        border = JBUI.Borders.empty(10, 10, 10, 0)

        layout = BorderLayout(JBUI.scale(7), 0)
        val centerPanel = JPanel(VerticalLayout(JBUI.scale(8)))
        setContent(question)
        myList.isOpaque = false
        centerPanel.isOpaque = false
        centerPanel.border = JBUI.Borders.empty()
        add(centerPanel, BorderLayout.CENTER)
        centerPanel.add(myList)
        val actionPanel = JPanel(BorderLayout())
        actionPanel.isOpaque = false
        actionPanel.border = JBUI.Borders.empty()
        add(actionPanel, BorderLayout.EAST)
    }

    private fun setLinkForeground(styleSheet: StyleSheet) {
        val color = JBColor.namedColor("Notification.linkForeground", JBUI.CurrentTheme.Link.Foreground.ENABLED)
        styleSheet.addRule("a {color: " + ColorUtil.toHtmlColor(color) + "}")
    }

    private fun configureHtmlEditorKit(editorPane: JEditorPane) {
        val kit = HTMLEditorKitBuilder().withWordWrapViewFactory().withFontResolver { defaultFont, attributeSet ->
            if ("a".equals(attributeSet.getAttribute(AttributeSet.NameAttribute)?.toString(), ignoreCase = true)) {
                UIUtil.getLabelFont()
            } else defaultFont
        }.build()
        setLinkForeground(kit.styleSheet)
        editorPane.editorKit = kit
    }

    private fun createContentComponent(content: String, isCode: Boolean, index: Int): Component {
        val wrapper = JPanel(BorderLayout()).also {
            it.border = JBUI.Borders.empty(5)
            it.isOpaque = isCode
            if (isCode) {
                val isDark = ColorUtil.isDark(EditorColorsManager.getInstance().globalScheme.defaultBackground)
                it.background = if (isDark) ColorUtil.brighter(background, 2) else
                    ColorUtil.darker(background, 2)
            }
        }

        val component = JEditorPane()
        component.putClientProperty("isCode", isCode)
        component.isEditable = false
        component.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, java.lang.Boolean.TRUE)
        component.contentType = "text/html"
        component.isOpaque = false
        component.border = null
        configureHtmlEditorKit(component)
        component.putClientProperty(AccessibleContext.ACCESSIBLE_NAME_PROPERTY,
                StringUtil.unescapeXmlEntities(StringUtil.stripHtml(content, " ")))
        component.text = content

        val copyAction = JLabel(AllIcons.Actions.Copy)
        copyAction.cursor = Cursor(Cursor.HAND_CURSOR)
        copyAction.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard;
                clipboard.setContents(StringSelection(rawTexts[index]), null)
            }
        })
        if (isCode) {
            wrapper.add(JPanel(BorderLayout()).also {
                it.add(copyAction, BorderLayout.EAST)
                it.isOpaque = false
            }, BorderLayout.NORTH)
        }
        component.layout = FlowLayout()
        component.isEditable = false
        if (component.caret != null) {
            component.caretPosition = 0
        }
        wrapper.add(component)
        return wrapper
    }

    // <pre><code>
    fun setContent(content: List<ParsedText>) {
        rawTexts = content.map { it.rawText }
        content.forEachIndexed { index, element ->
            if (myList.components.size <= index) {
                myList.add(createContentComponent(element.htmlText, element.isCode, index))
                return@forEachIndexed
            }
            val editor = (myList.components[index] as JPanel).components.last() as JEditorPane
            if (element.isCode && !(editor.getClientProperty("isCode") as Boolean)) {
                myList.remove(index)
                myList.add(createContentComponent(element.htmlText, element.isCode, index), index)
                return@forEachIndexed
            }
            editor.putClientProperty("asd", "asd")
            if (editor.text == element.htmlText) {
                return@forEachIndexed
            } else {
                editor.text = element.htmlText
            }
            editor.updateUI()
        }
        myList.updateUI()
//        myList.components.lastOrNull()?.let {component ->
//            if (component is JEditorPane) {
//                component.text = content
//                component.updateUI()
//            }
//        }
    }
}