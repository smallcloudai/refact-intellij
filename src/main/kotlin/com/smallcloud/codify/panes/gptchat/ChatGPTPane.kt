package com.smallcloud.codify.panes.gptchat

import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.UIUtil
import com.obiscr.chatgpt.ui.HistoryComponent
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.panes.gptchat.ui.CustomSearchTextArea
import com.smallcloud.codify.panes.gptchat.ui.MessageComponent
import icons.CollaborationToolsIcons
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JTextArea


class ChatGPTPane {
    private val sendAction = object : DumbAwareAction(CollaborationToolsIcons.Send), LightEditCompatible {
        init {
            templatePresentation.hoveredIcon = CollaborationToolsIcons.SendHovered
            isEnabledInModalContext = AccountManager.isLoggedIn
        }
        override fun actionPerformed(e: AnActionEvent) {
            listener.doActionPerformed()
        }
        fun setEnabled(enabled: Boolean) {
            isEnabledInModalContext = enabled
        }

    }

    var searchTextArea: CustomSearchTextArea = CustomSearchTextArea(JBTextArea(), false).also {
        it.setExtraActions(sendAction)
    }
    private val listener = ChatGPTProvider(this)
    private var contentPanel = HistoryComponent()
    private var progressBar: JProgressBar? = null
    private val splitter: OnePixelSplitter = OnePixelSplitter(true,.9f)

    init {
        searchTextArea.isEnabled = AccountManager.isLoggedIn
        ApplicationManager.getApplication().messageBus.connect()
                .subscribe(AccountManagerChangedNotifier.TOPIC,object : AccountManagerChangedNotifier {
                    override fun isLoggedInChanged(isLoggedIn: Boolean) {
                        searchTextArea.isEnabled = isLoggedIn
                        sendAction.setEnabled(isLoggedIn)
                    }
                })

        splitter.dividerWidth = 2;
        searchTextArea.textArea.addKeyListener(listener)

        val top = JPanel(BorderLayout())
        progressBar = JProgressBar()
        top.add(progressBar, BorderLayout.NORTH)
        top.add(searchTextArea, BorderLayout.CENTER)
        top.border = UIUtil.getTextFieldBorder()

        splitter.firstComponent = contentPanel
        splitter.secondComponent = top
    }
    fun getComponent(): JPanel {
        return splitter
    }
    fun getComponentForFocus(): JTextArea {
        return searchTextArea.textArea
    }

    fun aroundRequest(status: Boolean) {
        progressBar!!.isIndeterminate = status
        sendAction.setEnabled(!status)
    }
    fun send(msg: String, selectedText: String) {
        contentPanel.clearHistory()
        listener.doActionPerformed(msg, selectedText)
    }

    fun add(messageComponent: MessageComponent) {
        contentPanel.add(messageComponent)
    }
    fun scrollToBottom() {
        contentPanel.scrollToBottom()
    }
    fun lastMessage(): MessageComponent {
        return contentPanel.lastMessage()
    }

}