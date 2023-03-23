package com.smallcloud.refact.panes.gptchat

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.containers.ContainerUtil
import javax.swing.JComponent


class ChatGPTPanes(project: Project, parent: Disposable) {
    private val paneBaseName = "Chat"
    private val panes = JBRunnerTabs(project, parent)

    private fun getTabNextText(intent: String): String {
        if (intent.isNotEmpty()) {
            if (intent.length > 18) {
                return intent.substring(0, 18) + "..."
            }
            return intent
        }

        val allNames = panes.tabs.map { it.text }
        var counter = 0
        for (name in allNames) {
            val currentSuffix = name.substringAfterLast(paneBaseName).toIntOrNull()
            if (currentSuffix != null && currentSuffix != counter) {
                break
            }
            counter++
        }
        return paneBaseName + if (counter == 0) "" else counter.toString()
    }


    init {
        addTab()
    }

    fun getComponent(): JComponent {
        return panes
    }

    fun addTab(intent: String = "", selectedText: String = "", needInsertCode: Boolean = false) {
        val newPane = ChatGPTPane()
        if (intent.isNotEmpty()) {
            newPane.send(intent, selectedText)
        }
        newPane.searchTextArea.needToInline = needInsertCode
        val info = TabInfo(newPane)
        info.text = getTabNextText(intent)
        info.preferredFocusableComponent = newPane.getComponentForFocus()
        info.setActions(DefaultActionGroup(object : AnAction(AllIcons.General.Add) {
            override fun actionPerformed(e: AnActionEvent) {
                addTab()
            }
        }), ActionPlaces.EDITOR_TAB)
        info.setTabLabelActions(DefaultActionGroup(object : AnAction(AllIcons.Actions.Close) {
            override fun actionPerformed(e: AnActionEvent) {
                (info.component as ChatGPTPane).cancelRequest()
                panes.removeTab(info)
                if (getVisibleTabs().isEmpty()) {
                    addTab()
                }
            }
        }), ActionPlaces.EDITOR_TAB)
        panes.addTab(info)
        panes.select(info, true)
    }

    fun getVisibleTabs(): List<TabInfo?> {
        return ContainerUtil.filter(panes.tabs) { tabInfo -> !tabInfo.isHidden }
    }

    fun renameTab(pane: ChatGPTPane, intent: String) {
        val tabInfo = panes.tabs.find { it.component == pane }
        tabInfo?.text = getTabNextText(intent)
    }

    fun requestFocus() {
        panes.requestFocus()
    }

    fun send(intent: String, selectedText: String) {
        addTab(intent, selectedText)
    }
}