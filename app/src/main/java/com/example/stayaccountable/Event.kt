// Generated with AI
// Event.kt
// Data class representing a single event (e.g., bad word detection) in StayAccountable.

package com.example.stayaccountable

/**
 * Represents a single accountability event.
 *
 * This data class holds all the information related to a specific event, such as
 * a detected keyword. It's used throughout the app to pass event data between
 * components.
 *
 * @property id The unique identifier for the event, typically assigned by the database.
 * @property description A textual description of the event (e.g., the word that was detected).
 * @property severity A numerical value indicating the event's severity level.
 * @property timestamp The exact time the event occurred, in milliseconds since the epoch.
 * @property userId The identifier for the user who triggered the event.
 */

data class Event(
    val id: Long = 0, // Unique event ID from the local database
    val description: String, // The detected word or event description
    val severity: Int, // Severity level of the event
    val timestamp: Long = System.currentTimeMillis(), // Timestamp of when the event occurred
    val userId: String? // The user associated with the event
)
