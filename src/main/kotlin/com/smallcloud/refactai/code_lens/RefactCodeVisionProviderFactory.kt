package com.smallcloud.refactai.code_lens

import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.openapi.project.Project
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance

class RefactCodeVisionProviderFactory : CodeVisionProviderFactory {
    override fun createProviders(project: Project): Sequence<CodeVisionProvider<*>> {
        val customization = getInstance(project).fetchCustomization()
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
                providers.add(RefactCodeVisionProvider(key, posAfter, label))
            }
            return providers.asSequence()

        }
        return emptySequence()
    }
}