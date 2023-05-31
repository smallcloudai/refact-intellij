package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
//import com.intellij.ui.NewUI
import com.intellij.util.IconUtil
import com.intellij.util.ui.StartupUiUtil
import java.net.URI
import javax.swing.Icon
fun getThisPlugin() = PluginManager.getPlugins().find { it.name == "Refact.ai" }

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
//    val isNewUI: Boolean by lazy { NewUI.isEnabled() }
    val defaultCloudUrl: URI = URI("https://www.smallcloud.ai")
    val defaultContrastUrlSuffix = URI("v1/contrast")
    val defaultChatUrlSuffix = URI("v1/chat")
    val defaultSelfHostedLongthinkFunctionsSuffix = URI("v1/longthink-functions")
    val defaultRecallUrl: URI = defaultCloudUrl.resolve("/v1/streamlined-login-recall-ticket")
    val defaultLoginUrlSuffix: URI = URI("/v1/login")
    val defaultLoginUrl: URI = defaultCloudUrl.resolve(defaultLoginUrlSuffix)
    val defaultReportUrl: URI = defaultCloudUrl.resolve("/v1/usage-stats")
    val defaultAcceptRejectReportUrl: URI = defaultCloudUrl.resolve("/v1/accept-reject-stats")
    val defaultLikeReportUrl: URI = defaultCloudUrl.resolve("/v1/longthink-like")
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode"
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val loginCoolDown: Int = 30 // sec
    const val inferenceLoginCoolDown: Int = 300 // sec
    const val titleStr: String = "RefactAI"
    val pluginId: PluginId = getPluginId()
    const val stagingFilterPrefix: String = "STAGING"
    val jbBuildVersion: String = ApplicationInfo.getInstance().build.toString()

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
        private fun makeIcon12(path: String): Icon {
            return brushForTheme(resizeSquaredIcon(IconLoader.getIcon(path, Resources::class.java), 12))
        }
        private fun makeIcon16(path: String): Icon {
            return brushForTheme(resizeSquaredIcon(IconLoader.getIcon(path, Resources::class.java), 16))
        }
        private fun makeIcon24(path: String): Icon {
            return brushForTheme(resizeSquaredIcon(IconLoader.getIcon(path, Resources::class.java), 24))
        }

        val LOGO_RED_12x12: Icon = resizeSquaredIcon(
                IconLoader.getIcon("/icons/refactai_logo_red.svg", Resources::class.java),
                12)
        val LOGO_RED_13x13: Icon = resizeSquaredIcon(
                IconLoader.getIcon("/icons/refactai_logo_red.svg", Resources::class.java),
                13)
        val LOGO_12x12: Icon = makeIcon12("/icons/refactai_logo.svg")
        val LOGO_RED_16x16: Icon = resizeSquaredIcon(
                IconLoader.getIcon("/icons/refactai_logo_red.svg", Resources::class.java),
                16)

        val LIKE_CHECKED_16x16: Icon = makeIcon16("/icons/like_checked.svg")
        val LIKE_UNCHECKED_16x16: Icon = makeIcon16("/icons/like_unchecked.svg")
        val LIKE_CHECKED_24x24: Icon = makeIcon24("/icons/like_checked.svg")
        val LIKE_UNCHECKED_24x24: Icon = makeIcon24("/icons/like_unchecked.svg")

        val BOOKMARK_CHECKED_16x16: Icon = makeIcon16("/icons/bookmark_checked.svg")
        val DESCRIPTION_16x16: Icon = makeIcon16("/icons/description.svg")
        val BOOKMARK_CHECKED_24x24: Icon = makeIcon24("/icons/bookmark_checked.svg")
        val BOOKMARK_UNCHECKED_24x24: Icon = makeIcon24("/icons/bookmark_unchecked.svg")

        val COIN_16x16: Icon = makeIcon16("/icons/coin.svg")
        val HAND_12x12: Icon = makeIcon12("/icons/hand.svg")
    }

    object ExtraUserDataKeys {
        val addedFromHL = Key.create<Boolean>("refact.added_from_hl")
        val lastEditor = Key.create<Editor>("refact.last_editor")
    }
}
