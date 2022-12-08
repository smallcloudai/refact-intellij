package com.smallcloud.codify.status_bar

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.openapi.wm.impl.status.TextPanel.WithIconAndArrows
import com.intellij.ui.ColorUtil
import com.intellij.util.Consumer
import com.smallcloud.codify.Resources.Icons.LOGO_DARK_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_LIGHT_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_RED_12x12
import com.smallcloud.codify.account.AccountManager.is_login
import com.smallcloud.codify.account.LoginStatusChangedNotifier
import com.smallcloud.codify.notifications.emit_login
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent

class SMCStatusBarWidget(project: Project) : EditorBasedWidget(project), CustomStatusBarWidget, WidgetPresentation {
    private var component: WithIconAndArrows? = null

    init {
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(LoginStatusChangedNotifier.LOGIN_STATUS_CHANGED_TOPIC, object : LoginStatusChangedNotifier {
                override fun isLoginChanged(is_login: Boolean) {
                    update(is_login)
                }
            })
    }

    override fun ID(): String {
        return javaClass.name
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getComponent(): JComponent {
        val component = WithIconAndArrows()
        component.icon = getIcon()
        component.text = "codify"
        component.toolTipText = tooltipText
        component.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    Objects.requireNonNull(clickConsumer)?.consume(e)
                }
            })
        this.component = component
        return component
    }

    private fun getIcon(): Icon? {
        val isDark = ColorUtil.isDark(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground())
        if (!is_login) {
            return LOGO_RED_12x12
        }
        return if (isDark) {
            LOGO_LIGHT_12x12
        } else LOGO_DARK_12x12
    }


    // Compatability implementation. DO NOT ADD @Override.
    override fun getPresentation(): WidgetPresentation? {
        return this
    }

    override fun getTooltipText(): String? {
        return "Codify"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { e: MouseEvent ->
            if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
                if (!is_login)
                    emit_login(project)
            }
        }
    }

    private fun update(is_login: Boolean) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    if (myProject == null || myProject.isDisposed || myStatusBar == null) {
                        return@invokeLater
                    }
                    component!!.icon = getIcon()
                    myStatusBar.updateWidget(ID())
                    val statusBar = WindowManager.getInstance().getStatusBar(myProject)
                    statusBar?.component?.updateUI()
                },
                ModalityState.any()
            )
    }

//    companion object {
//        private val PRO_SERVICE_LEVELS: Set<ServiceLevel?> = EnumSet.of(ServiceLevel.PRO, ServiceLevel.TRIAL)
//    }
}