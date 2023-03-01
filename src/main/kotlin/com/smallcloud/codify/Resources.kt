package com.smallcloud.codify

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.util.IconUtil
import com.intellij.util.ui.StartupUiUtil
import java.net.URI
import javax.swing.Icon

fun getThisPlugin() = PluginManager.getPlugins().find { it.name == Resources.codifyStr }

private fun getVersion(): String {
    val thisPlugin = getThisPlugin()
    if (thisPlugin != null) {
        return thisPlugin.version
    }
    return ""
}

private fun getPluginId(): PluginId {
    val thisPlugin = getThisPlugin()
    if (thisPlugin != null) {
        return thisPlugin.pluginId
    }
    return PluginId.getId("com.smallcloud.codify")
}

object Resources {
    val defaultCodifyUrl: URI = URI("https://www.smallcloud.ai")
    val defaultContrastUrlSuffix = URI("v1/contrast")
    val defaultRecallUrl: URI = defaultCodifyUrl.resolve("/v1/streamlined-login-recall-ticket")
    val defaultLoginUrl: URI = defaultCodifyUrl.resolve("/v1/login")
    val defaultReportUrl: URI = defaultCodifyUrl.resolve("/v1/usage-stats")
    val defaultAcceptRejectReportUrl: URI = defaultCodifyUrl.resolve("/v1/accept-reject-stats")
    val defaultLikeReportUrl: URI = defaultCodifyUrl.resolve("/v1/longthink-like")
    val developerModeLoginParameters = mapOf("want_staging_version" to "1")
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode"
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val loginCoolDown: Int = 30 // sec
    const val inferenceLoginCoolDown: Int = 300 // sec
    const val codifyStr: String = "Codify"
    val pluginId: PluginId = getPluginId()
    const val stagingFilterPrefix: String = "STAGING"

    object Icons {
        private fun brushForTheme(icon: Icon): Icon {
            return if (StartupUiUtil.isUnderDarcula()) {
                IconUtil.brighter(icon, 3)
            } else {
                IconUtil.darker(icon, 3)
            }
        }
        fun resizeSquaredIcon(icon: Icon, width: Int): Icon {
            return IconUtil.resizeSquared(icon, width)
        }
        private fun makeIcon16(path: String): Icon {
            return brushForTheme(resizeSquaredIcon(IconLoader.getIcon(path, Resources::class.java), 16))
        }
        private fun makeIcon24(path: String): Icon {
            return brushForTheme(resizeSquaredIcon(IconLoader.getIcon(path, Resources::class.java), 24))
        }

        val LOGO_RED_12x12: Icon = IconLoader.getIcon("/icons/codify_red_12x12.svg", Resources::class.java)
        val LOGO_LIGHT_12x12: Icon = IconLoader.getIcon("/icons/codify_light_12x12.svg", Resources::class.java)
        val LOGO_DARK_12x12: Icon = IconLoader.getIcon("/icons/codify_dark_12x12.svg", Resources::class.java)
        val LOGO_RED_16x16: Icon = IconLoader.getIcon("/icons/codify_red_16x16.svg", Resources::class.java)

        val LIKE_CHECKED_16x16: Icon = makeIcon16("/icons/like_checked.svg")
        val LIKE_UNCHECKED_16x16: Icon = makeIcon16("/icons/like_unchecked.svg")
        val LIKE_CHECKED_24x24: Icon = makeIcon24("/icons/like_checked.svg")
        val LIKE_UNCHECKED_24x24: Icon = makeIcon24("/icons/like_unchecked.svg")

        val BOOKMARK_CHECKED_16x16: Icon = makeIcon16("/icons/bookmark_checked.svg")
        val BOOKMARK_CHECKED_24x24: Icon = makeIcon24("/icons/bookmark_checked.svg")
        val BOOKMARK_UNCHECKED_24x24: Icon = makeIcon24("/icons/bookmark_unchecked.svg")

        val COIN_16x16: Icon = makeIcon16("/icons/coin.svg")
    }

    object ExtraUserDataKeys {
        val addedFromHL = Key.create<Boolean>("codify.added_from_hl")
    }
}
