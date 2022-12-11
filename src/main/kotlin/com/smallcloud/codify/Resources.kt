package com.smallcloud.codify

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

private fun get_version(): String {
    val this_plugin = PluginManager.getPlugins().find { it.name == "Codify" }
    if (this_plugin != null) {
        return this_plugin.version
    }
    return ""
}

object Resources {
    val default_contrast_url_suffix = "v1/contrast"
    val default_recall_url: String = "https://www.smallcloud.ai/v1/streamlined-login-recall-ticket"
    val default_login_url: String = "https://www.smallcloud.ai/v1/login"
    val default_temperature: Float = 0.2f
    val default_model: String = "CONTRASTcode/3b/multi"
    val version: String = get_version()
    val client: String = "jetbrains"

    object Icons {
        val LOGO_FULL_WHITE: Icon? = IconLoader.findIcon("/icons/logo-full-white.svg")

        val LOGO_RED_12x12: Icon? = IconLoader.findIcon("/icons/codify_red_12x12.svg")
        val LOGO_LIGHT_12x12: Icon? = IconLoader.findIcon("/icons/codify_light_12x12.svg")
        val LOGO_DARK_12x12: Icon? = IconLoader.findIcon("/icons/codify_dark_12x12.svg")
        val LOGO_RED_16x16: Icon? = IconLoader.findIcon("/icons/codify_red_16x16.svg")
        val LOGO_LIGHT_16x16: Icon? = IconLoader.findIcon("/icons/codify_light_16x16.svg")
        val LOGO_DARK_16x16: Icon? = IconLoader.findIcon("/icons/codify_dark_16x16.svg")
    }
}