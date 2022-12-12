package com.smallcloud.codify.status_bar

import com.intellij.icons.AllIcons
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
import com.smallcloud.codify.*
import com.smallcloud.codify.Resources.Icons.LOGO_DARK_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_LIGHT_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_RED_12x12
import com.smallcloud.codify.Resources.default_contrast_url_suffix
import com.smallcloud.codify.Resources.default_model
import com.smallcloud.codify.account.AccountManager.is_logged_in
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.notifications.emit_login
import com.smallcloud.codify.notifications.emit_regular
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
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun isLoggedInChanged(unused: Boolean) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
                override fun websiteMessageChanged(newMsg: String?) {
                    update(newMsg)
                }
                override fun inferenceMessageChanged(newMsg: String?) {
                    update(newMsg)
                }
                override fun pluginEnableChanged(unused: Boolean) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(ConnectionChangedNotifier.TOPIC, object : ConnectionChangedNotifier {
                override fun statusChanged(unused: ConnectionStatus) {
                    update(null)
                }
                override fun lastErrorMsgChanged(newMsg: String?) {
                    update(newMsg)
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
        if (!SMCPlugin.instant.is_enable)
            return AllIcons.Diff.GutterCheckBoxIndeterminate
        if (!is_logged_in) {
            val isDark = ColorUtil.isDark(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground())
            return if (isDark) {
                LOGO_LIGHT_12x12
            } else LOGO_DARK_12x12
        }
        val c_stat = Connection.status
        if (c_stat == ConnectionStatus.DISCONNECTED)
            return AllIcons.Debugger.ThreadStates.Socket
        else if (c_stat == ConnectionStatus.ERROR)
            return AllIcons.Debugger.Db_exception_breakpoint
        else if (c_stat == ConnectionStatus.CONNECTED) {
            val isDark = ColorUtil.isDark(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground())
            return LOGO_RED_12x12
        }
        return null
    }


    // Compatability implementation. DO NOT ADD @Override.
    override fun getPresentation(): WidgetPresentation? {
        return this
    }

    override fun getTooltipText(): String? {
        if (!is_logged_in) {
            return "Click to login"
        }

        val c_stat = Connection.status
        if (c_stat == ConnectionStatus.DISCONNECTED) {
            return "Connection is lost"
        } else if (c_stat == ConnectionStatus.ERROR) {
            return Connection.last_error_msg
        } else if (c_stat == ConnectionStatus.CONNECTED) {
            var tooltip_str = "<html>"
            if (InferenceGlobalContext.inferenceUrl != null) {
                tooltip_str += "⚡ ${InferenceGlobalContext.inferenceUrl}${default_contrast_url_suffix}<br>"
            }
            val model = if (InferenceGlobalContext.model != null) InferenceGlobalContext.model else default_model
            tooltip_str += "\uD83D\uDDD2 ${model}"
            tooltip_str += "</html>"

            return tooltip_str
        }
        return "Codify"
    }

    override fun getClickConsumer(): Consumer<MouseEvent>? {
        return Consumer { e: MouseEvent ->
            if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
                if (!is_logged_in)
                    emit_login(project)
                else
                    emit_regular(project)
            }
        }
    }

    private fun update(newMsg: String? ) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    if (myProject == null || myProject.isDisposed || myStatusBar == null) {
                        return@invokeLater
                    }
                    component!!.icon = getIcon()
                    component!!.toolTipText = newMsg ?: tooltipText
                    myStatusBar.updateWidget(ID())
                    val statusBar = WindowManager.getInstance().getStatusBar(myProject)
                    statusBar?.component?.updateUI()
                },
                ModalityState.any()
            )
    }

}