package com.example.textdetector

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
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
        Log.d("DogDetection", "✓ Accessibility service connected and running")

        // Programmatically configure the service to ensure it captures events
        try {
            val info = serviceInfo
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                         android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 100
            // Listen to all packages (system-wide) so detection works outside this app
            info.packageNames = null
            serviceInfo = info
            Log.d("DogDetection", "✓ Service configuration updated programmatically")
            // Diagnostic dump of serviceInfo
            try {
                val si = serviceInfo
                Log.d("DogDetection", "serviceInfo.eventTypes=${si.eventTypes}")
                Log.d("DogDetection", "serviceInfo.feedbackType=${si.feedbackType}")
                Log.d("DogDetection", "serviceInfo.flags=${si.flags}")
                Log.d("DogDetection", "serviceInfo.notificationTimeout=${si.notificationTimeout}")
                Log.d("DogDetection", "serviceInfo.packageNames=${si.packageNames?.joinToString()}")
            } catch (e: Exception) {
                Log.e("DogDetection", "Error dumping serviceInfo: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e("DogDetection", "Error configuring service: ${e.message}")
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
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            Log.d("DogDetection", "✓ Notification channel created")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Ignore system UI events to avoid reacting to notification shade updates
        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName == "com.android.systemui") {
            Log.d("DogDetection", "Ignoring system UI events to avoid loops")
            return
        }

        // If the last notification text is recent, ignore events that contain it
        val now = System.currentTimeMillis()
        if (lastNotificationText != null && now - lastNotificationTextTime < NOTIFICATION_TEXT_TTL) {
            // We'll still gather text to log, but skip detection if it matches our notification
            val eventTextPreview = (event.text.joinToString(" ") + " " + (event.contentDescription?.toString() ?: "")).trim()
            if (eventTextPreview.contains(lastNotificationText!!, ignoreCase = true)) {
                Log.d("DogDetection", "Ignoring event because it matches last notification text")
                return
            }
        }

        // Log ALL events to see what we're receiving
        val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
        Log.d("DogDetection", "📥 Event: $eventTypeName | pkg=${event.packageName} | class=${event.className}")

        // Get text from the event
        val eventText = event.text.joinToString(" ")
        val contentDesc = event.contentDescription?.toString() ?: ""
        var allText = "$eventText $contentDesc"

        // Also try to get text from the source node
        try {
            event.source?.let { node ->
                val nodeText = getTextFromNode(node)
                allText += " $nodeText"
                node.recycle()
            }
        } catch (e: Exception) {
            Log.e("DogDetection", "Error getting node text: ${e.message}")
        }

        allText = allText.trim()

        if (allText.isNotEmpty()) {
            Log.d("DogDetection", "📝 Text found: $allText")

            if (allText.lowercase().contains("dog")) {
                Log.d("DogDetection", "🐕 DETECTED 'dog' in text: $allText")
                val now2 = System.currentTimeMillis()
                // Normalize text to dedupe similar notifications (strip non-alphanum and collapse spaces)
                val normalized = allText.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                if (lastSentNormalizedText != null && normalized == lastSentNormalizedText && now2 - lastSentTime < SENT_DEDUP_TTL) {
                    Log.d("DogDetection", "Duplicate detection (same text within TTL), skipping notification")
                    return
                }
                if (now2 - lastNotificationTime > 5000) {
                    // record dedupe keys and notification text BEFORE posting to avoid immediate echo-triggered duplicates
                    lastSentNormalizedText = normalized
                    lastSentTime = now2
                    lastNotificationTime = now2
                    lastNotificationText = NOTIFICATION_BODY
                    lastNotificationTextTime = now2
                    showDogNotification()
                    Log.d("DogDetection", "✓ Notification sent")
                } else {
                    Log.d("DogDetection", "⏳ Skipped notification (debounce)")
                }
            }
        } else {
            Log.d("DogDetection", "⚠️ No text in this event")
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
                    child.recycle()
                }
            } catch (e: Exception) {
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
         } catch (e: Exception) {
             Log.e("DogDetection", "Error showing notification: ${e.message}")
         }
     }

    override fun onInterrupt() {
        Log.d("DogDetection", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("DogDetection", "Service destroyed")
    }
}
