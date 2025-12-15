package com.smallcloud.refactai

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.IconUtil
import java.io.File
import java.net.URI
import javax.swing.Icon
import javax.swing.UIManager


fun getThisPlugin(): IdeaPluginDescriptor? {
    val thisPluginById = PluginManagerCore.getPlugin(PluginId.getId("com.smallcloud.codify"))
    if (thisPluginById != null) {
        return thisPluginById
    }
    return null
}


private fun getHomePath(): File {
    return getThisPlugin()?.pluginPath?.toFile()
        ?: throw IllegalStateException("Plugin not found")
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

    val defaultCloudUrl: URI = URI("https://www.smallcloud.ai")
    val defaultCodeCompletionUrlSuffix = URI("v1/code-completion")
    val cloudUserMessage: URI = defaultCloudUrl.resolve("/v1/user-message")
    val defaultReportUrlSuffix: URI = URI("v1/telemetry-network")
    val defaultChatReportUrlSuffix: URI = URI("v1/telemetry-chat")
    val defaultSnippetAcceptedUrlSuffix: URI = URI("v1/snippet-accepted")
    val version: String = getVersion()
    const val client: String = "jetbrains"
    const val titleStr: String = "Refact.ai"
    val pluginId: PluginId = getPluginId()
    val jbBuildVersion: String = ApplicationInfo.getInstance().build.toString()
    const val refactAIRootSettingsID = "refactai_root"
    const val refactAIAdvancedSettingsID = "refactai_advanced_settings"

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
        val LOGO_12x12: Icon = makeIcon("/icons/refactai_logo_12x12.svg")
        val LOGO_RED_16x16: Icon = IconLoader.getIcon("/icons/refactai_logo_red_16x16.svg", Resources::class.java)

        val COIN_16x16: Icon = makeIcon("/icons/coin_16x16.svg")
        val HAND_12x12: Icon = makeIcon("/icons/hand_12x12.svg")
    }
}
