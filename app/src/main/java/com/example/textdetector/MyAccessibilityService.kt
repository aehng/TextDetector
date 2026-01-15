package com.example.textdetector

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast


class MyAccessibilityService : AccessibilityService() {
    private var lastNotificationTime = 0L
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                // Handle the event here
                val eventTexts = event.text.joinToString(" ") { it.toString() }
                val contentDesc = event.contentDescription?.toString() ?: ""
                val allText = (event.text.joinToString(" ") { it.toString() } + " " +
                        (event.contentDescription?.toString() ?: "")).trim()

                if (allText.lowercase().contains("dog")) {
                    val now = System.currentTimeMillis()
                    if (now - lastNotificationTime > 5000) {
                        Toast.makeText(this, "The word 'dog' was detected!", Toast.LENGTH_LONG).show()
                        lastNotificationTime = now
                    }
                }
            }
            else -> {
                // Ignore other event types
            }
        }
    }

    override fun onInterrupt() {
        // Required override, can be left empty for now
    }
}
