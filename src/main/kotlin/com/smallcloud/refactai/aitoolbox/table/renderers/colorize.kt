package com.smallcloud.refactai.aitoolbox.table.renderers

import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.image.RGBImageFilter
import javax.swing.Icon


private class FullColorizeFilter(val color: Color) : RGBImageFilter() {
    override fun filterRGB(x: Int, y: Int, rgba: Int): Int {
        val a = rgba shr 24 and 0xff
        var r = rgba shr 16 and 0xff
        var g = rgba shr 8 and 0xff
        var b = rgba and 0xff
        if (a != 0) {
            r = color.red
            g = color.green
            b = color.blue
        }
        return a shl 24 or (r and 255 shl 16) or (g and 255 shl 8) or (b and 255)
    }
}


internal fun colorize(originalIcon: Icon, foreground: Color): Icon {
    return IconLoader.filterIcon(originalIcon) { FullColorizeFilter(foreground) }
}