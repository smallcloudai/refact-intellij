package com.smallcloud.refactai.panes.gptchat

import com.intellij.execution.ui.layout.impl.JBRunnerTabs
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.ui.tabs.TabInfo
import com.intellij.util.containers.ContainerUtil
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.struct.DeploymentMode
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext


class ChatGPTPanes(project: Project, parent: Disposable) {
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
        holder.removeAll()
        if (isAvailable) {
            holder.add(panes)
        } else {
            holder.add(placeholder)
        }
    }

    init {
        setupPanes(!InferenceGlobalContext.isSelfHosted)
        ApplicationManager.getApplication()
                .messageBus
                .connect(PluginState.instance)
                .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                    override fun deploymentModeChanged(newValue: DeploymentMode) {
                        setupPanes(newValue == DeploymentMode.CLOUD)
                    }
                })
    }

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
        return holder
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

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
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

            override fun getActionUpdateThread(): ActionUpdateThread {
                return ActionUpdateThread.EDT
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