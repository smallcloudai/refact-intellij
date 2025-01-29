package com.smallcloud.refactai.status_bar

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBarWidget.WidgetPresentation
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedWidget
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.JBColor
import com.intellij.util.Consumer
import com.intellij.util.concurrency.AppExecutorUtil
import com.smallcloud.refactai.ExtraInfoChangedNotifier
import com.smallcloud.refactai.PluginState
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources.Icons.HAND_12x12
import com.smallcloud.refactai.Resources.Icons.LOGO_12x12
import com.smallcloud.refactai.Resources.Icons.LOGO_RED_16x16
import com.smallcloud.refactai.account.AccountManagerChangedNotifier
import com.smallcloud.refactai.io.ConnectionChangedNotifier
import com.smallcloud.refactai.io.ConnectionStatus
import com.smallcloud.refactai.io.InferenceGlobalContextChangedNotifier
import com.smallcloud.refactai.listeners.SelectionChangedNotifier
import com.smallcloud.refactai.lsp.LSPProcessHolder
import com.smallcloud.refactai.lsp.LSPProcessHolderChangedNotifier
import com.smallcloud.refactai.lsp.RagStatus
import com.smallcloud.refactai.notifications.emitRegular
import com.smallcloud.refactai.notifications.emitWarning
import com.smallcloud.refactai.privacy.Privacy
import com.smallcloud.refactai.privacy.PrivacyChangesNotifier
import com.smallcloud.refactai.privacy.PrivacyService
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import javax.swing.JComponent
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

class StatusBarState {
    var astLimitHit: Boolean = false
    var vecdbLimitHit: Boolean = false
    var vecdbWarning: String = ""
    var lastRagStatus: RagStatus? = null
}

class SMCStatusBarWidget(project: Project) : EditorBasedWidget(project), CustomStatusBarWidget, WidgetPresentation {
    private var component: StatusBarComponent? = null
    private var logger: Logger = Logger.getInstance(javaClass)
    private val spinIcon = AnimatedIcon.Default.INSTANCE

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    private var statusbarState: StatusBarState = StatusBarState()
    private var lspSyncTask: Future<*>? =null

    override fun dispose() {
        lspSyncTask?.cancel(true)
    }

    private fun updateStatusBarStateAndUpdateStatusBarIfNeed(ragStatus: RagStatus) {
        try {
            statusbarState.lastRagStatus = ragStatus

            if (ragStatus.ast != null && ragStatus.ast.astMaxFilesHit) {
                statusbarState.astLimitHit = true
                update(null)
                return
            }
            if (ragStatus.vecdb != null && ragStatus.vecdb.vecdbMaxFilesHit) {
                statusbarState.vecdbLimitHit = true
                update(null)
                return
            }

            statusbarState.astLimitHit = false
            statusbarState.vecdbLimitHit = false

            if (ragStatus.vecDbError.isNotEmpty()) {
                statusbarState.vecdbWarning = ragStatus.vecDbError
            }

            if ((ragStatus.ast != null && listOf("starting", "parsing", "indexing").contains(ragStatus.ast.state))
                || (ragStatus.vecdb != null && listOf("starting", "parsing").contains(ragStatus.vecdb.state))
            ) {
                logger.info("ast or vecdb is still indexing")
            } else {
                logger.info("ast and vecdb status complete, slowdown poll")
            }

            update(null)
        } catch (_: Exception) {}
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
                override fun isLoggedInChanged(isLoggedIn: Boolean) {
                    update(null)
                }
            })
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(InferenceGlobalContextChangedNotifier.TOPIC, object : InferenceGlobalContextChangedNotifier {
                override fun inferenceUriChanged(newUrl: URI?) {
                    update(null)
                }

                override fun userInferenceUriChanged(newUrl: String?) {
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
        ApplicationManager.getApplication()
            .messageBus
            .connect(this)
            .subscribe(SelectionChangedNotifier.TOPIC, object : SelectionChangedNotifier {
                override fun isEditorChanged(editor: Editor?) {
                    update(null)
                }

            })
        project.messageBus.connect(PluginState.instance)
            .subscribe(LSPProcessHolderChangedNotifier.TOPIC, object : LSPProcessHolderChangedNotifier {
                override fun lspIsActive(isActive: Boolean) {
                    update(null)
                }

                override fun ragStatusChanged(ragStatus: RagStatus) {
                    updateStatusBarStateAndUpdateStatusBarIfNeed(ragStatus)
                }
            })
    }

    override fun ID(): String {
        return javaClass.name
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getComponent(): JComponent {
        val component = StatusBarComponent()
        component.icon = getIcon()
        component.text = getText()
        component.toolTipText = getTooltipText()
        component.bottomLineColor = getBackgroundColor()
        component.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    Objects.requireNonNull(getClickConsumer())?.consume(e)
                }
            })
        this.component = component
        return component
    }

    private fun getIcon(): Icon {
        if (!AccountManager.isLoggedIn && InferenceGlobalContext.isCloud) {
            return LOGO_12x12
        }

        var lastRagStatusMb: RagStatus?
        synchronized(this) {
            lastRagStatusMb = statusbarState.lastRagStatus?.copy()
        }
        if (statusbarState.astLimitHit || statusbarState.vecdbLimitHit) {
            return AllIcons.Debugger.ThreadStates.Socket
        }

        if (lastRagStatusMb != null) {
            val lastRagStatus = lastRagStatusMb!!
            if ((lastRagStatus.vecdb != null && !listOf("done", "idle").contains(lastRagStatus.vecdb.state))
                || lastRagStatus.ast != null && !listOf("done", "idle").contains(lastRagStatus.ast.state)
            ) {
                return spinIcon
            }
        }

        return when (InferenceGlobalContext.status) {
            ConnectionStatus.DISCONNECTED -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.ERROR -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.CONNECTED -> if (isPrivacyDisabled()) HAND_12x12 else LOGO_RED_16x16
            ConnectionStatus.PENDING -> spinIcon
        }
    }

    private fun isPrivacyDisabled(): Boolean {
        val editor = getEditor()
        return if (editor == null) {
            false
        } else {
            PrivacyService.instance.getPrivacy(getVirtualFile(editor)) == Privacy.DISABLED
        }
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getPresentation(): WidgetPresentation {
        return this
    }

    override fun getTooltipText(): String? {
        if (!AccountManager.isLoggedIn && InferenceGlobalContext.isCloud) {
            return null
        }

        if (isPrivacyDisabled()) {
            return RefactAIBundle.message("statusBar.tooltipIfPrivacyDisabled")
        }

        if (statusbarState.vecdbWarning.isNotEmpty()) {
            return statusbarState.vecdbWarning
        }

        if (statusbarState.astLimitHit || statusbarState.vecdbLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipClickToMakeChanges")
        }

        if (InferenceGlobalContext.status == ConnectionStatus.ERROR && InferenceGlobalContext.lastErrorMsg != null) {
            return if (InferenceGlobalContext.lastErrorMsg!!.indexOf("no model") != -1) {
                RefactAIBundle.message("statusBar.noModelWarning", InferenceGlobalContext.lastErrorMsg!!)
            } else {
                RefactAIBundle.message("statusBar.errorWarning", InferenceGlobalContext.lastErrorMsg!!)
            }
        }
        val lsp = LSPProcessHolder.getInstance(project)!!
        var msg = RefactAIBundle.message("statusBar.communicatingWith", lsp.attempingToReach())
        if (InferenceGlobalContext.lastAutoModel != null) {
            msg += "<br><br>${RefactAIBundle.message("statusBar.lastUsedModel", InferenceGlobalContext.lastAutoModel!!)}"
        }

        var lastRagStatusMb: RagStatus?
        synchronized(this) {
            lastRagStatusMb = statusbarState.lastRagStatus?.copy()
        }
        var ragStatusMsg = ""
        if (lastRagStatusMb != null) {
            val lastRagStatus = lastRagStatusMb!!
            ragStatusMsg += if (lastRagStatus.ast != null) {
                "${RefactAIBundle.message("statusBar.astStatus", lastRagStatus.ast.astIndexFilesTotal, 
                    lastRagStatus.ast.astIndexSymbolsTotal)}<br><br>"

            } else {
                "${RefactAIBundle.message("statusBar.astTurnedOff")}<br><br>"
            }
            ragStatusMsg += if (lastRagStatus.vecdb != null) {
                RefactAIBundle.message("statusBar.vecDBStatus",
                    lastRagStatus.vecdb.dbSize, lastRagStatus.vecdb.dbCacheSize,
                    lastRagStatus.vecdb.requestsMadeSinceStart, lastRagStatus.vecdb.requestsMadeSinceStart)
            } else {
                RefactAIBundle.message("statusBar.vecDBTurnedOff")
            }
            ragStatusMsg = ragStatusMsg.trim()
        }
        if (ragStatusMsg.isNotEmpty()) {
            msg += "<br><br>$ragStatusMsg"
        }

        return msg
    }

    override fun getClickConsumer(): Consumer<MouseEvent> {
        return Consumer { e: MouseEvent ->
            if (!e.isPopupTrigger && MouseEvent.BUTTON1 == e.button) {
                if (statusbarState.astLimitHit || statusbarState.vecdbLimitHit) {
                    emitWarning(project, RefactAIBundle.message("statusBar.notificationAstVecdbLimitMsg"))
                    return@Consumer
                }
                if (AccountManager.isLoggedIn || !InferenceGlobalContext.isCloud)
                    getEditor()?.let { emitRegular(project, it) }
            }
        }
    }

    fun getBackgroundColor(): Color {
        if (statusbarState.vecdbWarning.isNotEmpty()
            || statusbarState.astLimitHit
            || statusbarState.vecdbLimitHit
            || InferenceGlobalContext.status == ConnectionStatus.ERROR
        ) {
            return JBColor.YELLOW
        }

        return JBColor.background()
    }

    private fun getText(): String {
        var lastRagStatusMb: RagStatus?
        synchronized(this) {
            lastRagStatusMb = statusbarState.lastRagStatus?.copy()
        }

        if (statusbarState.astLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipIfAstLimitHit")
        }
        if (statusbarState.vecdbLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipIfVecdbLimitHit")
        }

        if (lastRagStatusMb != null) {
            val lastRagStatus = lastRagStatusMb!!
            if (lastRagStatus.vecdb != null && !listOf("done", "idle").contains(lastRagStatus.vecdb.state)) {
                val vecdbParsedQty = lastRagStatus.vecdb.filesTotal - lastRagStatus.vecdb.filesUnprocessed
                return RefactAIBundle.message("statusBar.vecDBProgress", vecdbParsedQty, lastRagStatus.vecdb.filesTotal)
            }
            if (lastRagStatus.ast != null && !listOf("done", "idle").contains(lastRagStatus.ast.state)) {
                when (lastRagStatus.ast.state) {
                    "parsing" -> {
                        val astParsedQty = lastRagStatus.ast.filesTotal - lastRagStatus.ast.filesUnparsed
                        return RefactAIBundle.message("statusBar.astProgress", astParsedQty, lastRagStatus.ast.filesTotal)
                    }
                    "indexing" -> {
                        return RefactAIBundle.message("statusBar.astIndexing")
                    }
                    "starting" -> {
                        return RefactAIBundle.message("statusBar.astStarting")
                    }
                }
            }
        }
        return "Refact.ai"
    }

    private fun update(newMsg: String?) {
        val icon = if (ApplicationManager.getApplication().isDispatchThread) {
            ApplicationManager.getApplication().runWriteAction<Icon> {
                return@runWriteAction getIcon()
            }
        } else {
            getIcon()
        }
        ApplicationManager.getApplication()
            .invokeLater(
                {
                    if (project.isDisposed || myStatusBar == null) {
                        return@invokeLater
                    }
                    if (component!!.icon != icon) {
                        component!!.icon = icon
                    }
                    component!!.icon = icon
                    component!!.text = getText()
                    component!!.toolTipText = newMsg ?: getTooltipText()
                    component!!.bottomLineColor = getBackgroundColor()
                    myStatusBar!!.updateWidget(ID())
                    val statusBar = WindowManager.getInstance().getStatusBar(project)
                    statusBar?.component?.updateUI()
                },
                ModalityState.any()
            )
    }
}
