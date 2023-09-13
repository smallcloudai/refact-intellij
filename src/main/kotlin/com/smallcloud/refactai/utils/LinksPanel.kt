package com.smallcloud.refactai.utils

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ui.components.labels.LinkLabel
import org.jdesktop.swingx.HorizontalLayout
import javax.swing.JPanel

fun makeLinksPanel(): JPanel {
    return JPanel(HorizontalLayout()).apply {
        add(LinkLabel<String>("Bug report", AllIcons.Ide.External_link_arrow).apply {

            setListener({ _, _ ->
                BrowserUtil.browse("https://github.com/smallcloudai/refact-intellij/issues")
            }, null)
        })
        add(LinkLabel<String>("Discord", AllIcons.Ide.External_link_arrow).apply {
            setListener({ _, _ ->
                BrowserUtil.browse("https://www.smallcloud.ai/discord")
            }, null)
        })
    }
}