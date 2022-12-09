package com.smallcloud.codify.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import com.smallcloud.codify.SMCPlugin
import com.smallcloud.codify.account.AccountManager
import com.smallcloud.codify.account.AccountManager.logout
import com.smallcloud.codify.account.AccountManagerChangedNotifier
import com.smallcloud.codify.account.login
import com.smallcloud.codify.struct.PlanType
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel


class AppRootComponent {
    private val loginButton = JButton("Login / Register")
    private val logoutButton = JButton("Logout")
    private val bugReportButton = JButton("Bug Report...")
    private val loggedLabel = JBLabel("")
    private val activePlanLabel = JBLabel("")
    private var myPanel: JPanel = recreate_panel()

    init {
        ApplicationManager.getApplication()
                .messageBus
                .connect(SMCPlugin.instant)
                .subscribe(AccountManagerChangedNotifier.TOPIC, object : AccountManagerChangedNotifier {
                    override fun isLoggedInChanged(unused: Boolean) {
                        revalidate()
                    }
                    override fun planStatusChanged(unused: PlanType) {
                        revalidate()
                    }
                })

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
        val is_logged_in = AccountManager.is_logged_in
        loggedLabel.text = "Logged as ${AccountManager.user}"
        loggedLabel.isVisible = is_logged_in
        activePlanLabel.text = "Active plan: ${AccountManager.active_plan}"
        activePlanLabel.isVisible = is_logged_in
        logoutButton.isVisible = is_logged_in
        bugReportButton.isVisible = is_logged_in
        loginButton.isVisible = !is_logged_in
    }

    val preferredFocusedComponent: JComponent
        get() = if (AccountManager.is_logged_in) bugReportButton else loginButton

    private fun recreate_panel(): JPanel {
        var builder = FormBuilder.createFormBuilder()
        val description = JBLabel("Codify: AI autocomplete, refactoring and advanced code generation")
        builder.addComponent(description)
        builder.addComponent(loggedLabel)
        builder.addComponent(activePlanLabel)
        builder.addComponent(logoutButton)
        builder.addComponent(bugReportButton)
        builder = builder.addComponent(loginButton)
        setup_properties()
        return builder.addComponentFillVertically(JPanel(), 0).panel
    }

    val panel: JPanel
        get() {
            return myPanel
        }
}