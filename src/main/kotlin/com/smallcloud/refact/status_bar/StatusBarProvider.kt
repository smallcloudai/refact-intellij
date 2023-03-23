package com.smallcloud.refact.status_bar

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jetbrains.annotations.Nullable

class SMCStatusBarWidgetFactory : StatusBarWidgetFactory, Disposable {
    override fun getId(): String {
        return "SMCStatusBarWidgetFactory"
    }

    override fun getDisplayName(): String {
        return "Codify"
    }

    override fun isAvailable(project: Project): Boolean {
        return true
    }

    @Nullable
    override fun createWidget(project: Project): StatusBarWidget {
        return SMCStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }

    override fun dispose() {
        TODO("Not yet implemented")
    }

}
