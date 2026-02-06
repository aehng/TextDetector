// Generated with AI
// ProfileActivity.kt
// This activity allows the user to view and update their profile information.

package com.example.stayaccountable

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class ProfileActivity : AppCompatActivity() {

    private lateinit var emailTextView: TextView
    private lateinit var displayNameEditText: EditText
    private lateinit var saveProfileButton: Button
    private lateinit var logoutButton: Button
    private lateinit var deleteAccountButton: Button

    private val authRepo = AuthRepository()
    private lateinit var dbHelper: EventDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        // Initialize views
        emailTextView = findViewById(R.id.emailTextView)
        displayNameEditText = findViewById(R.id.displayNameEditText)
        saveProfileButton = findViewById(R.id.saveProfileButton)
        logoutButton = findViewById(R.id.logoutButton)
        deleteAccountButton = findViewById(R.id.deleteAccountButton)

        // Initialize AuthRepository and Database Helper
        authRepo.initAuth()
        dbHelper = EventDatabaseHelper(this)

        // Load current user data
        loadUserProfile()

        // Set listener for the save button
        saveProfileButton.setOnClickListener {
            val newDisplayName = displayNameEditText.text.toString().trim()
            if (newDisplayName.isNotEmpty()) {
                updateUserProfile(newDisplayName)
            } else {
                displayNameEditText.error = "Display name cannot be empty"
            }
        }

        // Set listener for the logout button
        logoutButton.setOnClickListener {
            handleLogout()
        }

        // Set listener for the delete account button
        deleteAccountButton.setOnClickListener {
            confirmAndDeleteAccount()
        }
    }

    /**
     * Loads the current user's profile data into the views.
     */
    private fun loadUserProfile() {
        val currentUser = authRepo.getCurrentUser()
        if (currentUser != null) {
            emailTextView.text = "Email: ${currentUser.email}"
            displayNameEditText.setText(currentUser.displayName)
        } else {
            // This should not happen if the user is logged in
            Toast.makeText(this, "Error: User not found.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Calls the repository to update the user's display name.
     */
    private fun updateUserProfile(displayName: String) {
        // Show a loading indicator (optional)

        authRepo.updateDisplayName(displayName,
            onSuccess = {
                runOnUiThread {
                    Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close the profile page
                }
            },
            onFailure = { exception ->
                runOnUiThread {
                    val message = exception?.localizedMessage ?: "Update failed."
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    /**
     * Handles user logout by signing out and returning to the main activity.
     */
    private fun handleLogout() {
        authRepo.signOut()
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    /**
     * Shows a confirmation dialog and deletes all user data and account.
     */
    private fun confirmAndDeleteAccount() {
        AlertDialog.Builder(this)
            .setTitle("Delete Account")
            .setMessage("Are you sure you want to delete your account? This will delete:\n\n" +
                    "• All your data from the cloud (Firestore)\n" +
                    "• All local data on this device\n" +
                    "• Your account permanently\n\n" +
                    "This action cannot be undone!")
            .setPositiveButton("Delete Everything") { _, _ ->
                deleteAllUserData()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Deletes all user data from Firestore, local database, and finally the user account.
     */
    private fun deleteAllUserData() {
        val currentUser = authRepo.getCurrentUser()
        if (currentUser == null) {
            Toast.makeText(this, "Error: No user logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid

        // Step 1: Delete Firestore data
        authRepo.deleteFirestoreData(
            onSuccess = {
                // Step 2: Delete local database data
                dbHelper.deleteEventsForUser(userId)

                // Step 3: Delete the user account
                authRepo.deleteAccount(
                    onSuccess = {
                        runOnUiThread {
                            Toast.makeText(this, "Account and all data deleted successfully", Toast.LENGTH_LONG).show()
                            val intent = Intent(this, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        }
                    },
                    onFailure = { exception ->
                        runOnUiThread {
                            val message = exception?.localizedMessage ?: "Failed to delete account."
                            Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            },
            onFailure = { exception ->
                runOnUiThread {
                    val message = exception?.localizedMessage ?: "Failed to delete cloud data."
                    Toast.makeText(this, "Error: $message", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}
