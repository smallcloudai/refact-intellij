package com.smallcloud.refactai.listeners

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginStateListener
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.Resources.defaultCloudUrl
import com.smallcloud.refactai.account.AccountManager
import com.smallcloud.refactai.statistic.UsageStatistic
import com.smallcloud.refactai.statistic.StatisticService.Companion.instance as StatisticService
import com.smallcloud.refactai.statistic.UsageStats.Companion.instance as UsageStats

class UninstallListener: PluginStateListener {
    override fun install(descriptor: IdeaPluginDescriptor) {}

    override fun uninstall(descriptor: IdeaPluginDescriptor) {
        if (descriptor.pluginId != Resources.pluginId) {
            return
        }
        if (Thread.currentThread().stackTrace.any { it.methodName == "uninstallAndUpdateUi" }) {
            UsageStats.addStatistic(true, UsageStatistic("uninstall"), defaultCloudUrl.toString(), "")
            UsageStats.forceReport()
            StatisticService.forceReport()
            BrowserUtil.browse("https://refact.ai/feedback?ide=${Resources.client}&tenant=${AccountManager.user}")
        }
    }
}