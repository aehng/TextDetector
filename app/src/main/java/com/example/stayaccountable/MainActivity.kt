// MainActivity.kt
// The entry point for the StayAccountable app. It manages user login/registration and the permission request flow.

package com.example.stayaccountable

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import android.Manifest
import android.provider.Settings
import android.app.AlertDialog
import android.content.ComponentName
import android.text.TextUtils
import android.content.Context
import android.content.Intent
import android.accessibilityservice.AccessibilityService
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/**
 * The main entry point of the application.
 *
 * This activity is responsible for:
 * 1. Handling user authentication (sign-in and account creation) via Firebase.
 * 2. Managing the UI for the authentication form.
 * 3. Requesting necessary permissions (e.g., Notifications and Accessibility Service) after successful authentication.
 * 4. Navigating the user to the [AccServiceSwitch] activity upon successful login and permission grants.
 */
class MainActivity : AppCompatActivity() {
    // Request code for the notification permission request.
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    // Repository for handling authentication logic with Firebase.
    private val authRepo = AuthRepository()

    // A flag to toggle between Sign-In and Create Account UI modes.
    private var isSignInMode = false

    /**
     * Called when the activity is first created.
     * Initializes the view, sets up edge-to-edge display, and configures the authentication UI.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepo.initAuth() // Initialize Firebase Auth
        enableEdgeToEdge()    // Enable edge-to-edge display for a modern look
        setContentView(R.layout.activity_main)

        // Adjust padding to accommodate system bars (status bar, navigation bar).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set up the interactive UI elements.
        setupUI()
    }

    /**
     * Configures the user interface for authentication.
     * It sets up listeners for the sign-in/create-account button and the mode-toggle text.
     */
    private fun setupUI() {
        val usernameEt = findViewById<EditText>(R.id.usernameEditText)
        val passwordEt = findViewById<EditText>(R.id.etPassword)
        val startBtn = findViewById<Button>(R.id.startButton)
        val toggleAuthModeText = findViewById<TextView>(R.id.toggleAuthModeText)

        // Initialize the UI to the default auth mode (Create Account).
        updateAuthModeUI(startBtn, toggleAuthModeText)

        // Listener to switch between sign-in and create account modes.
        toggleAuthModeText.setOnClickListener {
            isSignInMode = !isSignInMode
            updateAuthModeUI(startBtn, toggleAuthModeText)
        }

        // Main action button for authentication.
        startBtn.setOnClickListener {
            val username = usernameEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            // --- Input Validation ---
            var valid = true
            if (username.isEmpty()) {
                usernameEt.error = "Username required"
                valid = false
            } else {
                usernameEt.error = null
            }

            if (password.isEmpty()) {
                passwordEt.error = "Password required"
                valid = false
            } else {
                passwordEt.error = null
            }

            if (!valid) return@setOnClickListener

            // --- Firebase Authentication Logic ---
            val onAuthSuccess = { ->
                runOnUiThread {
                    // After successful authentication, proceed to permission checks.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        checkPermissions { allPermissionsGranted ->
                            if (allPermissionsGranted) {
                                startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                            }
                        }
                    } else {
                        // For older Android versions, skip notification permission check.
                        startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                    }
                }
            }

            val onAuthFailure = { exception: Exception? ->
                runOnUiThread {
                    val message = exception?.localizedMessage ?: if (isSignInMode) "Sign in failed." else "Account creation failed."
                    android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                }
            }

            // Execute either sign-in or account creation based on the current mode.
            if (isSignInMode) {
                authRepo.signIn(username, password, onAuthSuccess, onAuthFailure)
            } else {
                authRepo.createAccount(username, password, onAuthSuccess, onAuthFailure)
            }
        }
    }

    /**
     * Updates the UI text of the main button and the toggle text based on the current authentication mode.
     */
    private fun updateAuthModeUI(startBtn: Button, toggleText: TextView) {
        if (isSignInMode) {
            startBtn.setText(R.string.sign_in)
            toggleText.setText(R.string.toggle_to_create_account)
        } else {
            startBtn.setText(R.string.create_account)
            toggleText.setText(R.string.toggle_to_sign_in)
        }
    }

    /**
     * Checks for required application permissions.
     * This includes Notification permissions on Android 13+ and Accessibility Service enablement.
     * @param callback A function to be called with the result of whether all permissions were granted.
     */
    private fun checkPermissions(callback: (Boolean) -> Unit) {
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions.
        }

        val hasAccessibilityPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)

        // Request notification permission if not granted.
        if (!hasNotificationPermission) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
        }

        // Prompt user to enable accessibility service if it's not already enabled.
        if (!hasAccessibilityPermission) {
            showAccessibilityDialog { granted -> callback(granted && hasNotificationPermission) }
        } else {
            callback(hasNotificationPermission)
        }
    }

    /**
     * Displays a dialog to instruct the user to enable the Accessibility Service.
     */
    private fun showAccessibilityDialog(onDismissCallback: (Boolean) -> Unit) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_image, null)
        dialogView.findViewById<TextView>(R.id.dialogMessage).text = getString(R.string.accessibility_service_instructions)

        AlertDialog.Builder(this)
            .setTitle("Enable Accessibility Service")
            .setView(dialogView)
            .setPositiveButton("Open Settings") { _, _ ->
                // Opens the system's accessibility settings screen.
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .setOnDismissListener {
                // After the user returns from settings, re-check if the service was enabled.
                val recheckedPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
                onDismissCallback(recheckedPermission)
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Callback for the result from requesting permissions.
     */
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // The primary permission handling logic is managed within the `checkPermissions` and `showAccessibilityDialog` flows.
    }

    /**
     * Checks if the specified accessibility service is enabled in the system settings.
     *
     * @param context The application context.
     * @param service The accessibility service class to check.
     * @return `true` if the service is enabled, `false` otherwise.
     */
    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        if (enabledServices.isNullOrEmpty()) return false

        // The system stores the list of enabled services as a colon-separated string.
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentName = ComponentName.unflattenFromString(colonSplitter.next())
            if (componentName != null && componentName == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
