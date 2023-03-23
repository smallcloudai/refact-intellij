package com.smallcloud.refactai.listeners

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.defaultCloudUrl
import com.smallcloud.refactai.UsageStats.Companion.instance as UsageStats
import com.smallcloud.refactai.statistic.StatisticService.Companion.instance as StatisticService

class UninstallListener: PluginStateListener {
    override fun install(descriptor: IdeaPluginDescriptor) {}

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
        if (descriptor.pluginId != Resources.pluginId) {
            return
        }
        if (Thread.currentThread().stackTrace.any { it.methodName == "uninstallAndUpdateUi" }) {
            UsageStats.addStatistic(true, "uninstall", defaultCloudUrl.toString(), "")
            UsageStats.forceReport()
            StatisticService.forceReport()
        }
    }
}