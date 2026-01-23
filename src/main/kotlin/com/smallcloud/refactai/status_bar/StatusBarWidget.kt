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
import com.smallcloud.refactai.RefactAIBundle
import com.smallcloud.refactai.Resources.Icons.LOGO_12x12
import com.smallcloud.refactai.Resources.Icons.LOGO_RED_16x16
import com.smallcloud.refactai.Resources.titleStr
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
import java.awt.Color
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.net.URI
import java.util.*
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Icon
import javax.swing.JComponent
import com.smallcloud.refactai.account.AccountManager.Companion.instance as AccountManager
import com.smallcloud.refactai.io.InferenceGlobalContext.Companion.instance as InferenceGlobalContext

data class StatusBarState(
    val astLimitHit: Boolean = false,
    val vecdbLimitHit: Boolean = false,
    val vecdbWarning: String = "",
    val lastRagStatus: RagStatus? = null
)

private fun escapeHtml(text: String): String {
    return text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

class SMCStatusBarWidget(project: Project) : EditorBasedWidget(project), CustomStatusBarWidget, WidgetPresentation {
    private var component: StatusBarComponent? = null
    private var logger: Logger = Logger.getInstance(javaClass)
    private val spinIcon = AnimatedIcon.Default.INSTANCE

    private fun getVirtualFile(editor: Editor): VirtualFile? {
        return FileDocumentManager.getInstance().getFile(editor.document)
    }

    private val statusbarStateRef: AtomicReference<StatusBarState> = AtomicReference(StatusBarState())
    private var lspSyncTask: Future<*>? = null

    override fun dispose() {
        lspSyncTask?.cancel(true)
    }

    private fun updateStatusBarStateAndUpdateStatusBarIfNeed(ragStatus: RagStatus) {
        try {
            val currentState = statusbarStateRef.get()
            val newState = when {
                ragStatus.ast != null && ragStatus.ast.astMaxFilesHit ->
                    currentState.copy(astLimitHit = true, lastRagStatus = ragStatus)
                ragStatus.vecdb != null && ragStatus.vecdb.vecdbMaxFilesHit ->
                    currentState.copy(vecdbLimitHit = true, lastRagStatus = ragStatus)
                else -> {
                    val warning = if (ragStatus.vecDbError.isNotEmpty()) ragStatus.vecDbError else currentState.vecdbWarning
                    currentState.copy(
                        astLimitHit = false,
                        vecdbLimitHit = false,
                        vecdbWarning = warning,
                        lastRagStatus = ragStatus
                    )
                }
            }
            statusbarStateRef.set(newState)
            update(null)
        } catch (e: Exception) {
            logger.warn("Failed to update statusbar state", e)
        }
    }

    init {
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

        project
            .messageBus
            .connect(this)
            .subscribe(LSPProcessHolderChangedNotifier.TOPIC, object : LSPProcessHolderChangedNotifier {
                override fun lspIsActive(isActive: Boolean) {
                    update(null)
                }

                override fun ragStatusChanged(ragStatus: RagStatus) {
                    updateStatusBarStateAndUpdateStatusBarIfNeed(ragStatus)
                }
            })
        lspSyncTask = AppExecutorUtil.getAppExecutorService().submit {
            try {
                val lsp = LSPProcessHolder.getInstance(project) ?: return@submit
                var attempts = 0
                while (!Thread.currentThread().isInterrupted && !project.isDisposed && attempts < 100) {
                    val ragStatus = lsp.ragStatusCache
                    if (ragStatus != null) {
                        updateStatusBarStateAndUpdateStatusBarIfNeed(ragStatus)
                        break
                    }
                    attempts++
                    Thread.sleep(150)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (e: Exception) {
                logger.error("Failed to update status bar", e)
            }
        }
    }

    override fun ID(): String {
        return javaClass.name
    }

    // Compatability implementation. DO NOT ADD @Override.
    override fun getComponent(): JComponent {
        component?.let { return it }
        val newComponent = StatusBarComponent()
        newComponent.icon = getIcon()
        newComponent.text = getText()
        newComponent.toolTipText = titleStr
        newComponent.bottomLineColor = getBackgroundColor()
        newComponent.addMouseListener(
            object : MouseAdapter() {
                override fun mousePressed(e: MouseEvent) {
                    Objects.requireNonNull(getClickConsumer())?.consume(e)
                }
            })
        this.component = newComponent
        return newComponent
    }

    private fun getIcon(): Icon {
        if (!AccountManager.isLoggedIn && InferenceGlobalContext.isCloud) {
            return LOGO_12x12
        }

        val state = statusbarStateRef.get()
        if (state.astLimitHit || state.vecdbLimitHit) {
            return AllIcons.Debugger.ThreadStates.Socket
        }

        val lastRagStatus = state.lastRagStatus
        if (lastRagStatus != null) {
            if ((lastRagStatus.vecdb != null && !listOf("done", "idle").contains(lastRagStatus.vecdb.state))
                || lastRagStatus.ast != null && !listOf("done", "idle").contains(lastRagStatus.ast.state)
            ) {
                return spinIcon
            }
        }

        return when (InferenceGlobalContext.status) {
            ConnectionStatus.DISCONNECTED -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.ERROR -> AllIcons.Debugger.ThreadStates.Socket
            ConnectionStatus.CONNECTED -> LOGO_RED_16x16
            ConnectionStatus.PENDING -> spinIcon
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

        val state = statusbarStateRef.get()

        if (state.vecdbWarning.isNotEmpty()) {
            return escapeHtml(state.vecdbWarning)
        }

        if (state.astLimitHit || state.vecdbLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipClickToMakeChanges")
        }

        val lastErrorMsg = InferenceGlobalContext.lastErrorMsg
        if (InferenceGlobalContext.status == ConnectionStatus.ERROR && lastErrorMsg != null) {
            val safeMsg = escapeHtml(lastErrorMsg)
            return if (lastErrorMsg.indexOf("no model") != -1) {
                RefactAIBundle.message("statusBar.noModelWarning", safeMsg)
            } else {
                RefactAIBundle.message("statusBar.errorWarning", safeMsg)
            }
        }
        val lsp = LSPProcessHolder.getInstance(project) ?: return titleStr
        var msg = RefactAIBundle.message("statusBar.communicatingWith", escapeHtml(lsp.attempingToReach()))
        val lastAutoModel = InferenceGlobalContext.lastAutoModel
        if (lastAutoModel != null) {
            msg += "<br><br>${RefactAIBundle.message("statusBar.lastUsedModel", escapeHtml(lastAutoModel))}"
        }

        val lastRagStatus = state.lastRagStatus
        var ragStatusMsg = ""
        if (lastRagStatus != null) {
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
                val state = statusbarStateRef.get()
                if (state.astLimitHit || state.vecdbLimitHit) {
                    emitWarning(project, RefactAIBundle.message("statusBar.notificationAstVecdbLimitMsg"))
                    return@Consumer
                }
                if (AccountManager.isLoggedIn || !InferenceGlobalContext.isCloud)
                    getEditor()?.let { emitRegular(project, it) }
            }
        }
    }

    fun getBackgroundColor(): Color {
        val state = statusbarStateRef.get()
        if (state.vecdbWarning.isNotEmpty()
            || state.astLimitHit
            || state.vecdbLimitHit
            || InferenceGlobalContext.status == ConnectionStatus.ERROR
        ) {
            return JBColor.YELLOW
        }

        return JBColor.background()
    }

    private fun getText(): String {
        val state = statusbarStateRef.get()

        if (state.astLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipIfAstLimitHit")
        }
        if (state.vecdbLimitHit) {
            return RefactAIBundle.message("statusBar.tooltipIfVecdbLimitHit")
        }

        val lastRagStatus = state.lastRagStatus
        if (lastRagStatus != null) {
            if (lastRagStatus.vecdb != null && !listOf("done", "idle", "cooldown").contains(lastRagStatus.vecdb.state)) {
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
        AppExecutorUtil.getAppExecutorService().submit {
            try {
                val icon = getIcon()
                val tooltip = getTooltipText()
                val text = getText()
                val bgColor = getBackgroundColor()
                ApplicationManager.getApplication()
                    .invokeLater(
                        {
                            if (project.isDisposed || myStatusBar == null) {
                                return@invokeLater
                            }
                            val comp = component ?: return@invokeLater
                            comp.icon = icon
                            comp.text = text
                            comp.toolTipText = newMsg ?: tooltip
                            comp.bottomLineColor = bgColor
                            myStatusBar?.updateWidget(ID())
                        },
                        ModalityState.any()
                    )
            } catch (e: Exception) {
                logger.error("Failed to update status bar", e)
            }
        }
    }
}
