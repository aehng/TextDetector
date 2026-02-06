// Generated with AI
// LogSyncWorker.kt
// A background worker responsible for synchronizing local event logs with the remote Firestore database.

package com.example.stayaccountable

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A background worker that syncs pending event logs from the local database to Firestore.
 *
 * This worker is designed to run periodically to ensure that locally stored accountability
 * events are uploaded to the user's profile in Firestore. It handles fetching pending
 * logs, uploading them, and deleting them from the local database upon successful upload.
 *
 * @param appContext The application context.
 * @param workerParams Parameters for the worker.
 */
class LogSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    // Lazily initialized properties to ensure they are created only when first needed.

    // Firebase Auth instance to get the currently signed-in user.
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    // Firestore instance for accessing the remote database.
    private val firestore by lazy { Firebase.firestore }
    // Local SQLite database helper to access locally stored events.
    private val dbHelper by lazy { EventDatabaseHelper(appContext) }

    /**
     * The main entry point for the worker. This function is called by the WorkManager.
     *
     * It checks for a signed-in user, fetches their pending logs from the local DB,
     * and attempts to upload each one to Firestore.
     *
     * @return [Result.success] if all logs are synced successfully.
     *         [Result.retry] if there is no signed-in user or if any log upload fails, to reschedule the work.
     */
    override suspend fun doWork(): Result {
        val currentUser = auth.currentUser ?: return Result.retry() // Retry if no user is logged in.

        // Fetch all unsynced logs for the current user from the local database.
        val pendingLogs = dbHelper.getPendingEventsForUser(currentUser.uid)

        // If there are no logs to sync, the work is done.
        if (pendingLogs.isEmpty()) return Result.success()

        // Iterate over each pending log and attempt to upload it.
        for (log in pendingLogs) {
            val uploaded = uploadLog(currentUser.uid, log)
            if (uploaded) {
                // If upload is successful, delete the log from the local database to prevent re-syncing.
                dbHelper.deleteEventById(log.id)
            } else {
                // If any upload fails, stop and retry the entire operation later.
                return Result.retry()
            }
        }

        // If all logs were uploaded and deleted successfully.
        return Result.success()
    }

    /**
     * Uploads a single pending log to the user's 'logs' collection in Firestore.
     *
     * This function uses `suspendCancellableCoroutine` to bridge Firebase's callback-based API
     * with Kotlin's coroutines, allowing it to be called from a suspend function.
     *
     * @param userId The ID of the user to whom the log belongs.
     * @param log The [PendingLog] object to upload.
     * @return `true` if the upload was successful, `false` otherwise.
     */
    private suspend fun uploadLog(userId: String, log: PendingLog): Boolean =
        suspendCancellableCoroutine { continuation ->
            val payload = hashMapOf(
                "description" to log.description,
                "severity" to log.severity,
                "timestamp" to log.timestamp
            )

            // Firestore path: /users/{userId}/logs/{auto-generated-id}
            firestore.collection("users")
                .document(userId)
                .collection("logs")
                .add(payload)
                .addOnSuccessListener {
                    Log.d("LogSyncWorker", "Log uploaded for user: $userId, logId: ${log.id}")
                    // Resume the coroutine with a success result.
                    if (continuation.isActive) continuation.resume(true)
                }
                .addOnFailureListener { e ->
                    Log.e("LogSyncWorker", "Failed to upload log: ${e.message}")
                    // Resume the coroutine with a failure result.
                    if (continuation.isActive) continuation.resume(false)
                }
        }
}
