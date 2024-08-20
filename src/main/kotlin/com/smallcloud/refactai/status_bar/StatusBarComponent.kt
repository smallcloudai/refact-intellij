package com.smallcloud.refactai.status_bar

import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.impl.status.TextPanel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.Icon

class StatusBarComponent: TextPanel {
    companion object {
        private val GAP = JBUIScale.scale(3)
    }

    open var icon: Icon? = null
    var bottomLineColor: Color = UIUtil.getPanelBackground()

    constructor() : super(null)
    constructor(toolTipTextSupplier: (() -> String?)?) : super(toolTipTextSupplier)

    override fun paintComponent(g: Graphics) {
        val panelWidth = width
        val panelHeight = height
        g as Graphics2D
        val fontMetrics = g.getFontMetrics()
        setupAntialiasing(g)
        g.setColor(bottomLineColor)
        g.fillRect(0, panelHeight - GAP, panelWidth, GAP)
        super.paintComponent(g)
        val icon = if (icon == null || isEnabled) icon else IconLoader.getDisabledIcon(icon!!)
        icon?.paintIcon(this, g, getIconX(g), height / 2 - icon.iconHeight / 2 - 1)
    }

    override fun getPreferredSize(): Dimension {
        val preferredSize = super.getPreferredSize()
        return if (icon == null) {
            preferredSize
        } else {
            Dimension((preferredSize.width + icon!!.iconWidth + GAP).coerceAtLeast(height), preferredSize.height)
        }
    }

    override fun getTextX(g: Graphics): Int {
        val x = super.getTextX(g)
        return when {
            icon == null || alignment == RIGHT_ALIGNMENT -> x
            alignment == CENTER_ALIGNMENT -> x + (icon!!.iconWidth + GAP) / 2
            alignment == LEFT_ALIGNMENT -> x + icon!!.iconWidth + GAP
            else -> x
        }
    }

    private fun getIconX(g: Graphics): Int {
        val x = super.getTextX(g)
        return when {
            icon == null || text == null || alignment == LEFT_ALIGNMENT -> x
            alignment == CENTER_ALIGNMENT -> x - (icon!!.iconWidth + GAP) / 2
            alignment == RIGHT_ALIGNMENT -> x - icon!!.iconWidth - GAP
            else -> x
        }
    }



    fun hasIcon(): Boolean = icon != null
}