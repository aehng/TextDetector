// Generated with AI
// AuthRepository.kt
// A repository class that abstracts the logic for interacting with Firebase Authentication.

package com.example.stayaccountable

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

/**
 * A repository for handling all authentication-related operations with Firebase.
 *
 * This class abstracts the details of Firebase Auth, providing a clean API for the
 * rest of the application to use for creating accounts, signing in, signing out,
 * and retrieving user information.
 */
class AuthRepository {
    // The core Firebase Authentication object.
    private lateinit var auth: FirebaseAuth
    private val firestore by lazy { Firebase.firestore }

    /**
     * Initializes the Firebase Auth instance.
     * This should be called once, typically at the application's entry point.
     */
    fun initAuth() {
        // Check if auth has already been initialized to avoid redundant calls.
        if (!::auth.isInitialized) {
            auth = FirebaseAuth.getInstance()
        }
    }

    /**
     * Retrieves the currently signed-in Firebase user.
     * @return The [FirebaseUser] if a user is signed in, otherwise `null`.
     */
    fun getCurrentUser(): FirebaseUser? = if (::auth.isInitialized) auth.currentUser else null

    /**
     * Checks if there is a user currently signed in.
     * @return `true` if a user is signed in, `false` otherwise.
     */
    fun isUserSignedIn(): Boolean = getCurrentUser() != null

    /**
     * Creates a new user account with the given email and password.
     *
     * @param email The user's email address.
     * @param password The user's chosen password.
     * @param onSuccess A callback function to execute upon successful account creation.
     * @param onFailure A callback function to execute upon failure, passing the exception.
     */
    fun createAccount(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception)
                }
            }
    }

    /**
     * Signs in an existing user with their email and password.
     *
     * @param email The user's email address.
     * @param password The user's password.
     * @param onSuccess A callback function to execute upon successful sign-in.
     * @param onFailure A callback function to execute upon failure, passing the exception.
     */
    fun signIn(
        email: String,
        password: String,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception)
                }
            }
    }

     /**
     * Updates the display name for the currently signed-in user.
     *
     * @param displayName The new name for the user.
     * @param onSuccess A callback function to execute on success.
     * @param onFailure A callback function to execute on failure.
     */
    fun updateDisplayName(
        displayName: String,
        onSuccess: () -> Unit,
        onFailure: (Exception?) -> Unit
    ) {
         val user = getCurrentUser() ?: run {
             onFailure(Exception("User not logged in"))
             return
         }
         val profileUpdates = UserProfileChangeRequest.Builder().setDisplayName(displayName).build()
         user.updateProfile(profileUpdates).addOnCompleteListener { task ->
             if (task.isSuccessful) {
                 onSuccess()
             } else {
                 onFailure(task.exception)
             }
         }
     }

    /**
     * Retrieves the email address of the currently signed-in user.
     * @return The user's email string, or `null` if no user is signed in.
     */
    fun getCurrentUserEmail(): String? {
        return getCurrentUser()?.email
    }

    /**
     * Deletes all of the user's logs from the remote Firestore database.
     */
    fun deleteFirestoreData(onSuccess: () -> Unit, onFailure: (Exception?) -> Unit) {
        val user = getCurrentUser() ?: run {
            onFailure(Exception("User not logged in"))
            return
        }

        firestore.collection("users").document(user.uid).collection("logs")
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()
                for (document in documents) {
                    batch.delete(document.reference)
                }
                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    /**
     * Deletes the currently signed-in user's account from Firebase Authentication.
     *
     * @param onSuccess A callback function to execute on successful deletion.
     * @param onFailure A callback function to execute on failure.
     */
    fun deleteAccount(onSuccess: () -> Unit, onFailure: (Exception?) -> Unit) {
        val user = getCurrentUser() ?: run {
            onFailure(Exception("User not logged in"))
            return
        }

        user.delete()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onSuccess()
                } else {
                    onFailure(task.exception)
                }
            }
    }

    /**
     * Signs out the currently authenticated user.
     */
    fun signOut() {
        if (::auth.isInitialized) {
            auth.signOut()
        }
    }
}
