package com.smallcloud.refactai

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.registry.Registry

object JcefConfigurer {
    fun isAffectedVersion(): Boolean {
        return try {
            val appInfo = ApplicationInfo.getInstance()
            appInfo.majorVersion == "2025" && appInfo.minorVersion == "1"
        } catch (_: Exception) { false }
    }

    fun isOutOfProcessEnabled(): Boolean {
        val key = "ide.browser.jcef.out-of-process.enabled"
        val fromRegistry = try { Registry.get(key).asBoolean() } catch (_: Exception) { false }
        val fromProperty = System.getProperty(key, "").lowercase() == "true"
        return fromRegistry || fromProperty
    }

    fun getPerformanceHints(): List<String> {
        val hints = mutableListOf<String>()

        if (isAffectedVersion() && isOutOfProcessEnabled()) {
            hints.add("JCEF out-of-process mode may cause freezes in 2025.1.*")
        }

        val gpuDisabled = System.getProperty("ide.browser.jcef.gpu.disable", "false") == "true"
        if (gpuDisabled) {
            hints.add("JCEF GPU is disabled - this may reduce performance")
        }

        return hints
    }

    fun getRecommendedVmOptions(): String {
        return buildString {
            appendLine("# Recommended JCEF VM options for optimal performance:")
            appendLine()
            if (isAffectedVersion()) {
                appendLine("# Fix for IJPL-186252 freeze bug in 2025.1.*")
                appendLine("-Dide.browser.jcef.out-of-process.enabled=false")
                appendLine()
            }
            appendLine("# Force windowed rendering (faster than OSR)")
            appendLine("-Drefact.jcef.force-osr=false")
        }
    }
}
