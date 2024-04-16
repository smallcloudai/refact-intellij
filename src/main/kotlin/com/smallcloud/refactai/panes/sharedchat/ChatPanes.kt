package com.smallcloud.refactai.panes.sharedchat

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import java.awt.BorderLayout
import javax.swing.JPanel
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.containers.ContainerUtil
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.panes.gptchat.ChatGPTPane
import javax.swing.JComponent

class ChatPanes(val project: Project, private val parent: Disposable) {
    private val paneBaseName = "Chat"
    private val panes = JBRunnerTabs(project, parent)
    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }

    private val placeholder = JPanel().also { it ->
        it.layout = BorderLayout()
        it.add(JBLabel(RefactAIBundle.message("aiToolbox.panes.chat.placeholderSelfhosted")).also { label ->
            label.verticalAlignment = JBLabel.CENTER
            label.horizontalAlignment = JBLabel.CENTER
            label.isEnabled = false
        }, BorderLayout.CENTER)
    }

    private fun setupPanes(isAvailable: Boolean) {
        invokeLater {
            holder.removeAll()
            if (isAvailable) {
                holder.add(panes)
            } else {
                holder.add(placeholder)
            }
        }
    }

    init {
        setupPanes(true)
        addTab()
    }

    fun addTab() {
        val newPane = SharedChatPane(project)
        val component:  JComponent = newPane.webView.component
        val info = TabInfo(component)
        info.text = "Chat ${panes.tabs.size + 1}"
        Disposer.register(parent, newPane)

        // Add button
        info.setActions(DefaultActionGroup(object : AnAction(AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                addTab()
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }), ActionPlaces.EDITOR_TAB)

        // Delete button
        info.setTabLabelActions(DefaultActionGroup(object : AnAction(AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                // TODO: cancel requests
                // (info.component as SharedChatPane).cancelRequest()
                panes.removeTab(info)
                // TODO: remove from history
                Disposer.dispose(newPane)
                if (getVisibleTabs().isEmpty()) {
                    addTab()
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }), ActionPlaces.EDITOR_TAB)

        panes.addTab(info)
        panes.select(info, true)
    }

    fun getComponent(): JComponent {
        return holder
    }

    fun getVisibleTabs(): List<TabInfo?> {
        return ContainerUtil.filter(panes.tabs) { tabInfo -> !tabInfo.isHidden }
    }
}