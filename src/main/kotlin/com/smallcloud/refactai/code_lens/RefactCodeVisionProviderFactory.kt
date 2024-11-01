package com.smallcloud.refactai.code_lens

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.initialize
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

class RefactCodeVisionProviderFactory : CodeVisionProviderFactory {
    override fun createProviders(project: Project): Sequence<CodeVisionProvider<*>> {
        initialize()
        val customization = getLSPProcessHolder(project)?.fetchCustomization() ?: return emptySequence()
        if (customization.has("code_lens")) {
            val allCodeLenses = customization.get("code_lens").asJsonObject
            val allCodeLensKeys = allCodeLenses.keySet().toList()
            val providers: MutableList<CodeVisionProvider<*>> = mutableListOf()
            for ((idx, key) in allCodeLensKeys.withIndex()) {
                val label = allCodeLenses.get(key).asJsonObject.get("label").asString
                var posAfter: String? = null
                if (idx != 0) {
                    posAfter = allCodeLensKeys[idx - 1]
                }
                providers.add(RefactCodeVisionProvider(key, posAfter, label, customization))
            }
            val ids = providers.map { it.id }
            project.service<CodeLensInvalidatorService>().setCodeLensIds(ids)

            return providers.asSequence()

        }
        return emptySequence()
    }
}