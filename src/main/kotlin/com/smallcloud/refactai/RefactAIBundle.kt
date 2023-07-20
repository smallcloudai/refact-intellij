package com.smallcloud.refactai

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "bundles.RefactAI"

object RefactAIBundle : DynamicBundle(BUNDLE) {
    private val INSTANCE: RefactAIBundle = RefactAIBundle

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return INSTANCE.getMessage(key, *params)
    }

}