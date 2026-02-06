// MyAccessibilityService.kt
// The core accessibility service for StayAccountable, responsible for monitoring screen content,
// detecting specific keywords, and logging events.

package com.example.stayaccountable

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth

/**
 * Represents a keyword to be detected, including its text, matching regex, and severity.
 */
class BadWord(val word: String, val regex: Regex, val severity: Int)

/**
 * The main accessibility service for StayAccountable.
 *
 * This service runs in the background and monitors the screen for text changes and content updates.
 * It is responsible for:
 * - Reading text from the screen.
 * - Matching text against a predefined list of "bad words".
 * - Generating leet-speak variations of words for more robust detection.
 * - Deduplicating recent detections to avoid spam.
 * - Creating user notifications upon detection.
 * - Saving detected events to the local database.
 * - Broadcasting events to the main application UI.
 */
class MyAccessibilityService : AccessibilityService() {

    // --- Service Configuration & State ---
    private var myPackageName: String? = null
    private lateinit var prefs: SharedPreferences
    private var badWords: List<BadWord> = emptyList()

    // --- Notification Constants ---
    private val CHANNEL_ID = "stayaccountable_detection_channel"
    private val NOTIFICATION_ID = 1

    // --- Deduplication Logic ---
    private val recentDetections = mutableMapOf<String, Long>()
    private val DEDUPLICATION_WINDOW_MS = 60_000L // 1 minute

    /**
     * Called by the system when the service is first connected (i.e., enabled by the user).
     * Sets up the service configuration, loads resources, and creates the notification channel.
     */
    override fun onServiceConnected() {
        super.onServiceConnected()
        myPackageName = packageName
        Log.d("MyAccessibilityService", "Service connected.")
        Toast.makeText(this, "StayAccountable service is running.", Toast.LENGTH_SHORT).show()

        prefs = getSharedPreferences(AccServiceSwitch.PREFS_NAME, MODE_PRIVATE)

        // Configure the service to listen for relevant event types across all apps.
        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 500 // Coalesce events to reduce frequency.
            packageNames = null       // Monitor all packages.
        }

        createNotificationChannel()
        loadBadWordsFromAssets()
    }

    /**
     * The main event processing loop. This method is called by the system for each relevant accessibility event.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Honor the user's choice to enable/disable the service from the app.
        if (!prefs.getBoolean(AccServiceSwitch.KEY_SERVICE_ACTIVE, true)) return
        if (event == null) return

        // Ignore events from the System UI and this app itself to prevent loops and noise.
        val pkgName = event.packageName?.toString() ?: ""
        if (pkgName == "com.android.systemui" || pkgName == myPackageName) return

        // Gather all text from the event source and its children.
        val allText = extractTextFromEvent(event).trim().lowercase()
        if (allText.isEmpty()) return

        // Process the collected text to find unique words.
        val uniqueWords = allText.split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()
        if (uniqueWords.isEmpty()) return

        // Match words against the bad word list.
        val detectedBadWords = badWords.filter { badWord -> uniqueWords.any { badWord.regex.matches(it) } }

        // Handle the detected words (log, notify, etc.).
        processDetections(detectedBadWords)
    }

    /**
     * Extracts all readable text from an [AccessibilityEvent] and its source node hierarchy.
     */
    private fun extractTextFromEvent(event: AccessibilityEvent): String {
        val eventText = event.text.joinToString(" ")
        val contentDesc = event.contentDescription?.toString() ?: ""
        var allText = "$eventText $contentDesc"

        try {
            event.source?.let { allText += " ${getTextFromNode(it)}" }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Error extracting text from node: ${e.message}")
        }
        return allText
    }

    /**
     * Iterates through detected bad words, handles deduplication, and triggers logging/notification.
     */
    private fun processDetections(detectedBadWords: List<BadWord>) {
        val now = System.currentTimeMillis()
        for (foundWord in detectedBadWords) {
            val lastDetected = recentDetections[foundWord.word]

            // If the word was detected within the deduplication window, skip it.
            if (lastDetected != null && (now - lastDetected < DEDUPLICATION_WINDOW_MS)) {
                continue
            }

            // Record the new detection time and proceed.
            recentDetections[foundWord.word] = now
            val notificationBody = "StayAccountable detected: '${foundWord.word}'"
            Log.d("MyAccessibilityService", "Logging and notifying for word '${foundWord.word}'")

            // Persist the event and notify the user.
            logEvent(foundWord.word, foundWord.severity)
            showNotification(notificationBody)
        }
    }

    /**
     * Logs a detected event to the local database and broadcasts it to the app.
     */
    private fun logEvent(description: String, severity: Int) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        // Only log if a user is signed in.
        if (currentUser != null) {
            val dbHelper = EventDatabaseHelper(applicationContext)
            dbHelper.insertEvent(description, severity, System.currentTimeMillis(), currentUser.uid)
            // Schedule a background sync to upload the new log.
            LogSyncScheduler.scheduleSync(applicationContext)
        } else {
            Log.w("MyAccessibilityService", "Skipping log persistence: no Firebase user signed in.")
        }

        // Broadcast the event to the running application so the UI can update in real-time.
        val intent = Intent("com.example.stayaccountable.EVENT_BROADCAST").apply {
            setPackage(packageName)
            putExtra("description", description)
            putExtra("severity", severity)
        }
        sendBroadcast(intent)
    }


    /**
     * Loads the list of bad words from the `assets/bad_words.csv` file.
     * This file should be in `word,severity` format.
     */
    private fun loadBadWordsFromAssets() {
        val foundWords = mutableListOf<BadWord>()
        try {
            applicationContext.assets.open("bad_words.csv").bufferedReader().useLines { lines ->
                lines.drop(1) // Skip header row
                    .forEach { line ->
                        val parts = line.split(',')
                        if (parts.size == 2) {
                            val word = parts[0].trim().lowercase()
                            val severity = parts[1].trim().toIntOrNull()
                            if (severity != null) {
                                // For each word, generate its leet-speak variations.
                                generateLeetVariations(word).forEach { variant ->
                                    val prefix = if (variant.first().isLetterOrDigit()) "\\b" else ""
                                    val suffix = if (variant.last().isLetterOrDigit()) "\\b" else ""
                                    val regex = Regex("$prefix${Regex.escape(variant)}$suffix", RegexOption.IGNORE_CASE)
                                    foundWords.add(BadWord(word, regex, severity))
                                }
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to load bad words: ${e.message}")
        }
        // Sort by severity to ensure more severe words are checked first (though current logic doesn't require it).
        badWords = foundWords.sortedByDescending { it.severity }
    }

    /**
     * Generates a set of "leet-speak" variations for a given word.
     * Example: 'password' -> {'password', 'p@ssw0rd', 'p4ssword', ...}
     */
    private fun generateLeetVariations(word: String): Set<String> {
        val variations = mutableSetOf(word)
        val leetMap = mapOf(
            'a' to listOf("4", "@"), 'e' to listOf("3"), 'i' to listOf("1", "!"),
            'o' to listOf("0"), 's' to listOf("5", "$"), 't' to listOf("7")
        )

        fun findCombinations(index: Int, currentString: String) {
            if (index == word.length) {
                variations.add(currentString)
                return
            }
            val originalChar = word[index]
            // Recurse with the original character.
            findCombinations(index + 1, currentString + originalChar)
            // If a leet replacement exists, recurse with the replacement.
            leetMap[originalChar]?.forEach { replacement ->
                findCombinations(index + 1, currentString + replacement)
            }
        }
        findCombinations(0, "")
        return variations
    }

    /**
     * Recursively traverses a node and its children to extract all visible text.
     */
    private fun getTextFromNode(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val builder = StringBuilder()
        if (node.text != null) builder.append(node.text).append(" ")
        if (node.contentDescription != null) builder.append(node.contentDescription).append(" ")

        for (i in 0 until node.childCount) {
            builder.append(getTextFromNode(node.getChild(i)))
        }
        return builder.toString()
    }

    /**
     * Creates the notification channel required for sending notifications on Android 8.0+.
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Text Detection Alerts"
            val descriptionText = "Notifications for when keywords are detected."
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    /**
     * Displays a system notification to the user about the detected word.
     */
    private fun showNotification(notificationText: String) {
        try {
            val intent = Intent(this, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("StayAccountable Alert")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setVibrate(longArrayOf(0, 250, 250, 250))

            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.e("MyAccessibilityService", "Failed to show notification: ${e.message}")
        }
    }

    override fun onInterrupt() {
        // This service does not hold state that needs to be managed on interruption.
    }
}
