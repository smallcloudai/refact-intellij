package com.smallcloud.refactai

import com.intellij.ui.jcef.JBCefApp
import com.intellij.ide.plugins.PluginInstaller
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.smallcloud.refactai.io.NotificationSSEClient
import com.smallcloud.refactai.lsp.LSPActiveDocNotifierService
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.initialize
import com.smallcloud.refactai.notifications.emitInfoWithDocLink
import com.smallcloud.refactai.notifications.notificationStartup
import com.smallcloud.refactai.panes.sharedchat.ChatPaneInvokeAction
import com.smallcloud.refactai.settings.AppSettingsState
import com.smallcloud.refactai.settings.settingsStartup
import com.smallcloud.refactai.utils.ResourceCache
import java.util.concurrent.atomic.AtomicBoolean
import com.smallcloud.refactai.lsp.LSPProcessHolder.Companion.getInstance as getLSPProcessHolder

class Initializer : ProjectActivity, Disposable {
    private val logger = Logger.getInstance("SMCInitializer")

    override suspend fun execute(project: Project) {
        val isUnitTest = ApplicationManager.getApplication().isUnitTestMode
        val shouldInitialize = !isUnitTest && !initialized.getAndSet(true)
        if (shouldInitialize) {
            JcefConfigurer.applyEarlyRemoteModeWorkaround()
            logger.info("Bin prefix = ${Resources.binPrefix}")
            initialize()
            if (AppSettingsState.instance.isFirstStart) {
                AppSettingsState.instance.isFirstStart = false
                invokeLater { ChatPaneInvokeAction().actionPerformed() }
            }
            settingsStartup()
            notificationStartup()
            UpdateChecker.instance

            checkJcefStatus()
            preWarmResources()
        }
        getLSPProcessHolder(project).ensureStartedIfNeeded("project-activity")
        project.getService(LSPActiveDocNotifierService::class.java)
        getLSPProcessHolder(project).ensureStartedIfNeeded("active-doc-notifier")
        project.getService(NotificationSSEClient::class.java).start()
    }

    private fun preWarmResources() {
        if (!JBCefApp.isSupported()) return
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val resources = listOf(
                    "webview/index.html",
                    "dist/chat/index.umd.cjs",
                    "dist/chat/style.css"
                )
                resources.forEach { path ->
                    ResourceCache.getOrLoad(path) {
                        javaClass.classLoader.getResourceAsStream(path)
                    }
                }
                logger.info("Pre-warmed ${resources.size} resources for chat")
            } catch (e: Exception) {
                logger.debug("Resource pre-warming failed: ${e.message}")
            }
        }
    }

    private fun checkJcefStatus() {
        if (!JBCefApp.isSupported()) {
            emitInfoWithDocLink(RefactAIBundle.message("notifications.chatCanNotStartWarning"), "https://docs.refact.ai/guides/plugins/jetbrains/troubleshooting/", false)
            return
        }
    }

    override fun dispose() {
    }
}

private val initialized = AtomicBoolean(false)
