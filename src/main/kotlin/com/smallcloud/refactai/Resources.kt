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

private fun getArch(): String {
    val arch = SystemInfo.OS_ARCH
    return when (arch) {
        "amd64" -> "x86_64"
        "aarch64" -> "aarch64"
        else -> arch
    }
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

    return "dist-${getArch()}-${suffix}"
}

object Resources {
    val binPrefix: String = getBinPrefix()

    const val defaultCloudAuthLink: String = "https://refact.smallcloud.ai/authentication?token=%s&utm_source=plugin&utm_medium=jetbrains&utm_campaign=login"
    val defaultCloudUrl: URI = URI("https://www.smallcloud.ai")
    val defaultCodeCompletionUrlSuffix = URI("v1/code-completion")
    val defaultContrastUrlSuffix = URI("v1/contrast")
    val defaultChatUrlSuffix = URI("v1/chat")
    val defaultRecallUrl: URI = defaultCloudUrl.resolve("/v1/streamlined-login-recall-ticket")
    val loginSuffixUrl = URI("v1/login")
    val defaultLikeReportUrl: URI = defaultCloudUrl.resolve("/v1/longthink-like")
    val defaultLoginUrl: URI = defaultCloudUrl.resolve(loginSuffixUrl)
    val defaultReportUrlSuffix: URI = URI("v1/telemetry-network")
    val defaultSnippetAcceptedUrlSuffix: URI = URI("v1/snippet-accepted")
    const val defaultTemperature: Float = 0.2f
    const val defaultModel: String = "CONTRASTcode"
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val loginCoolDown: Int = 300 // sec
    const val titleStr: String = "RefactAI"
    val pluginId: PluginId = getPluginId()
    const val stagingFilterPrefix: String = "STAGING"
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

        private fun makeIcon(path: String): Icon {
            return brushForTheme(IconLoader.getIcon(path, Resources::class.java))
        }

        val LOGO_RED_12x12: Icon = IconLoader.getIcon("/icons/refactai_logo_red_12x12.svg", Resources::class.java)
        val LOGO_RED_13x13: Icon = IconLoader.getIcon("/icons/refactai_logo_red_13x13.svg", Resources::class.java)
        val LOGO_12x12: Icon = IconLoader.getIcon("/icons/refactai_logo_12x12.svg", Resources::class.java)
        val LOGO_RED_16x16: Icon = IconLoader.getIcon("/icons/refactai_logo_red_16x16.svg", Resources::class.java)

        val LIKE_CHECKED_16x16: Icon = makeIcon("/icons/like_checked_16x16.svg")
        val LIKE_UNCHECKED_16x16: Icon = makeIcon("/icons/like_unchecked_16x16.svg")
        val LIKE_CHECKED_24x24: Icon = makeIcon("/icons/like_checked_24x24.svg")
        val LIKE_UNCHECKED_24x24: Icon = makeIcon("/icons/like_unchecked_24x24.svg")

        val BOOKMARK_CHECKED_16x16: Icon = makeIcon("/icons/bookmark_checked_16x16.svg")
        val DESCRIPTION_16x16: Icon = makeIcon("/icons/description_16x16.svg")
        val BOOKMARK_CHECKED_24x24: Icon = makeIcon("/icons/bookmark_checked_24x24.svg")
        val BOOKMARK_UNCHECKED_24x24: Icon = makeIcon("/icons/bookmark_unchecked_24x24.svg")

        val COIN_16x16: Icon = makeIcon("/icons/coin_16x16.svg")
        val HAND_12x12: Icon = makeIcon("/icons/hand_12x12.svg")
    }

    object ExtraUserDataKeys {
        val addedFromHL = Key.create<Boolean>("refact.added_from_hl")
        val lastEditor = Key.create<Editor>("refact.last_editor")
    }
}
