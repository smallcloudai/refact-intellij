package com.smallcloud.refactai

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.gson.Gson
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.marketplace.IdeCompatibleUpdate
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.util.Urls
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.HttpRequests
import com.smallcloud.refactai.utils.getLastUsedProject
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

class UpdateChecker : Disposable {
    private val scheduler = AppExecutorUtil.createBoundedScheduledExecutorService(
        "SMCUpdateCheckerScheduler", 1
    )
    private var task: Future<*>? = null
    private var notification: Notification? = null

    init {
        task = scheduler.scheduleWithFixedDelay({
            checkNewVersion()
        }, 1, 5 * 60, TimeUnit.MINUTES)
    }

    private fun checkNewVersion() {
        val pluginHost = ApplicationInfoImpl.DEFAULT_PLUGINS_HOST

        val objectMapper = ObjectMapper()

        val data = Gson().toJson(
            mapOf(
                "build" to ApplicationInfo.getInstance().build.asString(),
                "pluginXMLIds" to listOf(Resources.pluginId.idString)
            )
        )

        val thisPlugin = getThisPlugin() ?: return

        val newVersions = HttpRequests
            .post(
                Urls.newFromEncoded("${pluginHost}/api/search/compatibleUpdates").toExternalForm(),
                HttpRequests.JSON_CONTENT_TYPE
            )
            .productNameAsUserAgent()
            .throwStatusCodeException(false)
            .connect {
                it.write(data)
                objectMapper.readValue(it.inputStream, object : TypeReference<List<IdeCompatibleUpdate>>() {})
            }
        if (newVersions.isEmpty()) {
            return
        }
        val thisNewPlugin = newVersions.find { it.pluginId == Resources.pluginId.idString } ?: return

        if (thisNewPlugin.version > thisPlugin.version) {
            emitUpdate(thisNewPlugin.version)
        }
    }

    private fun emitUpdate(newVersion: String) {
        notification?.apply {
            expire()
            hideBalloon()
        }

        val project = getLastUsedProject()
        val notification = NotificationGroupManager
            .getInstance()
            .getNotificationGroup("Refact AI Notification Group")
            .createNotification(
                Resources.titleStr,
                RefactAIBundle.message("updateChecker.newVersionIsAvailable", newVersion),
                NotificationType.INFORMATION
            )
        notification.icon = Resources.Icons.LOGO_RED_16x16

        notification.addAction(NotificationAction.createSimple(
            RefactAIBundle.message("updateChecker.update")
        ) {
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                PluginManagerConfigurable::class.java
            )
            notification.expire()
        })
        notification.notify(project)
        this.notification = notification
    }


    companion object {
        @JvmStatic
        val instance: UpdateChecker
            get() = ApplicationManager.getApplication().getService(UpdateChecker::class.java)
    }

    override fun dispose() {
        task?.cancel(true)
        scheduler.shutdown()
    }
}