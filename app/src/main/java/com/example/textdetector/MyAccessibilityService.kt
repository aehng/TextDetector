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

class BadWord(val word: String,val regex: Regex, val severity: Int) {

}
class MyAccessibilityService : AccessibilityService() {
    private var myPackageName: String? = null
    private var lastNotificationTime = 0L
    private val CHANNEL_ID = "dog_detection_channel"
    private val NOTIFICATION_ID = 1
    private var lastSentNormalizedText: String? = null
    private var lastSentTime = 0L
    private val SENT_DEDUP_TTL = 10_000L // 10s
    // Notification body constant
    private var badWords: List<BadWord> = emptyList()

    override fun onServiceConnected() {
        super.onServiceConnected()
        myPackageName = packageName
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
        loadBadWordsFromAssets()
    }

//This function was generated using Gemini but modified by me
private fun loadBadWordsFromAssets() {
    val foundWords = mutableListOf<BadWord>()
    try {
        val inputStream = applicationContext.assets.open("bad_words.csv")
        val reader = inputStream.bufferedReader()

        // 1. Read all lines into a list.
        val lines = reader.readLines()

        // 2. Loop through the list, but skip the first line (the header).
        lines.drop(1).forEach { line ->
            // The rest of your logic stays exactly the same.
            val parts = line.split(',')
            if (parts.size == 2) {
                val word = parts[0].trim().lowercase()
                val severity = parts[1].trim().toIntOrNull()

                if (severity != null) {
                    val variations = generateLeetVariations(word)
                    for(d in variations){
                        // Escape the variation 'd' to handle special characters like '$' literally.
                        // Create the regex more intelligently.
                        // Add a word boundary at the start ONLY if the word starts with a letter or number.
                        val prefix = if (d.first().isLetterOrDigit()) "\\b" else ""
                        // Add a word boundary at the end ONLY if the word ends with a letter or number.
                        val suffix = if (d.last().isLetterOrDigit()) "\\b" else ""

                        val searchRegex = Regex("$prefix${Regex.escape(d)}$suffix")

                        foundWords.add(BadWord(d, searchRegex, severity))

                    }

                }
            }
        }

    } catch (e: Exception) {
        Log.e("DogDetection", "Error loading bad_words.csv: ${e.message}")
    }

    badWords = foundWords.sortedByDescending { it.severity }
    Log.i("DogDetection", "Loaded ${badWords.size} bad words.")
}



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
            } catch (_: Exception) {
                Log.e("DogDetection", "Error creating notification channel")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val pkgName = event.packageName?.toString() ?: ""

        // Ignore events from System UI and from our own app to prevent feedback loops.
        if (pkgName == "com.android.systemui" || pkgName == myPackageName) {
            return
        }

        // Gather text from the event and node tree
        val eventTypeName = AccessibilityEvent.eventTypeToString(event.eventType)
        val eventText = event.text.joinToString(" ")
        val contentDesc = event.contentDescription?.toString() ?: ""
        var allText = "$eventText $contentDesc"

        try {
            event.source?.let { node ->
                val nodeText = getTextFromNode(node)
                allText += " $nodeText"
            }
        } catch (_: Exception) {
            Log.e("DogDetection", "Error getting node text")
        }

        allText = allText.trim()

        if (allText.isNotEmpty()) {
            val textToSearch = allText.lowercase()
            var foundWord: BadWord? = null

            // --- This part is already correct and efficient ---
            // Find the highest severity word present in the text
            // The list is pre-sorted, so the first match is the highest priority
            for (badWord in badWords) {
                if (badWord.regex.containsMatchIn(textToSearch)) {
                    foundWord = badWord
                    break // Found it, stop searching
                }
            }
            // --- End of correct part ---

            // --- START: This is the corrected integration logic ---
            if (foundWord != null) {
                // A bad word was found! Now, check if we should notify.
                val now = System.currentTimeMillis()

                // 1. De-duplicate based on the actual screen content
                val normalized = textToSearch.replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
                if (lastSentNormalizedText != null && normalized == lastSentNormalizedText && now - lastSentTime < SENT_DEDUP_TTL) {
                    return // Same content seen recently, do nothing.
                }

                // 2. Throttle notifications to avoid being too frequent
                if (now - lastNotificationTime > 5000) {
                    // We are clear to send a notification!

                    // Record dedupe keys and time BEFORE posting
                    lastSentNormalizedText = normalized
                    lastSentTime = now
                    lastNotificationTime = now

                    // Create dynamic log and notification messages using the foundWord
                    val toLog = if (allText.length > 2000) allText.take(2000) + "..." else allText
                    val logMessage = "DETECTED '${foundWord.word}' (Severity: ${foundWord.severity}) pkg=$pkgName event=$eventTypeName text=\"$toLog\""
                    val notificationBody = "Detected word: '${foundWord.word}'"

                    Log.i("DogDetection", logMessage)

                    // Show the notification with the specific details
                    showNotification(notificationBody)
                }
            }
            // --- END: Corrected integration logic ---
        }
    }


    // Generated by AI adapted by me
    // In MyAccessibilityService.kt

    // In MyAccessibilityService.kt

    private fun generateLeetVariations(word: String): Set<String> {
        val variations = mutableSetOf<String>()
        // --- THIS IS THE FIX ---
        // The map should ONLY contain the replacements. The recursive function will handle
        // using the original character automatically.
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

            // --- AND THIS IS WHY IT WORKS ---
            // 1. Always try the path with the original character.
            findCombinations(index + 1, currentString + originalChar)

            // 2. If there are leetspeak replacements, try those paths as well.
            if (leetMap.containsKey(originalChar)) {
                for (replacement in leetMap.getValue(originalChar)) {
                    findCombinations(index + 1, currentString + replacement)
                }
            }
        }

        findCombinations(0, "")
        return variations
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


    //Generated by AI
    private fun showNotification(notificationText: String) {
        try {
            // Create an Intent that will open the app's MainActivity when the user taps the notification.
            val intent = Intent(this, MainActivity::class.java)

            // Wrap the Intent in a PendingIntent. This gives the notification a safe, system-managed token
            // that will launch the Intent later when the user interacts with the notification.
            // Flags used:
            // - FLAG_UPDATE_CURRENT: if the PendingIntent already exists, update its extra data with this Intent.
            // - FLAG_IMMUTABLE: the created PendingIntent cannot be modified (safer on newer Android versions).
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Build the notification using NotificationCompat for backward compatibility.
            // Provide a CHANNEL_ID so Android O+ devices route the notification to the correct channel.
            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                // Small icon shown in the status bar and notification header.
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                // Title shown in the notification; keep it short and clear.
                .setContentTitle("Text Detected!") // A more general title
                // The main body text (passed into this function).
                .setContentText(notificationText) // Use the text passed into the function
                // Priority hint for pre-Oreo devices (Oreo+ uses channel importance).
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                // When the user taps the notification, the PendingIntent will be fired.
                .setContentIntent(pendingIntent)
                // Automatically remove the notification from the shade when the user taps it.
                .setAutoCancel(true)
                // A short vibration pattern: [wait, vibrate, wait, vibrate]
                .setVibrate(longArrayOf(0, 250, 250, 250))

            // Get the NotificationManager and publish the notification with a stable ID.
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(NOTIFICATION_ID, builder.build())
        } catch (_: Exception) {
            // Log any error while building or showing the notification. We swallow the exception
            // to avoid crashing the accessibility service (robustness is important for background services).
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
