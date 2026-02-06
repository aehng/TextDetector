// Generated with AI
// PendingLog.kt
// Data class representing an event that is stored locally and pending synchronization with Firestore.

package com.example.stayaccountable

/**
 * Represents a locally stored event log that is pending synchronization with Firestore.
 *
 * This class is used to hold event data retrieved from the local SQLite database
 * before it is sent to the remote server. It contains all the necessary information
 * for creating an [Event] object to be stored in Firestore.
 *
 * @property id The unique local ID of the log entry.
 * @property userId The ID of the user associated with this log.
 * @property description The textual description of the event.
 * @property severity The severity level of the event.
 * @property timestamp The time the event occurred, in milliseconds since the epoch.
 */
data class PendingLog(
    val id: Long,
    val userId: String,
    val description: String,
    val severity: Int,
    val timestamp: Long
) {
    /**
     * Converts this [PendingLog] object into an [Event] object.
     * This is useful for when the log is ready to be processed or sent to a remote data source
     * that uses the [Event] model.
     * @return An [Event] object with the same data.
     */
    fun toEvent(): Event = Event(
        id = id,
        description = description,
        severity = severity,
        timestamp = timestamp,
        userId = userId
    )
}
