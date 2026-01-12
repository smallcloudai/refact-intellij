package com.smallcloud.refactai

import com.intellij.ide.ApplicationLoadListener
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import java.nio.file.Path

class JcefConfigurer : ApplicationLoadListener {
    private val logger = Logger.getInstance(JcefConfigurer::class.java)

    override suspend fun beforeApplicationLoaded(application: Application, configPath: Path) {
        try {
            val appInfo = ApplicationInfo.getInstance()
            val majorVersion = appInfo.majorVersion
            val minorVersion = appInfo.minorVersion

            // Only auto-fix for 2025.1.* - on 2025.2+ disabling out-of-process can cause rendering issues
            if (majorVersion != "2025" || minorVersion != "1") return

            val key = "ide.browser.jcef.out-of-process.enabled"
            val isOutOfProcRegistry = try { Registry.get(key).asBoolean() } catch (_: Exception) { false }
            val isOutOfProcProperty = System.getProperty(key, "").lowercase() == "true"
            val isOutOfProc = isOutOfProcRegistry || isOutOfProcProperty

            if (isOutOfProc) {
                logger.warn("Auto-disabling JCEF out-of-process mode for 2025.1.* (IJPL-186252 workaround)")
                try {
                    Registry.get(key).setValue(false)
                } catch (e: Exception) {
                    logger.warn("Failed to set Registry value: ${e.message}")
                    System.setProperty(PROP_CONFIG_FAILED, "registry")
                }
                System.setProperty(key, "false")
                System.setProperty(PROP_AUTO_CONFIGURED, "true")
                logger.info("JCEF out-of-process mode disabled successfully")
            }
        } catch (e: Exception) {
            logger.warn("JCEF configuration failed: ${e.message}")
            System.setProperty(PROP_CONFIG_FAILED, e.message ?: "unknown")
        }
    }

    companion object {
        private const val PROP_AUTO_CONFIGURED = "refact.jcef.auto-configured"
        private const val PROP_CONFIG_FAILED = "refact.jcef.config-failed"

        fun wasAutoConfigured(): Boolean = System.getProperty(PROP_AUTO_CONFIGURED) == "true"
        fun configurationFailed(): String? = System.getProperty(PROP_CONFIG_FAILED)

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
    }
}
