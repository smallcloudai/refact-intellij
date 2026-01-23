package com.smallcloud.refactai.panes

import com.intellij.openapi.diagnostic.Logger
import com.intellij.ui.jcef.JBCefApp
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.smallcloud.refactai.Resources
import com.smallcloud.refactai.panes.sharedchat.ChatPanes
import com.smallcloud.refactai.panes.sharedchat.browser.ChatWebView
import com.smallcloud.refactai.utils.getLastUsedProject
import java.awt.BorderLayout
import java.awt.Desktop
import java.net.URI
import javax.swing.*


class RefactAIToolboxPaneFactory : ToolWindowFactory {
    private val logger = Logger.getInstance(RefactAIToolboxPaneFactory::class.java)

    override fun init(toolWindow: ToolWindow) {
        toolWindow.setIcon(Resources.Icons.LOGO_RED_13x13)
        super.init(toolWindow)
    }

    override suspend fun isApplicableAsync(project: Project): Boolean = JBCefApp.isSupported()

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()

        val reloadAction: () -> Unit = {
            toolWindow.contentManager.removeAllContents(true)
            createToolWindowContent(project, toolWindow)
        }

        try {
            val chatPanes = ChatPanes(project)
            val content: Content = contentFactory.createContent(chatPanes.getComponent(), null, true)
            Disposer.register(content, chatPanes)
            content.isCloseable = false
            content.putUserData(panesKey, chatPanes)
            toolWindow.contentManager.addContent(content)
            logger.info("Refact tool window created successfully")
        } catch (e: Exception) {
            logger.error("Failed to create Refact tool window", e)
            val errorPanel = createErrorPanel(e, reloadAction)
            val content: Content = contentFactory.createContent(errorPanel, null, true)
            content.isCloseable = false
            toolWindow.contentManager.addContent(content)
        }
    }

    private fun createErrorPanel(e: Exception, reloadAction: () -> Unit): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.empty(20)

        val errorMessage = ChatWebView.getLastInitError()
            ?: e.message
            ?: "Unknown error initializing the browser component"

        val renderingMode = ChatWebView.getRenderingModePreference()
        val modeInfo = when (renderingMode) {
            "osr" -> "Currently using: Software rendering (OSR) - switched due to crashes"
            "native" -> "Currently using: Native rendering (explicit preference)"
            else -> "Currently using: Auto mode"
        }

        val textArea = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            background = panel.background
            font = JBUI.Fonts.label()
            text = buildString {
                appendLine("⚠️ Refact Panel Failed to Load")
                appendLine()
                appendLine("Error: $errorMessage")
                appendLine()
                appendLine(modeInfo)
                appendLine()
                appendLine("Possible solutions:")
                appendLine()
                appendLine("1. Click 'Retry' to attempt loading again")
                appendLine()
                appendLine("2. Add VM options (Help → Edit Custom VM Options):")
                appendLine("   -Djcef.disable-gpu=true")
                appendLine("   -Dsun.java2d.opengl=false")
                appendLine()
                appendLine("3. Force a specific rendering mode:")
                appendLine("   -Drefact.jcef.force-osr=true  (software rendering)")
                appendLine("   -Drefact.jcef.force-native=true  (hardware rendering)")
                appendLine()
                appendLine("4. Restart the IDE after making changes")
                appendLine()
                appendLine("5. If the issue persists, please report it with your")
                appendLine("   idea.log file (Help → Collect Logs and Diagnostic Data)")
            }
        }

        val scrollPane = JScrollPane(textArea).apply {
            border = null
        }

        val buttonPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            add(Box.createHorizontalGlue())

            val reportButton = JButton("Report Issue").apply {
                addActionListener {
                    try {
                        Desktop.getDesktop().browse(
                            URI("https://github.com/smallcloudai/refact-intellij/issues/new")
                        )
                    } catch (ex: Exception) {
                        logger.warn("Failed to open browser", ex)
                    }
                }
            }
            add(reportButton)
            add(Box.createHorizontalStrut(10))

            val resetButton = JButton("Reset Preferences").apply {
                toolTipText = "Clear crash history and try native rendering again"
                addActionListener {
                    ChatWebView.resetRenderingPreferences()
                    ChatWebView.clearLastInitError()
                    reloadAction()
                }
            }
            add(resetButton)
            add(Box.createHorizontalStrut(10))

            val retryButton = JButton("Retry").apply {
                addActionListener {
                    ChatWebView.clearLastInitError()
                    reloadAction()
                }
            }
            add(retryButton)
        }

        panel.add(scrollPane, BorderLayout.CENTER)
        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    companion object {
        private val panesKey = Key.create<ChatPanes>("refact.panes")
        val chat: ChatPanes?
            get() {
                val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
                return tw?.contentManager?.getContent(0)?.getUserData(panesKey)
            }

        fun focusChat() {
            val tw = ToolWindowManager.getInstance(getLastUsedProject()).getToolWindow("Refact")
            val content = tw?.contentManager?.getContent(0) ?: return
            tw.contentManager.setSelectedContent(content, true)
            val panes = content.getUserData(panesKey)
            panes?.requestFocus()
            chat?.newChat()
        }
    }
}
