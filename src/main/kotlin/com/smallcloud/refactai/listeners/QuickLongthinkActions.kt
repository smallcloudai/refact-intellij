package com.smallcloud.refactai.listeners

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.modes.diff.DiffIntentProvider
import com.smallcloud.refactai.modes.diff.dialog.filter
import com.smallcloud.refactai.panes.gptchat.ChatGPTPaneInvokeAction
import com.smallcloud.refactai.privacy.ActionUnderPrivacy
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class QuickLongthinkAction(
        var function: LongthinkFunctionEntry = LongthinkFunctionEntry(),
        var hlFunction: LongthinkFunctionEntry = LongthinkFunctionEntry(),
        ): ActionUnderPrivacy() {
    override fun setup(e: AnActionEvent) {
        e.presentation.text = function.label
        e.presentation.icon = Resources.Icons.LOGO_RED_16x16
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val hl = editor.selectionModel.selectedText == null
        AIToolboxInvokeAction().doActionPerformed(editor, if (hl) hlFunction else function)
    }
}

class AskChatAction: DumbAwareAction(Resources.Icons.LOGO_RED_16x16) {
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = InferenceGlobalContext.isCloud
        e.presentation.text = "Ask in Chat..."
    }

    override fun actionPerformed(e: AnActionEvent) {
        ChatGPTPaneInvokeAction().doActionPerformed(true)
    }
}


class QuickLongthinkActionsService {
    private val groups = mutableListOf<DefaultActionGroup>()
    private val actions = MutableList(TOP_N) { QuickLongthinkAction() }
    init {
        for (groupName in listOf("EditorPopupMenu")) {
            var group = ActionManager.getInstance().getAction(groupName) ?: continue
            group = group as DefaultActionGroup
            groups.add(group)
        }
    }

    fun recreateActions() {
        groups.forEach { group ->
            actions.forEach { action ->
                if (group.containsAction(action)) {
                    group.remove(action)
                }
            }
        }
        var filteredLTFunctions = filter(DiffIntentProvider.instance.defaultThirdPartyFunctions, "", true)
        filteredLTFunctions = filteredLTFunctions.filter { !it.catchAny() }.subList(0, TOP_N)

        var filteredHLFunctions = filter(DiffIntentProvider.instance.defaultThirdPartyFunctions, "", false)
        filteredHLFunctions = filteredHLFunctions.filter { !it.catchAny() }.subList(0, TOP_N)

        filteredLTFunctions.zip(filteredHLFunctions).zip(actions).forEach { (functions, action) ->
            action.function = functions.first.copy(intent = functions.first.label)
            action.hlFunction = functions.second.copy(intent = functions.first.label)
        }
        groups.forEach { group ->
            actions.reversed().forEach { action ->
                if (action.function.label.isNotEmpty()) {
                    group.addAction(action, Constraints(Anchor.FIRST, "RefactAIAskChat"))
                }
            }
        }
    }

    companion object {
        const val TOP_N = 3
        @JvmStatic
        val instance: QuickLongthinkActionsService
            get() = ApplicationManager.getApplication().getService(QuickLongthinkActionsService::class.java)
    }
}