package com.smallcloud.refact.modes.completion

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupListener
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.openapi.editor.EditorKind
import com.smallcloud.refact.modes.ModeProvider

class PopupCompletionContributor: CompletionContributor() {
    override fun fillCompletionVariants(
        parameters: CompletionParameters, resultSet: CompletionResultSet
    ) {
        val provider = ModeProvider.getOrCreateModeProvider(parameters.editor)

        if (parameters.editor.editorKind != EditorKind.MAIN_EDITOR) {
            return
        }
        if (provider.isInCompletionMode()) {
            val listener = CompletionLookupListener.getOrCreate(parameters.editor)
            registerLookupListener(parameters, listener)
        }
    }

    private fun registerLookupListener(
        parameters: CompletionParameters, lookupListener: LookupListener
    ) {
        val lookupEx = LookupManager.getActiveLookup(parameters.editor) ?: return
        lookupEx.removeLookupListener(lookupListener)
        lookupEx.addLookupListener(lookupListener)
    }
}
