package com.smallcloud.refactai.panes.sharedchat

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanes(val project: Project) : Disposable {
    private var component: JComponent? = null
    private var pane: SharedChatPane? = null
    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }

    private fun setupPanes() {
        invokeLater {
            holder.removeAll()
            pane = SharedChatPane(project)
            component = pane?.webView?.component
            holder.add(component)
        }
    }

    init {
        setupPanes()
    }

    fun getComponent(): JComponent {
        return holder
    }

    fun executeCodeLensCommand(command: String, sendImmediately: Boolean, openNewTab: Boolean) {
        pane?.executeCodeLensCommand(command, sendImmediately, openNewTab)
    }

    fun requestFocus() {
        component?.requestFocus()
    }

    fun newChat() {
        pane?.newChat()
    }

    override fun dispose() {
        pane?.dispose()
    }
}