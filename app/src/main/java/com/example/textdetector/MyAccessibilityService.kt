package com.example.textdetector

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import android.util.Log

class MyAccessibilityService : AccessibilityService() {
    private var lastNotificationTime = 0L
    private val CHANNEL_ID = "dog_detection_channel"
    private val NOTIFICATION_ID = 1
    // Prevent reacting to our own notification text and system UI echoes
    private var lastNotificationText: String? = null
    private var lastNotificationTextTime = 0L
    private val NOTIFICATION_TEXT_TTL = 10_000L // keep last notification text for 10s
    // De-duplication: track last sent normalized text and time to avoid repeated notifications
    private var lastSentNormalizedText: String? = null
    private var lastSentTime = 0L
    private val SENT_DEDUP_TTL = 10_000L // 10s
    // Notification body constant
    private val NOTIFICATION_BODY = "The word 'dog' was found on screen."

    override fun onServiceConnected() {
        super.onServiceConnected()
        // Configure the service; avoid verbose logs
        try {
            val info = serviceInfo
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 100
            // Listen to all packages (system-wide)
            info.packageNames = null
            serviceInfo = info
        } catch (_: Exception) {
            Log.e("DogDetection", "Error configuring service")
        }

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Dog Detection Alerts"
            val descriptionText = "Notifications when 'dog' is detected on screen."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            } catch (_: Exception) {
                Log.e("DogDetection", "Error creating notification channel")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Ignore system UI events to avoid reacting to notification shade updates
        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName == "com.android.systemui") {
            return
        }

        // If the last notification text is recent, ignore events that contain it
        val now = System.currentTimeMillis()
        if (lastNotificationText != null && now - lastNotificationTextTime < NOTIFICATION_TEXT_TTL) {
            val eventTextPreview = (event.text.joinToString(" ") + " " + (event.contentDescription?.toString() ?: "")).trim()
            if (eventTextPreview.contains(lastNotificationText!!, ignoreCase = true)) {
                return
            }
        }

        // Gather text from the event and node tree
        val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
        val eventText = event.text.joinToString(" ")
        val contentDesc = event.contentDescription?.toString() ?: ""
        var allText = "$eventText $contentDesc"

        // Also try to get text from the source node
        try {
            event.source?.let { node ->
                val nodeText = getTextFromNode(node)
                allText += " $nodeText"
                // do not call deprecated recycle()
            }
        } catch (_: Exception) {
            Log.e("DogDetection", "Error getting node text")
        }

        allText = allText.trim()

        if (allText.isNotEmpty() && allText.lowercase().contains("dog")) {
            // Normalize and dedupe
            val now2 = System.currentTimeMillis()
            val normalized = allText.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
            if (lastSentNormalizedText != null && normalized == lastSentNormalizedText && now2 - lastSentTime < SENT_DEDUP_TTL) {
                return
            }
            if (now2 - lastNotificationTime > 5000) {
                // record dedupe keys and notification text BEFORE posting to avoid immediate echo-triggered duplicates
                lastSentNormalizedText = normalized
                lastSentTime = now2
                lastNotificationTime = now2
                lastNotificationText = NOTIFICATION_BODY
                lastNotificationTextTime = now2

                // Log only when a detection occurs: include package, event type, and the full extracted text (truncated to 2000 chars)
                val toLog = if (allText.length > 2000) allText.take(2000) + "..." else allText
                Log.i("DogDetection", "DETECTED 'dog' pkg=$pkgName event=$eventTypeName text=\"$toLog\"")

                showDogNotification()
            }
        }
    }

    private fun getTextFromNode(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()

        // Get text from this node
        node.text?.let { builder.append(it).append(" ") }
        node.contentDescription?.let { builder.append(it).append(" ") }

        // Recursively get text from child nodes
        for (i in 0 until node.childCount) {
            try {
                node.getChild(i)?.let { child ->
                    builder.append(getTextFromNode(child)).append(" ")
                    // do not call deprecated recycle()
                }
            } catch (_: Exception) {
                // Skip if child is not accessible
            }
        }

        return builder.toString().trim()
    }

    private fun showDogNotification() {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationText = NOTIFICATION_BODY
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🐕 Dog Detected!")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            Log.e("DogDetection", "Error showing notification")
        }
    }

    override fun onInterrupt() {
        // Minimal logging policy: only log errors
    }

    override fun onDestroy() {
        super.onDestroy()
        // Minimal logging policy
    }
}
