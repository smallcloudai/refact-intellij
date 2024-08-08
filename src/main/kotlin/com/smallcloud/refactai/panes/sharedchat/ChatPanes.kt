package com.smallcloud.refactai.panes.sharedchat

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanes(val project: Project) {
    private var component: JComponent? = null
    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }

    private fun setupPanes() {
        invokeLater {
            holder.removeAll()
            val newPane = SharedChatPane(project)
            component = newPane.webView.component
            holder.add(component)
        }
    }

    init {
        setupPanes()
    }

    fun getComponent(): JComponent {
        return holder
    }

    fun requestFocus() {
        component?.requestFocus()
    }
}