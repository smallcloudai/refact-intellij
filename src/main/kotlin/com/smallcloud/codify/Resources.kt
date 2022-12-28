package com.smallcloud.codify

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.util.IconLoader
import java.net.URI
import javax.swing.Icon

private fun getVersion(): String {
    val thisPlugin = PluginManager.getPlugins().find { it.name == "Codify" }
    if (thisPlugin != null) {
        return thisPlugin.version
    }
    return ""
}

object Resources {
    val defaultContrastUrlSuffix = URI("v1/contrast")
    val defaultRecallUrl: URI = URI("https://www.smallcloud.ai/v1/streamlined-login-recall-ticket")
    val defaultLoginUrl: URI = URI("https://www.smallcloud.ai/v1/login")
    val defaultReportUrl: URI = URI("https://www.smallcloud.ai/v1/usage-stats")
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode"
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val loginCooldown: Int = 30 // sec
    const val inferenceLoginCooldown: Int = 300 // sec
    const val waitWebsiteLoginStr: String = "Waiting for website login..."
    const val pluginDescriptionStr: String = "Codify: AI autocomplete and refactoring"

    object Icons {
        val LOGO_FULL_WHITE: Icon = IconLoader.getIcon("/icons/logo-full-white.svg", Resources::class.java)
        val LOGO_RED_12x12: Icon = IconLoader.getIcon("/icons/codify_red_12x12.svg", Resources::class.java)
        val LOGO_LIGHT_12x12: Icon = IconLoader.getIcon("/icons/codify_light_12x12.svg", Resources::class.java)
        val LOGO_DARK_12x12: Icon = IconLoader.getIcon("/icons/codify_dark_12x12.svg", Resources::class.java)
        val LOGO_RED_16x16: Icon = IconLoader.getIcon("/icons/codify_red_16x16.svg", Resources::class.java)
        val LOGO_LIGHT_16x16: Icon = IconLoader.getIcon("/icons/codify_light_16x16.svg", Resources::class.java)
        val LOGO_DARK_16x16: Icon = IconLoader.getIcon("/icons/codify_dark_16x16.svg", Resources::class.java)
    }
}
