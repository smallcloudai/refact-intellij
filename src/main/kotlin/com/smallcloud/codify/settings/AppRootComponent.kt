package com.smallcloud.codify.settings

import com.intellij.openapi.application.invokeLater
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.io.login
import com.smallcloud.codify.io.logout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.Timer


private val blah_blah_text = "SMC blah blah blah blah blah blah blah blah blah"


class AppRootComponent {
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val bugReportButton = JButton("Bug Report...")
    private val loggedLabel = JBLabel("")
    private var myPanel: JPanel = recreate_panel()
    private var timer: Timer

    init {
        timer = Timer(500) {
            invokeLater { revalidate() }
        }
        timer.start()
        loginButton.addActionListener {
            login()
        }
        logoutButton.addActionListener {
            logout()
        }
    }

    private fun revalidate() {
        setup_properties()
        myPanel.revalidate()
    }

    private fun setup_properties() {
        val is_logged = AppSettingsState.instance.is_logined()
        loggedLabel.text = "Logged as ${AppSettingsState.instance.userLogged}"
        loggedLabel.isVisible = is_logged
        logoutButton.isVisible = is_logged
        bugReportButton.isVisible = is_logged
        loginButton.isVisible = !is_logged
    }

    val preferredFocusedComponent: JComponent
        get() = if (AppSettingsState.instance.is_logined()) bugReportButton else loginButton

    private fun recreate_panel() : JPanel {
        var builder = FormBuilder.createFormBuilder()
        val blah_area = JBLabel(blah_blah_text)
        blah_area.text = blah_blah_text
        builder.addComponent(blah_area)
        builder.addComponent(loggedLabel)
        builder.addComponent(logoutButton)
        builder.addComponent(bugReportButton)
        builder = builder.addComponent(loginButton)
        setup_properties()
        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    val panel: JPanel
        get() {return myPanel}
}