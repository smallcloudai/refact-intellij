package com.smallcloud.refactai.listeners

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.smallcloud.refactai.PluginState

class PluginListener: DynamicPluginListener, Disposable {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        Disposer.dispose(PluginState.instance)
    }

    override fun dispose() {
        val i = 0
    }
}