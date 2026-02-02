package com.smallcloud.refactai.panes.sharedchat

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.struct.ChatMessage
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import javax.swing.JPanel

class ChatPanes(val project: Project) : Disposable {
    private val logger = Logger.getInstance(ChatPanes::class.java)
    private var component: JComponent? = null
    private var pane: SharedChatPane? = null
    private val holder = JPanel().also {
        it.layout = BorderLayout()
    }
    private val isRecreating = AtomicBoolean(false)

    private fun setupPanes() {
        invokeLater {
            holder.removeAll()
            try {
                pane = SharedChatPane(project)
                component = pane?.webView?.component
                holder.add(component)

                // Set up mode switch callback for dynamic crash recovery
                pane?.chatWebView?.setModeSwitchCallback {
                    recreateWithOsrMode()
                }

                holder.revalidate()
                holder.repaint()
                logger.info("Chat pane setup completed successfully")
            } catch (e: Exception) {
                logger.error("Failed to setup chat pane", e)
                showErrorPanel(e.message ?: "Unknown error")
            }
        }
    }

    private fun recreateWithOsrMode() {
        if (!isRecreating.compareAndSet(false, true)) {
            logger.info("Already recreating pane, skipping")
            return
        }

        logger.warn("Recreating chat pane with OSR mode due to rendering issues")

        invokeLater {
            try {
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Refact AI Notification Group")
                    ?.createNotification(
                        "Refact Panel Recovery",
                        "Detected rendering issues. Switching to software rendering mode for stability.",
                        NotificationType.WARNING
                    )
                    ?.notify(project)

                pane?.dispose()
                pane = null
                component = null
                holder.removeAll()

                pane = SharedChatPane(project)
                component = pane?.webView?.component
                holder.add(component)

                pane?.chatWebView?.setModeSwitchCallback {
                    logger.warn("Mode switch requested but already switched, attempting reload")
                    pane?.let { p ->
                        if (!p.webView.isDisposed) {
                            p.webView.cefBrowser.reload()
                        }
                    }
                }

                holder.revalidate()
                holder.repaint()

                logger.info("Chat pane recreated successfully with OSR mode")
            } catch (e: Exception) {
                logger.error("Failed to recreate chat pane", e)
                showErrorPanel("Recovery failed: ${e.message}")
            } finally {
                isRecreating.set(false)
            }
        }
    }

    private fun showErrorPanel(message: String) {
        holder.removeAll()
        val errorText = buildString {
            appendLine("⚠️ Refact Panel Error")
            appendLine()
            appendLine(message)
            appendLine()
            appendLine("Try restarting the IDE or adding VM options:")
            appendLine("-Drefact.jcef.linux-osr=true")
        }
        val textArea = javax.swing.JTextArea(errorText).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = javax.swing.BorderFactory.createEmptyBorder(20, 20, 20, 20)
        }
        holder.add(textArea, BorderLayout.CENTER)
        holder.revalidate()
        holder.repaint()
    }

    init {
        setupPanes()
    }

    fun getComponent(): JComponent {
        return holder
    }

    fun executeCodeLensCommand(messages: Array<ChatMessage>, sendImmediately: Boolean, openNewTab: Boolean) {
        pane?.executeCodeLensCommand(messages, sendImmediately, openNewTab)
    }

    fun requestFocus() {
        component?.requestFocus()
    }

    fun newChat() {
        pane?.newChat()
    }

    fun switchToThread(chatId: String) {
        pane?.switchToThread(chatId)
    }

    override fun dispose() {
        pane?.dispose()
    }
}