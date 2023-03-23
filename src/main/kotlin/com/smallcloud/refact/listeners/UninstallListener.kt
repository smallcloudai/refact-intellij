package com.smallcloud.refact.listeners

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.smallcloud.refact.Resources
import com.smallcloud.refact.Resources.defaultCodifyUrl
import com.smallcloud.refact.UsageStats.Companion.instance as UsageStats
import com.smallcloud.refact.statistic.StatisticService.Companion.instance as StatisticService

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