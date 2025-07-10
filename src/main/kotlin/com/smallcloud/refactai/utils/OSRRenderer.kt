package com.smallcloud.refactai.utils

import com.intellij.openapi.diagnostic.Logger
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
    private lateinit var hostComponent: JComponent

    fun attach(host: JComponent) {
        this.hostComponent = host
        logger.info("OSR optimizations attached (target ${targetFps}fps)")
        host.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                optimizeForResize()
            }
        })
    }

    private fun optimizeForResize() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastOptimizationTime < frameInterval) {
            return
        }
        lastOptimizationTime = currentTime
        logger.info("OSR resize optimization applied: ${hostComponent.width}x${hostComponent.height}")
        hostComponent.repaint()
    }

    fun cleanup() {
        logger.info("Cleaning up OSR renderer optimizations")
        lastOptimizationTime = 0L
        logger.info("OSR renderer cleanup completed")
    }
}
