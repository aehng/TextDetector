// MainActivity.kt
// Entry point for StayAccountable app. Handles login, permission checks, and navigation to the accessibility service switch screen.

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
import android.widget.TextView


// MainActivity: Handles user login and permission flow
class MainActivity : AppCompatActivity() {
    // Request code for notification permission
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    private val authRepo = AuthRepository()

    private var isSignInMode = false

    // Called when the activity is created
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        authRepo.initAuth()
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // Set up system bar insets for modern look
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }



    // Sets up the login UI and handles Start button logic
    private fun setupUI() {
        val usernameEt = findViewById<android.widget.EditText>(R.id.usernameEditText)
        val passwordEt = findViewById<android.widget.EditText>(R.id.etPassword)
        val startBtn = findViewById<android.widget.Button>(R.id.startButton)
        val toggleAuthModeText = findViewById<android.widget.TextView>(R.id.toggleAuthModeText)

        updateAuthModeUI(startBtn, toggleAuthModeText)

        toggleAuthModeText.setOnClickListener {
            isSignInMode = !isSignInMode
            updateAuthModeUI(startBtn, toggleAuthModeText)
        }

        // Handle Start button click
        startBtn.setOnClickListener {
            val username = usernameEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

            var valid = true
            // Validate username
            if (username.isEmpty()) {
                usernameEt.error = "Username required"
                valid = false
            } else {
                usernameEt.error = null
            }

            // Validate password
            if (password.isEmpty()) {
                passwordEt.error = "Password required"
                valid = false
            } else {
                passwordEt.error = null
            }

            // If not valid, do not proceed
            if (!valid) return@setOnClickListener

            // Attempt Firebase Auth sign-in
            // Optionally, you can show a loading indicator here

            authRepo.initAuth() // Ensure auth is initialized (safe to call multiple times)
            if (isSignInMode) {
                authRepo.signIn(
                    email = username,
                    password = password,
                    onSuccess = {
                        // On successful sign-in, check permissions and proceed
                        runOnUiThread {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                checkPermissions { allPermissionsGranted ->
                                    if (allPermissionsGranted) {
                                        startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                                    }
                                }
                            } else {
                                startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                            }
                        }
                    },
                    onFailure = { exception ->
                        // Show error to user
                        runOnUiThread {
                            val message = exception?.localizedMessage ?: "Sign in failed."
                            android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
            } else {
                authRepo.createAccount(
                    email = username,
                    password = password,
                    onSuccess = {
                        // On successful account creation, check permissions and proceed
                        runOnUiThread {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                checkPermissions { allPermissionsGranted ->
                                    if (allPermissionsGranted) {
                                        startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                                    }
                                }
                            } else {
                                startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                            }
                        }
                    },
                    onFailure = { exception ->
                        // Show error to user
                        runOnUiThread {
                            val message = exception?.localizedMessage ?: "Account creation failed."
                            android.widget.Toast.makeText(this@MainActivity, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }

    private fun updateAuthModeUI(startBtn: Button, toggleText: TextView) {
        if (isSignInMode) {
            startBtn.setText(R.string.sign_in)
            toggleText.setText(R.string.toggle_to_create_account)
        } else {
            startBtn.setText(R.string.create_account)
            toggleText.setText(R.string.toggle_to_sign_in)
        }
    }

    // Checks for required permissions (e.g., notifications)
    private fun checkPermissions(callback: (Boolean) -> Unit) {
        // Check notification permission
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android versions
        }

        // Check accessibility service
        val hasAccessibilityPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)

        if (!hasNotificationPermission) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }

        if (!hasAccessibilityPermission) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_accessibility_image, null)
            val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
            messageView.text = getString(R.string.accessibility_service_instructions)

            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setView(dialogView)
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel") { _, _ -> }
                .setOnDismissListener {
                    // Check again after the dialog is dismissed
                    val recheckedPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
                    callback(recheckedPermission && hasNotificationPermission)
                }
                .setCancelable(false)
                .show()
        } else {
            callback(hasNotificationPermission)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // No additional logic needed here; permission handling is done in checkPermissions.
    }


    private fun isAccessibilityServiceEnabled(context: Context, service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(context, service)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )

        if (enabledServices.isNullOrEmpty()) return false

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