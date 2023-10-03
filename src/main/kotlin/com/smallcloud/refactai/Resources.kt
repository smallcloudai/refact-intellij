package com.smallcloud.refactai

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.IconUtil
import java.io.File
import java.net.URI
import javax.swing.Icon
import javax.swing.UIManager

fun getThisPlugin() = PluginManager.getPlugins().find { it.name == "Refact.ai" }

private fun getHomePath() : File {
    return getThisPlugin()!!.pluginPath.toFile()
}

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

private fun getBinPrefix(): String {
    var suffix = ""
    if (SystemInfo.isMac) {
        suffix = "apple-darwin"
    } else if (SystemInfo.isWindows) {
        suffix = "pc-windows-msvc"
    } else if (SystemInfo.isLinux) {
        suffix = "unknown-linux-gnu"
    }

    return "dist-${SystemInfo.OS_ARCH}-${suffix}"
}

object Resources {
    val binPrefix: String = getBinPrefix()

    const val defaultCloudAuthLink: String = "https://refact.smallcloud.ai/authentication?token=%s&utm_source=plugin&utm_medium=jetbrains&utm_campaign=login"
    val defaultCloudUrl: URI = URI("https://www.smallcloud.ai")
    val defaultCodeCompletionUrlSuffix = URI("v1/code-completion")
    val defaultChatUrlSuffix = URI("v1/chat")
    val defaultRecallUrl: URI = defaultCloudUrl.resolve("/v1/streamlined-login-recall-ticket")
    val defaultLoginUrl: URI = defaultCloudUrl.resolve("/v1/login")
    val defaultReportUrlSuffix: URI = URI("v1/telemetry-network")
    val defaultSnippetAcceptedUrlSuffix: URI = URI("v1/snippet-accepted")
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode"
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val loginCoolDown: Int = 300 // sec
    const val titleStr: String = "RefactAI"
    val pluginId: PluginId = getPluginId()
    val jbBuildVersion: String = ApplicationInfo.getInstance().build.toString()
    const val refactAIRootSettingsID = "refactai_root"

    object Icons {
        private fun brushForTheme(icon: Icon): Icon {
            return if (UIManager.getLookAndFeel().name.contains("Darcula")) {
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
