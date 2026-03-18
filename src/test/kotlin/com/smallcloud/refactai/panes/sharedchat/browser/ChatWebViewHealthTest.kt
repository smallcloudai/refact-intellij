package com.smallcloud.refactai.panes.sharedchat.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatWebViewHealthTest {

    @Test
    fun stalePongMarksRendererUnresponsive() {
        val now = 120_000L
        val lastPongAt = now - JCEF_UNRESPONSIVE_PONG_TIMEOUT_MS - 1

        assertTrue(isJcefRendererUnresponsive(now, lastPongAt))
    }

    @Test
    fun recentPongKeepsRendererResponsive() {
        val now = 120_000L
        val lastPongAt = now - 3_000L

        assertFalse(isJcefRendererUnresponsive(now, lastPongAt))
        assertTrue(hasRecentJcefPong(now, lastPongAt))
    }

    @Test
    fun pongAtThirtySecondsTriggersRecoveryWindow() {
        val now = 120_000L
        val lastPongAt = now - JCEF_UNRESPONSIVE_PONG_TIMEOUT_MS - 1

        assertTrue(isJcefRendererUnresponsive(now, lastPongAt))
        assertFalse(hasRecentJcefPong(now, lastPongAt))
    }

    @Test
    fun pongOutsideHealthyWindowStopsStableCredit() {
        val now = 120_000L
        val lastPongAt = now - JCEF_HEALTHY_PONG_WINDOW_MS - 1

        assertFalse(hasRecentJcefPong(now, lastPongAt))
    }
}
