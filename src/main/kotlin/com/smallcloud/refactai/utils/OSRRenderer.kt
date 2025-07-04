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
    
    /**
     * Attaches OSR optimizations to the host component.
     * Note: JBCef handles the actual OSR internally, this just adds optimizations.
     * @param host The host component that displays the rendered content
     */
    fun attach(host: JComponent) {
        this.hostComponent = host
        logger.info("OSR optimizations attached (target ${targetFps}fps)")
        
        // Add component listener for resize optimization
        host.addComponentListener(object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent) {
                optimizeForResize()
            }
        })
    }
    
    /**
     * Optimizes rendering performance when component is resized.
     */
    private fun optimizeForResize() {
        val currentTime = System.currentTimeMillis()
        
        // Throttle resize optimizations
        if (currentTime - lastOptimizationTime < frameInterval) {
            return
        }
        
        lastOptimizationTime = currentTime
        logger.info("OSR resize optimization applied: ${hostComponent.width}x${hostComponent.height}")
        
        // Force a repaint to ensure the new size is rendered
        hostComponent.repaint()
    }
    
    /**
     * Gets the current target FPS.
     */
    fun getTargetFps(): Int = targetFps
    
    /**
     * Gets the actual frame interval in milliseconds.
     */
    fun getFrameInterval(): Long = frameInterval
    
    /**
     * Cleans up the OSR renderer resources.
     */
    fun cleanup() {
        logger.info("Cleaning up OSR renderer optimizations")
        lastOptimizationTime = 0L
        logger.info("OSR renderer cleanup completed")
    }
}
