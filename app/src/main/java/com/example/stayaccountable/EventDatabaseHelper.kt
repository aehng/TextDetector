// EventDatabaseHelper.kt
// Handles SQLite database operations for storing and retrieving event logs in StayAccountable.

package com.example.stayaccountable

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.database.Cursor

// EventDatabaseHelper: Manages the events database (create, insert, query)
class EventDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
    companion object {
        private const val DB_NAME = "events.db" // Database file name
        private const val DB_VERSION = 2 // Database version
        private const val TABLE_EVENTS = "events" // Table name
        private const val COL_ID = "id"
        private const val COL_DESCRIPTION = "description"
        private const val COL_SEVERITY = "severity"
        private const val COL_TIMESTAMP = "timestamp"
    }

    // Called when the database is first created
    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_EVENTS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DESCRIPTION TEXT NOT NULL,
                $COL_SEVERITY INTEGER NOT NULL,
                $COL_TIMESTAMP INTEGER NOT NULL
            )
        """
        db.execSQL(createTable)
    }

    // Called when the database needs to be upgraded
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No upgrade logic needed
    }

    // Inserts a new event into the database
    fun insertEvent(description: String, severity: Int, timestamp: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put(COL_DESCRIPTION, description)
            put(COL_SEVERITY, severity)
            put(COL_TIMESTAMP, timestamp)
        }
        return writableDatabase.insert(TABLE_EVENTS, null, values)
    }

    // Retrieves all events from the database, sorted by most recent
    fun getAllEvents(): List<Event> {
        val events = mutableListOf<Event>()
        val cursor: Cursor = readableDatabase.query(
            TABLE_EVENTS,
            null, null, null, null, null,
            "$COL_TIMESTAMP DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(COL_ID))
                val desc = it.getString(it.getColumnIndexOrThrow(COL_DESCRIPTION))
                val sev = it.getInt(it.getColumnIndexOrThrow(COL_SEVERITY))
                val ts = it.getLong(it.getColumnIndexOrThrow(COL_TIMESTAMP))
                events.add(Event(id, desc, sev, ts, null))
            }
        }
        return events
    }
}
