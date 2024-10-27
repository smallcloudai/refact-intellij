package com.smallcloud.refactai.utils

import com.intellij.ui.jcef.JBCefApp

fun isJcefCanStart(): Boolean {
    return try {
        JBCefApp.isSupported() && JBCefApp.isStarted()
        JBCefApp.isSupported()
    } catch (_: Exception) {
        false
    }
}