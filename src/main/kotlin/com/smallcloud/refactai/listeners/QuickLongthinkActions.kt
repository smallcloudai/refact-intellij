package com.smallcloud.refactai.listeners

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.aitoolbox.filter
import com.smallcloud.refactai.panes.gptchat.ChatGPTPaneInvokeAction
import com.smallcloud.refactai.privacy.ActionUnderPrivacy
import com.smallcloud.refactai.struct.LongthinkFunctionEntry
import com.smallcloud.refactai.struct.LongthinkFunctionVariation
import com.smallcloud.refactai.aitoolbox.LongthinkFunctionProvider.Companion.instance as LongthinkFunctionProvider
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class QuickLongthinkAction(
        var function: LongthinkFunctionVariation = QuickLongthinkActionsService.DUMMY_LONGTHINK,
        var hlFunction: LongthinkFunctionVariation = QuickLongthinkActionsService.DUMMY_LONGTHINK,
        ): ActionUnderPrivacy() {
    override fun setup(e: AnActionEvent) {
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val hl = editor.selectionModel.selectedText == null

        val variation = (if (hl) hlFunction else function)
        val entry = variation.getFunctionByFilter()
        e.presentation.text = entry.label
        e.presentation.icon = Resources.Icons.LOGO_RED_16x16
        e.presentation.isEnabledAndVisible = variation != QuickLongthinkActionsService.DUMMY_LONGTHINK
    }

    override fun actionPerformed(e: AnActionEvent) {
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext) ?: return
        val hl = editor.selectionModel.selectedText == null
        AIToolboxInvokeAction().doActionPerformed(editor,
                if (hl) hlFunction.getFunctionByFilter() else function.getFunctionByFilter())
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
        var filteredLTFunctions = filter(LongthinkFunctionProvider.functionVariations, "", true)
        filteredLTFunctions = filteredLTFunctions.filter { !it.catchAny() && it.supportSelection }
        var realL = minOf(TOP_N, filteredLTFunctions.size)
        filteredLTFunctions = filteredLTFunctions.subList(0, minOf(TOP_N, filteredLTFunctions.size))
        if (TOP_N > realL) {
            filteredLTFunctions = filteredLTFunctions.toMutableList().apply {
                repeat(TOP_N - realL) {
                    this.add(DUMMY_LONGTHINK)
                }
            }.toList()
        }

        var filteredHLFunctions = filter(LongthinkFunctionProvider.functionVariations, "", false)
        filteredHLFunctions = filteredHLFunctions.filter { !it.catchAny() && it.supportHighlight }
        realL = minOf(TOP_N, filteredHLFunctions.size)
        filteredHLFunctions = filteredHLFunctions.subList(0, realL)
        if (TOP_N > realL) {
            filteredHLFunctions = filteredHLFunctions.toMutableList().apply {
                repeat(TOP_N - realL) {
                    this.add(DUMMY_LONGTHINK)
                }
            }.toList()
        }

        filteredLTFunctions.zip(filteredHLFunctions).zip(actions).forEach { (functions, action) ->
            action.function = functions.first
            action.hlFunction = functions.second
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
        val DUMMY_LONGTHINK = LongthinkFunctionVariation(listOf(LongthinkFunctionEntry()), listOf(""))
        @JvmStatic
        val instance: QuickLongthinkActionsService
            get() = ApplicationManager.getApplication().getService(QuickLongthinkActionsService::class.java)
    }
}
