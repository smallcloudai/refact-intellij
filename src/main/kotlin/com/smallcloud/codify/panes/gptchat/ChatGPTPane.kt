package com.smallcloud.codify.panes.gptchat

import com.intellij.find.SearchTextArea
import com.intellij.ide.ui.laf.darcula.ui.DarculaButtonUI
import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.UIUtil
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.panes.gptchat.listeneres.SendListener
import com.smallcloud.codify.panes.gptchat.utils.HtmlUtil.md2html
import org.intellij.plugins.markdown.ui.preview.jcef.MarkdownJCEFHtmlPanel
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JProgressBar
import com.smallcloud.codify.panes.gptchat.State.Companion.instance as State



class ChatGPTPane {
    var searchTextArea: SearchTextArea = SearchTextArea(JBTextArea(), true)
    var button: JButton = JButton("Send")
    private val listener = SendListener(this)
    private var contentPanel: MarkdownJCEFHtmlPanel? = null
    private var progressBar: JProgressBar? = null
    private val splitter: OnePixelSplitter = OnePixelSplitter(true,.9f)
    init {
        searchTextArea.isEnabled = AccountManager.isLoggedIn
        button.isEnabled = AccountManager.isLoggedIn
        ApplicationManager.getApplication().messageBus.connect()
                .subscribe(AccountManagerChangedNotifier.TOPIC,object : AccountManagerChangedNotifier {
                    override fun isLoggedInChanged(isLoggedIn: Boolean) {
                        searchTextArea.isEnabled = isLoggedIn
                        button.isEnabled = isLoggedIn
                    }
                })

        splitter.setDividerWidth(2);
        searchTextArea.textArea.addKeyListener(listener)
        button.addActionListener(listener)
        button.setUI(DarculaButtonUI())

        val top = JPanel(BorderLayout())
        progressBar = JProgressBar()
        top.add(progressBar, BorderLayout.NORTH)
        top.add(searchTextArea, BorderLayout.CENTER)
        top.add(button, BorderLayout.EAST)
        top.border = UIUtil.getTextFieldBorder()
        contentPanel = MarkdownJCEFHtmlPanel()

        var helloString = "# Hi! \uD83D\uDC4B\n" +
                "# This chat has more features and it's more responsive than a free one you might find on the web."
        if (!AccountManager.isLoggedIn) {
            helloString += " Don't forget to log in!"
        }

        val s: String = md2html(helloString)
        contentPanel!!.setHtml(s, 0)

        splitter.setFirstComponent(contentPanel!!.component)
        splitter.setSecondComponent(top)
    }
    fun getComponent(): JPanel {
        return splitter
    }

    fun aroundRequest(status: Boolean) {
        progressBar!!.isIndeterminate = status
        button.isEnabled = !status
    }

    fun showContent(conversation: String? = null) {
        val html = md2html(conversation ?: State.buildConversations() ?: "")
        contentPanel!!.setHtml(html, html.length)
        contentPanel!!.scrollBy(0, html.length)
        contentPanel!!.component.repaint()
    }

    fun send(msg: String, selectedText: String) {
        listener.doActionPerformed(msg, selectedText)
    }

}