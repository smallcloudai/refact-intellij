package com.obiscr.chatgpt.ui

import com.intellij.openapi.ui.NullableComponent
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.panes.gptchat.State
import com.smallcloud.codify.panes.gptchat.ui.MessageComponent
import com.smallcloud.codify.panes.gptchat.utils.md2html
import org.jdesktop.swingx.VerticalLayout
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Graphics
import javax.swing.Box
import javax.swing.JPanel
import javax.swing.JScrollBar
import javax.swing.ScrollPaneConstants


class MyScrollPane(view: Component?, vsbPolicy: Int, hsbPolicy: Int) : JBScrollPane(view, vsbPolicy, hsbPolicy) {
    override fun updateUI() {
        border = null
        super.updateUI()
    }

    override fun setCorner(key: String?, corner: Component?) {
        border = null
        super.setCorner(key, corner)
    }
}

class ShiftedMessage(val message: MessageComponent): JPanel() {
    val shift = Box.createRigidArea(Dimension(30, 0))
    init {
        layout = VerticalLayout()

        add(message)
//        if (message.me) {
////            layout = FlowLayout(FlowLayout.RIGHT)
//            add(shift, BorderLayout.WEST)
////            add
//        } else {
//            add(message, BorderLayout.EAST)
////            layout = FlowLayout(FlowLayout.LEFT)
////            add(shift/*, BorderLayout.EAST*/)
//        }
    }
}



class HistoryComponent(private val state: State): JBPanel<HistoryComponent>(), NullableComponent {
    private val myList = JPanel(VerticalLayout(JBUI.scale(10)))
    private val myScrollPane: MyScrollPane = MyScrollPane(myList, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER)
    private var myScrollValue = 0
    private val tip: MessageComponent
    init {
        border = JBUI.Borders.empty(10, 10, 10, 0)
        layout = BorderLayout(JBUI.scale(7), 0)
        background = UIUtil.getListBackground()
        val mainPanel = JPanel(BorderLayout(0, JBUI.scale(8)))
        mainPanel.isOpaque = false
        mainPanel.border = JBUI.Borders.emptyLeft(8)
        add(mainPanel)
        var helloString = "### Hi! \uD83D\uDC4B\n" +
                "### This chat has more features and it's more responsive than a free one you might find on the web."
        if (!AccountManager.isLoggedIn) {
            helloString += " Don't forget to log in!"
        }
        tip = MessageComponent(md2html(helloString), false)
        myList.isOpaque = true
        myList.background = UIUtil.getListBackground()
        myList.border = JBUI.Borders.emptyRight(10)
        myScrollPane.setBorder(null)
        mainPanel.add(myScrollPane)
        myScrollPane.getVerticalScrollBar().setAutoscrolls(true)
        myScrollPane.getVerticalScrollBar().addAdjustmentListener { e ->
            val value: Int = e.getValue()
            if (myScrollValue == 0 && value > 0 || myScrollValue > 0 && value == 0) {
                myScrollValue = value
                repaint()
            } else {
                myScrollValue = value
            }
        }
        add(tip)
    }

    fun clearHistory() {
        state.clear()
        myList.removeAll()
    }

    fun add(messageComponent: MessageComponent) {
        if (myList.componentCount > 0 && (myList.getComponent(0) as ShiftedMessage).message == tip) {
            myList.remove(0)
        }
        myList.add(ShiftedMessage(messageComponent))
        updateLayout()
        scrollToBottom()
        updateUI()
    }
    fun lastMessage(): MessageComponent {
        return (myList.getComponent(myList.componentCount - 1) as ShiftedMessage).message
    }


    fun scrollToBottom() {
        val verticalScrollBar: JScrollBar = myScrollPane.getVerticalScrollBar()
        verticalScrollBar.value = verticalScrollBar.maximum
    }

    fun updateLayout() {
        val layout = myList.layout
        val componentCount = myList.componentCount
        for (i in 0 until componentCount) {
            layout.removeLayoutComponent(myList.getComponent(i))
            layout.addLayoutComponent(null, myList.getComponent(i))
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (myScrollValue > 0) {
            g.color = JBColor.border()
            val y: Int = myScrollPane.getY() - 1
            g.drawLine(0, y, width, y)
        }
    }

    override fun isVisible(): Boolean {
        if (super.isVisible()) {
            val count = myList.componentCount
            for (i in 0 until count) {
                if (myList.getComponent(i).isVisible) {
                    return true
                }
            }
        }
        return false
    }

    override fun isNull(): Boolean {
        return !isVisible
    }
}