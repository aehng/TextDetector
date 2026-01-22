package com.example.textdetector

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
import android.util.Log


class MainActivity : AppCompatActivity() {
    private val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
    }

    private fun setupUI() {
        val usernameEt = findViewById<android.widget.EditText>(R.id.usernameEditText)
        val passwordEt = findViewById<android.widget.EditText>(R.id.etPassword)
        val startBtn = findViewById<android.widget.Button>(R.id.startButton)


        startBtn.setOnClickListener {
            val username = usernameEt.text.toString().trim()
            val password = passwordEt.text.toString().trim()

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

            // Check all permissions after user clicks Start button
            // Add version check before calling checkPermissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPermissions { allPermissionsGranted ->
                    if (allPermissionsGranted) {
                        startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
                    }
                }
            } else {
                // For older versions, directly proceed
                startActivity(Intent(this@MainActivity, AccServiceSwitch::class.java))
            }
        }
    }

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
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("To detect words on screen, please enable the TextDetector accessibility service in your device settings.\n\nLook for 'TextDetector' in the list and toggle it ON.")
                .setIcon(R.drawable.settings_steps)
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Cancel") { _, _ ->
                    callback(false)
                }
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

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            // After notification permission is handled, check accessibility
            checkAccessibilityService()
        }
    }

    private fun checkAccessibilityService() {
        val hasAccessibilityPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)

        if (!hasAccessibilityPermission) {
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("To detect the word 'dog' on screen, please enable the TextDetector accessibility service in your device settings.\n\nLook for 'TextDetector' in the list and toggle it ON.")
                .setPositiveButton("Open Settings") { _, _ ->
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)

                    // Start a background thread to monitor accessibility service status
                    Thread {
                        while (!isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                            Thread.sleep(1000) // Check every second
                        }

                        // Once enabled, bring the user back to the app
                        runOnUiThread {
                            try {
                                val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                                if (launchIntent != null) {
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    startActivity(launchIntent)
                                }
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Error occurred", e)
                            }
                        }
                    }.start()
                }
                .setNegativeButton("Later") { _, _ ->
                }
                .setCancelable(false)
                .show()
        }
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