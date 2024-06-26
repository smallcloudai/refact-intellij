package com.smallcloud.refactai.panes.sharedchat

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.containers.ContainerUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanes(val project: Project, private val parent: Disposable) {
    private val paneBaseName = "Chat"
    private val panes = JBRunnerTabs(project, parent)
    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }

    private fun setupPanes() {
        invokeLater {
            holder.removeAll()
            holder.add(panes)
            restoreOrAddNew()
        }
    }

    private fun restoreOrAddNew() {
        ChatHistory.instance.state.let {
            if (it.chatHistory.isNotEmpty()) {
                it.getAll().forEach { item ->
                    restoreTab(item)
                }
            } else {
                addTab()
            }
        }
    }

    private fun restoreTab(item: ChatHistoryItem) {
        val newPane = SharedChatPane(project)
        val component:  JComponent = newPane.webView.component
        val info = TabInfo(component)
        info.text = item.title ?: "Chat"

        newPane.restoreWhenReady(item.id, item.messages, item.model)
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
                newPane.handleChatStop(item.id)
                panes.removeTab(info)
                ChatHistory.instance.state.removeItem(item.id)
                if (getVisibleTabs().isEmpty()) {
                    addTab()
                }
            }

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
            }
        }), ActionPlaces.EDITOR_TAB)

        panes.addTab(info)

//        val devToolsBrowser = JBCefBrowser.createBuilder()
//            .setCefBrowser(newPane.webView.cefBrowser.devTools)
//            .setClient(newPane.webView.jbCefClient)
//            .build();
//
//        val devInfo = TabInfo(devToolsBrowser.component)
//        devInfo.text = "DevTools"
//        panes.addTab(devInfo)
//        devToolsBrowser.openDevtools()
    }

    init {
        setupPanes()
    }

    fun addTab(title: String? = null) {
        val newPane = SharedChatPane(project)
        val component:  JComponent = newPane.webView.component
        val info = TabInfo(component)
        info.text = title ?: "Chat"
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
                ChatHistory.instance.state.removeItem(newPane.id)
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


//        val devToolsBrowser = JBCefBrowser.createBuilder()
//            .setCefBrowser(newPane.webView.cefBrowser.devTools)
//            .setClient(newPane.webView.jbCefClient)
//            .build();
//
//        val devInfo = TabInfo(devToolsBrowser.component)
//        devInfo.text = "DevTools"
//        panes.addTab(devInfo)
//        devToolsBrowser.openDevtools()
    }

    fun getComponent(): JComponent {
        return holder
    }

    fun requestFocus() {
        panes.requestFocus()
    }

    fun getVisibleTabs(): List<TabInfo?> {
        return ContainerUtil.filter(panes.tabs) { tabInfo -> !tabInfo.isHidden }
    }
}