// MyAccessibilityService.kt
// AccessibilityService implementation for StayAccountable
// Detects bad words on screen and logs events for accountability

package com.example.stayaccountable

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

// Data class representing a bad word, its regex, and severity
class BadWord(val word: String, val regex: Regex, val severity: Int)

// Main accessibility service for monitoring screen content
class MyAccessibilityService : AccessibilityService() {
    // Package name of this app (used to ignore self events)
    private var myPackageName: String? = null
    // Timestamp of last notification sent
    private var lastNotificationTime = 0L
    // Notification channel and ID constants
    private val CHANNEL_ID = "stayaccountable_detection_channel"
    private val NOTIFICATION_ID = 1
    // Used for deduplication of events
    private var lastSentNormalizedText: String? = null
    private var lastSentTime = 0L
    private val SENT_DEDUP_TTL = 10_000L // 10 seconds
    // List of bad words loaded from assets
    private var badWords: List<BadWord> = emptyList()
    // Lock for synchronizing event deduplication
    private val lock = Any()
    // SharedPreferences for service state
    private lateinit var prefs: SharedPreferences

    // Called when the service is connected/enabled
    override fun onServiceConnected() {
        super.onServiceConnected()
        myPackageName = packageName
        android.util.Log.d("MyAccessibilityService", "onServiceConnected called")
        android.widget.Toast.makeText(this, "Service running", android.widget.Toast.LENGTH_SHORT).show()
        // Load preferences for service state
        prefs = getSharedPreferences(AccServiceSwitch.PREFS_NAME, Context.MODE_PRIVATE)
        try {
            val info = serviceInfo
            // Listen for both text and content changes
            info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            info.feedbackType = android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.notificationTimeout = 500 // Reduce event frequency
            info.packageNames = null // Listen to all packages
            serviceInfo = info
        } catch (_: Exception) {}
        createNotificationChannel()
        loadBadWordsFromAssets()
    }

    // Loads bad words and their severities from assets/bad_words.csv
    private fun loadBadWordsFromAssets() {
        val foundWords = mutableListOf<BadWord>()
        try {
            val inputStream = applicationContext.assets.open("bad_words.csv")
            val reader = inputStream.bufferedReader()
            val lines = reader.readLines()
            // Skip header, parse each line as word,severity
            lines.drop(1).forEach { line ->
                val parts = line.split(',')
                if (parts.size == 2) {
                    val word = parts[0].trim().lowercase()
                    val severity = parts[1].trim().toIntOrNull()
                    if (severity != null) {
                        val variations = generateLeetVariations(word)
                        for (d in variations) {
                            // Add regex for each leet variation
                            val prefix = if (d.first().isLetterOrDigit()) "\\b" else ""
                            val suffix = if (d.last().isLetterOrDigit()) "\\b" else ""
                            val searchRegex = Regex("$prefix${Regex.escape(d)}$suffix")
                            foundWords.add(BadWord(d, searchRegex, severity))
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        badWords = foundWords.sortedByDescending { it.severity }
    }

    // Creates a notification channel for alerts (Android O+)
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Text Detection Alerts"
            val descriptionText = "Notifications when key words are detected on screen."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.createNotificationChannel(channel)
            } catch (_: Exception) {}
        }
    }

    // Main event handler: called for every relevant accessibility event
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Check if service is active (user can toggle in app)
        val isServiceActive = prefs.getBoolean(AccServiceSwitch.KEY_SERVICE_ACTIVE, true)
        if (!isServiceActive) return
        if (event == null) return
        val pkgName = event.packageName?.toString() ?: ""
        // Ignore events from system UI and this app
        if (pkgName == "com.android.systemui" || pkgName == myPackageName) return
        // Gather all visible text from the event
        val eventText = event.text.joinToString(" ")
        val contentDesc = event.contentDescription?.toString() ?: ""
        var allText = "$eventText $contentDesc"
        // Recursively extract text from node tree in background
        thread {
            try {
                event.source?.let { node ->
                    val nodeText = getTextFromNode(node)
                    allText += " $nodeText"
                }
            } catch (_: Exception) {}
            allText = allText.trim()
            if (allText.isEmpty()) return@thread
            val toProcess = allText.lowercase()
            var foundWord: BadWord? = null
            // Check for any bad word match
            for (badWord in badWords) {
                if (badWord.regex.containsMatchIn(toProcess)) {
                    foundWord = badWord
                    break
                }
            }
            if (foundWord != null) {
                val now = System.currentTimeMillis()
                val normalized = toProcess.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                synchronized(lock) {
                    // Deduplicate: don't send same event repeatedly
                    if (lastSentNormalizedText != null && normalized == lastSentNormalizedText && now - lastSentTime < SENT_DEDUP_TTL) {
                        return@synchronized
                    }
                    if (now - lastNotificationTime > 5000) {
                        lastSentNormalizedText = normalized
                        lastSentTime = now
                        lastNotificationTime = now
                        val notificationBody = "StayAccountable detected: '${foundWord.word}'"
                        // Log the event in the app
                        sendEventBroadcast(foundWord.word, foundWord.severity)
                        // Show notification to user
                        showNotification(notificationBody)
                    }
                }
            }
        }
    }

    // Generates all leet-speak variations for a word (e.g., a->4, e->3)
    private fun generateLeetVariations(word: String): Set<String> {
        val variations = mutableSetOf<String>()
        val leetMap = mapOf(
            'a' to listOf("4", "@"),
            'e' to listOf("3"),
            'i' to listOf("1", "!"),
            'o' to listOf("0"),
            's' to listOf("5", "$"),
            't' to listOf("7")
        )
        fun findCombinations(index: Int, currentString: String) {
            if (index == word.length) {
                variations.add(currentString)
                return
            }
            val originalChar = word[index]
            findCombinations(index + 1, currentString + originalChar)
            if (leetMap.containsKey(originalChar)) {
                for (replacement in leetMap.getValue(originalChar)) {
                    findCombinations(index + 1, currentString + replacement)
                }
            }
        }
        findCombinations(0, "")
        return variations
    }

    // Recursively extracts all text from a node and its children
    private fun getTextFromNode(node: AccessibilityNodeInfo): String {
        val builder = StringBuilder()
        node.text?.let { builder.append(it).append(" ") }
        node.contentDescription?.let { builder.append(it).append(" ") }
        for (i in 0 until node.childCount) {
            try {
                node.getChild(i)?.let { child ->
                    builder.append(getTextFromNode(child)).append(" ")
                }
            } catch (_: Exception) {}
        }
        return builder.toString().trim()
    }

    // Shows a notification to the user when a bad word is detected
    private fun showNotification(notificationText: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("StayAccountable Alert!")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Swallow exceptions to avoid crashing the service
        }
    }

    // Broadcasts an event to the app for logging in the event database
    private fun sendEventBroadcast(description: String, severity: Int) {
        val dbHelper = EventDatabaseHelper(applicationContext)
        dbHelper.insertEvent(description, severity, System.currentTimeMillis())
        val intent = Intent("com.example.stayaccountable.EVENT_BROADCAST")
        intent.putExtra("description", description)
        intent.putExtra("severity", severity)
        sendBroadcast(intent)
    }

    // Required override: called if the service is interrupted
    override fun onInterrupt() {
        // Minimal logging policy: only log errors
    }

    // Required override: called when the service is destroyed
    override fun onDestroy() {
        super.onDestroy()
        // Minimal logging policy
    }
}
