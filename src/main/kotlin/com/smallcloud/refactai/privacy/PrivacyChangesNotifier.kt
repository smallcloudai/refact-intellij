package com.smallcloud.refactai.privacy

import com.intellij.util.messages.Topic

interface PrivacyChangesNotifier {
    fun privacyChanged() {}

    companion object {
        val TOPIC = Topic.create(
            "Refact AI Privacy Changed Notifier",
            PrivacyChangesNotifier::class.java
        )
    }
}