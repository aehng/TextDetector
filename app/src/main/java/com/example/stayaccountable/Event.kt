// Event.kt
// Data class representing a single event (bad word detection) in StayAccountable.

package com.example.stayaccountable

data class Event(
    val id: Long = 0, // Unique event ID (autoincremented by database)
    val description: String, // The detected word or event description
    val severity: Int, // Severity level of the event
    val timestamp: Long = System.currentTimeMillis(), // When the event occurred
    val screenshotPath: String? = null // Path to screenshot file, if any (kept for compatibility, always null)
)
