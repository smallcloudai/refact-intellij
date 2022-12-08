package com.smallcloud.codify.status_bar

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.StatusBarWidgetProvider
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable


class SMCStatusBarWidgetFactory : StatusBarWidgetFactory {
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
//        Disposer.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean {
        return true
    }

}