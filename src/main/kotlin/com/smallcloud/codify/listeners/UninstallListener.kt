package com.smallcloud.codify.listeners

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.smallcloud.codify.Resources
import com.smallcloud.codify.Resources.defaultCodifyUrl
import com.smallcloud.codify.UsageStats.Companion.instance as UsageStats
import com.smallcloud.codify.statistic.StatisticService.Companion.instance as StatisticService

class UninstallListener: PluginStateListener {
    override fun install(descriptor: IdeaPluginDescriptor) {}

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
        if (descriptor.pluginId != Resources.pluginId) {
            return
        }
        if (Thread.currentThread().stackTrace.any { it.methodName == "uninstallAndUpdateUi" }) {
            UsageStats.addStatistic(true, "uninstall", defaultCodifyUrl.toString(), "")
            UsageStats.forceReport()
            StatisticService.forceReport()
        }
    }
}