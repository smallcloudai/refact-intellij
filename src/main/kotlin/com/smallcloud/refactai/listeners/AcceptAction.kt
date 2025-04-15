package com.smallcloud.refactai.listeners


import com.intellij.codeInsight.hint.HintManagerImpl.ActionToIgnore
import com.intellij.codeInsight.inline.completion.InlineCompletion
import com.intellij.codeInsight.inline.completion.session.InlineCompletionContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler
import com.intellij.openapi.util.TextRange
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.codecompletion.EditorRefactLastCompletionIsMultilineKey
import com.smallcloud.refactai.codecompletion.EditorRefactLastSnippetTelemetryIdKey
import com.smallcloud.refactai.codecompletion.InlineCompletionGrayTextElementCustom
import com.smallcloud.refactai.modes.ModeProvider
import com.smallcloud.refactai.statistic.UsageStats
import kotlin.math.absoluteValue

const val ACTION_ID_ = "TabPressedAction"

class TabPressedAction : EditorAction(InsertInlineCompletionHandler()), ActionToIgnore {
    val ACTION_ID = ACTION_ID_

    init {
        this.templatePresentation.icon = Resources.Icons.LOGO_RED_16x16
    }

    class InsertInlineCompletionHandler : EditorWriteActionHandler() {
        override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
            Logger.getInstance("RefactTabPressedAction").debug("executeWriteAction")
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            if (provider.isInCompletionMode()) {
                InlineCompletion.getHandlerOrNull(editor)?.insert()
                 EditorRefactLastSnippetTelemetryIdKey[editor]?.also {
                     editor.project?.service<UsageStats>()?.snippetAccepted(it)
                     EditorRefactLastSnippetTelemetryIdKey[editor] = null
                     EditorRefactLastCompletionIsMultilineKey[editor] = null
                 }
            } else {
                provider.onTabPressed(editor, caret, dataContext)
            }
        }

        override fun isEnabledForCaret(
            editor: Editor,
            caret: Caret,
            dataContext: DataContext
        ): Boolean {
            val provider = ModeProvider.getOrCreateModeProvider(editor)
            if (provider.isInCompletionMode()) {
                val ctx = InlineCompletionContext.getOrNull(editor) ?: return false
                if (ctx.state.elements.isEmpty()) return false
                val elem = ctx.state.elements.first()
                val isMultiline = EditorRefactLastCompletionIsMultilineKey[editor]
                if (isMultiline && elem is InlineCompletionGrayTextElementCustom.Presentable) {
                    val startOffset = elem.startOffset() ?: return false
                    if (elem.delta == 0)
                        return elem.delta == (caret.offset - startOffset).absoluteValue
                    else {
                        val prefixOffset = editor.document.getLineStartOffset(caret.logicalPosition.line)
                        return elem.delta == (caret.offset - prefixOffset)
                    }
                }
                return true
            } else {
                return ModeProvider.getOrCreateModeProvider(editor).modeInActiveState()
            }
        }
    }
}
