package com.smallcloud.refactai.status_bar

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Editor
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
import com.intellij.util.Consumer
import com.smallcloud.refactai.ExtraInfoChangedNotifier
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources.Icons.HAND_12x12
import com.smallcloud.refactai.Resources.Icons.LOGO_12x12
import com.smallcloud.refactai.Resources.Icons.LOGO_RED_12x12
import com.smallcloud.refactai.Resources.defaultContrastUrlSuffix
import com.smallcloud.refactai.Resources.defaultTemperature
import com.smallcloud.refactai.account.AccountManager.isLoggedIn
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.account.LoginStateService
import com.smallcloud.refactai.io.ConnectionChangedNotifier
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContext
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.notifications.emitLogin
import com.smallcloud.refactai.notifications.emitRegular
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyChangesNotifier
import com.smallcloud.refactai.privacy.PrivacyService
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
        component.text = "Refact.ai"
        component.toolTipText = getTooltipText()
        component.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    Objects.requireNonNull(getClickConsumer())?.consume(e)
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
            return LOGO_12x12
        }
        return when (InferenceGlobalContext.status) {
            ConnectionStatus.DISCONNECTED -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.ERROR -> AllIcons.Debugger.Db_exception_breakpoint
            ConnectionStatus.CONNECTED -> if (isPrivacyEnabled()) LOGO_RED_12x12 else HAND_12x12
            ConnectionStatus.PENDING -> AnimatedIcon.Default()
        }
    }

    private fun isPrivacyEnabled(): Boolean {
        return PrivacyService.instance.getPrivacy(getEditor()?.let { getVirtualFile(it) }) != Privacy.DISABLED
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getPresentation(): WidgetPresentation {
        return this
    }

    override fun getTooltipText(): String? {
        if (!isLoggedIn) {
            return RefactAIBundle.message("statusBar.clickToLogin")
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
                    return RefactAIBundle.message("statusBar.tooltipIfPrivacyDisabled")
                }
            }
            else -> return "Refact.ai"
        }
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { e: MouseEvent ->
            if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
                if (!isLoggedIn)
                    emitLogin(project)
                else
                    getEditor()?.let { emitRegular(project, it) }
            }
        }
    }

    private fun update(newMsg: String?) {
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    if (project.isDisposed || myStatusBar == null) {
                        return@invokeLater
                    }
                    component!!.icon = getIcon()
                    component!!.toolTipText = newMsg ?: getTooltipText()
                    myStatusBar!!.updateWidget(ID())
                    val statusBar = WindowManager.getInstance().getStatusBar(myProject)
                    statusBar?.component?.updateUI()
                },
                ModalityState.any()
            )
    }
}
