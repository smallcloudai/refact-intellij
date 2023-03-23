package com.smallcloud.codify.status_bar

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.openapi.wm.impl.status.TextPanel.WithIconAndArrows
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.ColorUtil
import com.intellij.util.Consumer
import com.smallcloud.codify.CodifyBundle
import com.smallcloud.codify.ExtraInfoChangedNotifier
import com.smallcloud.codify.PluginState
import com.smallcloud.codify.Resources
import com.smallcloud.codify.Resources.Icons.LOGO_DARK_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_LIGHT_12x12
import com.smallcloud.codify.Resources.Icons.LOGO_RED_12x12
import com.smallcloud.codify.Resources.defaultContrastUrlSuffix
import com.smallcloud.codify.Resources.defaultTemperature
import com.smallcloud.codify.account.AccountManager.isLoggedIn
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.account.LoginStateService
import com.smallcloud.codify.io.ConnectionChangedNotifier
import com.smallcloud.codify.io.ConnectionStatus
import com.smallcloud.codify.io.InferenceGlobalContext
import com.smallcloud.codify.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.codify.notifications.emitLogin
import com.smallcloud.codify.notifications.emitRegular
import com.smallcloud.codify.privacy.Privacy
import com.smallcloud.codify.privacy.PrivacyChangesNotifier
import com.smallcloud.codify.privacy.PrivacyService
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.util.*
import javax.swing.Icon
import javax.swing.JComponent

class SMCStatusBarWidget(project: Project) : EditorBasedWidget(project), CustomStatusBarWidget, WidgetPresentation {
    private var component: WithIconAndArrows? = null

    private fun getVirtualFile(editor: Editor) : VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    init {
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(PrivacyChangesNotifier.TOPIC, object : PrivacyChangesNotifier {
                override fun privacyChanged() {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                override fun isLoggedInChanged(limited: Boolean) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun inferenceUriChanged(unused: URI?) {
                    update(null)
                }

                override fun modelChanged(newModel: String?) {
                    update(null)
                }

                override fun lastAutoModelChanged(newModel: String?) {
                    update(null)
                }

                override fun temperatureChanged(newTemp: Float?) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(ExtraInfoChangedNotifier.TOPIC, object : ExtraInfoChangedNotifier {
                override fun tooltipMessageChanged(newMsg: String?) {
                    update(null)
                }

                override fun inferenceMessageChanged(newMsg: String?) {
                    update(newMsg)
                }

                override fun pluginEnableChanged(newVal: Boolean) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(ConnectionChangedNotifier.TOPIC, object : ConnectionChangedNotifier {
                override fun statusChanged(newStatus: ConnectionStatus) {
                    update(null)
                }

                override fun lastErrorMsgChanged(newMsg: String?) {
                    update(newMsg)
                }
            })
    }

    override fun selectionChanged(event: FileEditorManagerEvent) {
        update(null)
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

    private fun getLastStatus(): String {
        val websiteStat =
            ApplicationManager.getApplication().getService(LoginStateService::class.java).getLastWebsiteLoginStatus()
        val infStat =
            ApplicationManager.getApplication().getService(LoginStateService::class.java).getLastInferenceLoginStatus()
        if (infStat == "OK" && websiteStat == "OK") return "OK"
        return ""
    }

    private fun getIcon(): Icon {
        val isOkStat = getLastStatus()
        if (!PluginState.instance.isEnabled)
            return AllIcons.Diff.GutterCheckBoxIndeterminate
        if (!isLoggedIn) {
            val isDark = ColorUtil.isDark(EditorColorsManager.getInstance().globalScheme.defaultBackground)
            return if (isDark) {
                LOGO_LIGHT_12x12
            } else LOGO_DARK_12x12
        }
        return when (InferenceGlobalContext.status) {
            ConnectionStatus.DISCONNECTED -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.ERROR -> AllIcons.Debugger.Db_exception_breakpoint
            ConnectionStatus.CONNECTED -> if (isPrivacyEnabled()) LOGO_RED_12x12 else AllIcons.Diff.Lock
            ConnectionStatus.PENDING -> AnimatedIcon.Default()
        }
    }

    private fun isPrivacyEnabled(): Boolean {
        return PrivacyService.instance.getPrivacy(editor?.let { getVirtualFile(it) }) != Privacy.DISABLED
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getPresentation(): WidgetPresentation {
        return this
    }

    override fun getTooltipText(): String? {
        if (!isLoggedIn) {
            return CodifyBundle.message("statusBar.clickToLogin")
        }

        when (InferenceGlobalContext.status) {
            ConnectionStatus.DISCONNECTED -> {
                return InferenceGlobalContext.lastErrorMsg
            }
            ConnectionStatus.ERROR -> {
                return InferenceGlobalContext.lastErrorMsg
            }
            ConnectionStatus.CONNECTED -> {
                if (isPrivacyEnabled()) {
                    var tooltipStr = "<html>"
                    if (InferenceGlobalContext.inferenceUri != null) {
                        tooltipStr += "⚡ ${InferenceGlobalContext.inferenceUri!!.resolve(defaultContrastUrlSuffix)}"
                    }
                    val model = if (InferenceGlobalContext.model != null) InferenceGlobalContext.model else
                        if (InferenceGlobalContext.lastAutoModel != null) InferenceGlobalContext.lastAutoModel else null
                    val temp =
                            if (InferenceGlobalContext.temperature != null) InferenceGlobalContext.temperature else defaultTemperature
                    if (model != null)
                        tooltipStr += "<br>\uD83D\uDDD2 $model"
                    tooltipStr += "<br>\uD83C\uDF21️ $temp"
                    if (PluginState.instance.tooltipMessage != null)
                        tooltipStr += "<br>${PluginState.instance.tooltipMessage}"
                    tooltipStr += "</html>"

                    return tooltipStr
                } else {
                    return CodifyBundle.message("statusBar.tooltipIfPrivacyDisabled")
                }
            }
            else -> return Resources.codifyStr
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { e: MouseEvent ->
            if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
                if (!isLoggedIn)
                    emitLogin(project)
                else
                    editor?.let { emitRegular(project, it) }
            }
        }
    }

    private fun update(newMsg: String?) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    if (myProject.isDisposed || myStatusBar == null) {
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
