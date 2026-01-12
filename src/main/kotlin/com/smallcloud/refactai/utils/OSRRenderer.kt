package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent

/**
 * OSR (Off-Screen Rendering) utilities for Linux systems.
 * Since JBCef handles OSR internally when setOffScreenRendering(true) is called,
 * this class provides additional optimizations and monitoring.
 */
class OSRRenderer(
    private val targetFps: Int = 30
) {
    private val logger = Logger.getInstance(OSRRenderer::class.java)
    private val frameInterval = 1000L / targetFps
    private var lastOptimizationTime = 0L
    private var hostComponent: JComponent? = null
    private var componentListener: ComponentAdapter? = null

    fun attach(host: JComponent) {
        this.hostComponent = host
        logger.info("OSR optimizations attached (target ${targetFps}fps)")

        componentListener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) {
                optimizeForResize()
            }
        }
        host.addComponentListener(componentListener)
    }

    private fun optimizeForResize() {
        val host = hostComponent ?: return
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOptimizationTime < frameInterval) {
            return
        }
        lastOptimizationTime = currentTime
        logger.debug("OSR resize optimization applied: ${host.width}x${host.height}")
        host.repaint()
    }

    fun cleanup() {
        logger.info("Cleaning up OSR renderer optimizations")

        componentListener?.let { listener ->
            hostComponent?.removeComponentListener(listener)
        }
        componentListener = null
        hostComponent = null
        lastOptimizationTime = 0L

        logger.info("OSR renderer cleanup completed")
    }
}
