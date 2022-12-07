package com.smallcloud.codify

import com.intellij.ide.plugins.PluginManager

private fun get_version(): String {
    val this_plugin = PluginManager.getPlugins().find { it.name == "Codify" }
    if (this_plugin != null) {
        return this_plugin.version
    }
    return ""
}

object Resources {
    val default_contrast_url: String = "https://inference.smallcloud.ai/v1/contrast"
    val default_activate_api_url: String = "https://www.smallcloud.ai/v1/api-activate"
    val default_temperature: Float = 0.2f
    val version: String = get_version()
    val client: String = "jetbrains"
}