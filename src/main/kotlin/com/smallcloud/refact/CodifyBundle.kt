package com.smallcloud.refact

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "bundles.Codify"

object CodifyBundle : DynamicBundle(BUNDLE) {
    private val INSTANCE: CodifyBundle = CodifyBundle

    @Nls
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any): String {
        return INSTANCE.getMessage(key, *params)
    }

}