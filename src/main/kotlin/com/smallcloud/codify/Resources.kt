package com.smallcloud.codify

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

private fun getVersion(): String {
    val thisPlugin = PluginManager.getPlugins().find { it.name == "Codify" }
    if (thisPlugin != null) {
        return thisPlugin.version
    }
    return ""
}

object Resources {
    const val defaultContrastUrlSuffix = "v1/contrast"
    const val defaultRecallUrl: String = "https://www.smallcloud.ai/v1/streamlined-login-recall-ticket"
    const val defaultLoginUrl: String = "https://www.smallcloud.ai/v1/login"
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode/3b/multi"
    val version: String = getVersion()
    const val client: String = "jetbrains"

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
