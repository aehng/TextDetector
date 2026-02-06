// Generated with AI
// EventDatabaseHelper.kt
// Handles SQLite database operations for storing and retrieving event logs in StayAccountable.

package com.example.stayaccountable

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor

/**
 * Manages the local SQLite database for storing accountability events.
 * This class handles creating the database, managing versions, and providing
 * methods to insert, query, and delete event data.
 *
 * @param context The application context.
 */
class EventDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    companion object {
        // --- Database and Table Constants ---
        private const val DB_NAME = "events.db"       // Database file name
        private const val DB_VERSION = 3              // Database version
        private const val TABLE_EVENTS = "events"     // Table name

        // --- Column Names ---
        private const val COL_ID = "id"
        private const val COL_DESCRIPTION = "description"
        private const val COL_SEVERITY = "severity"
        private const val COL_TIMESTAMP = "timestamp"
        private const val COL_USER_ID = "user_id"
    }

    /**
     * Called when the database is created for the first time. This is where the
     * creation of tables and the initial population of the tables should happen.
     * @param db The database.
     */
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DESCRIPTION TEXT NOT NULL,
                $COL_SEVERITY INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL,
                $COL_USER_ID TEXT NOT NULL
            )
        """
        db.execSQL(createTable)
    }

    /**
     * Called when the database needs to be upgraded. This method will only be called if
     * the database version is higher than the current version.
     * @param db The database.
     * @param oldVersion The old database version.
     * @param newVersion The new database version.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // If upgrading from a version older than 3, add the user_id column.
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE $TABLE_EVENTS ADD COLUMN $COL_USER_ID TEXT DEFAULT ''''")
        }
    }

    /**
     * Inserts a new event into the database.
     * @param description The event description.
     * @param severity The severity level of the event.
     * @param timestamp The time the event occurred (defaults to current time).
     * @param userId The ID of the user associated with the event.
     * @return The row ID of the newly inserted row, or -1 if an error occurred.
     */
    fun insertEvent(
        description: String,
        severity: Int,
        timestamp: Long = System.currentTimeMillis(),
        userId: String
    ): Long {
        val values = ContentValues().apply {
            put(COL_DESCRIPTION, description)
            put(COL_SEVERITY, severity)
            put(COL_TIMESTAMP, timestamp)
            put(COL_USER_ID, userId)
        }
        return writableDatabase.insert(TABLE_EVENTS, null, values)
    }

    /**
     * Retrieves all events from the database, with an option to filter by user.
     * Events are sorted from most recent to oldest.
     * @param userId (Optional) The ID of the user to filter events for. If null, returns all events.
     * @return A list of [Event] objects.
     */
    fun getAllEvents(userId: String? = null): List<Event> {
        val events = mutableListOf<Event>()

        // Set up query to filter by userId if provided
        val (selection, selectionArgs) = if (userId != null) {
            "$COL_USER_ID = ?" to arrayOf(userId)
        } else {
            null to null // No filter
        }

        val cursor: Cursor = readableDatabase.query(
            TABLE_EVENTS,
            null, // All columns
            selection,
            selectionArgs,
            null, // groupBy
            null, // having
            "$COL_TIMESTAMP DESC" // orderBy (most recent first)
        )

        // Iterate through the cursor and build the list of events
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                val desc = it.getString(it.getColumnIndexOrThrow(COL_DESCRIPTION))
                val sev = it.getInt(it.getColumnIndexOrThrow(COL_SEVERITY))
                val ts = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                val userIdFromDb = it.getString(it.getColumnIndexOrThrow(COL_USER_ID))
                events.add(Event(id, desc, sev, ts, userIdFromDb))
            }
        }
        return events
    }

    /**
     * Retrieves all locally stored events for a specific user that are considered "pending".
     * Events are sorted chronologically (oldest to newest).
     * @param userId The ID of the user whose pending events are to be fetched.
     * @return A list of [PendingLog] objects, representing unsynchronized events.
     */
    fun getPendingEventsForUser(userId: String): List<PendingLog> {
        val pending = mutableListOf<PendingLog>()
        val cursor = readableDatabase.query(
            TABLE_EVENTS,
            null, // All columns
            "$COL_USER_ID = ?", // WHERE clause
            arrayOf(userId),   // WHERE arguments
            null, // groupBy
            null, // having
            "$COL_TIMESTAMP ASC" // orderBy (oldest first)
        )

        // Iterate through the cursor and build the list of pending logs
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                val desc = it.getString(it.getColumnIndexOrThrow(COL_DESCRIPTION))
                val sev = it.getInt(it.getColumnIndexOrThrow(COL_SEVERITY))
                val ts = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                pending.add(PendingLog(id, userId, desc, sev, ts))
            }
        }
        return pending
    }

    /**
     * Deletes a specific event from the database by its unique ID.
     * @param id The ID of the event to delete.
     */
    fun deleteEventById(id: Long) {
        writableDatabase.delete(TABLE_EVENTS, "$COL_ID = ?", arrayOf(id.toString()))
    }

    /**
     * Deletes all events for a specific user from the local database.
     * @param userId The ID of the user whose events are to be deleted.
     */
    fun deleteEventsForUser(userId: String) {
        writableDatabase.delete(TABLE_EVENTS, "$COL_USER_ID = ?", arrayOf(userId))
    }
}
