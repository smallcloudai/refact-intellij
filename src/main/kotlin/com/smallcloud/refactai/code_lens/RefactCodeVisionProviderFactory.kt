package com.smallcloud.refactai.code_lens

import com.google.gson.JsonObject
import com.intellij.codeInsight.codeVision.CodeVisionProvider
import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.openapi.project.Project
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getCustomizationDirectly
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.initialize

class RefactCodeVisionProviderFactory : CodeVisionProviderFactory {
    val customization: JsonObject?
    init {
        initialize()
        customization = getCustomizationDirectly()
    }

    override fun createProviders(project: Project): Sequence<CodeVisionProvider<*>> {
        if (customization == null) return emptySequence()
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