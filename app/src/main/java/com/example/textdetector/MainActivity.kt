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
        checkPermissions()
    }


    private fun setupUI() {
        val usernameEt = findViewById<android.widget.EditText>(R.id.etUsername)
        val passwordEt = findViewById<android.widget.EditText>(R.id.etPassword)
        val startBtn = findViewById<android.widget.Button>(R.id.btnStart)

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

            startActivity(Intent(this@MainActivity, DogTestActivity::class.java))
        }
    }




    private fun checkPermissions() {
        Log.d("PermissionCheck", "=== Checking Permissions ===")

        // Check notification permission
        val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not needed on older Android versions
        }
        Log.d("PermissionCheck", "✓ Notification permission: $hasNotificationPermission")

        // Check accessibility service
        val hasAccessibilityPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
        Log.d("PermissionCheck", "✓ Accessibility service: $hasAccessibilityPermission")

        // Request notification permission first if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasNotificationPermission) {
            Log.d("PermissionCheck", "→ Requesting notification permission...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // If notification permission is OK, check accessibility
            checkAccessibilityService()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            Log.d("PermissionCheck", "✓ Notification permission result: $granted")

            // After notification permission is handled, check accessibility
            checkAccessibilityService()
        }
    }

    private fun checkAccessibilityService() {
        val hasAccessibilityPermission = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)

        if (!hasAccessibilityPermission) {
            Log.d("PermissionCheck", "→ Prompting for accessibility service...")
            AlertDialog.Builder(this)
                .setTitle("Enable Accessibility Service")
                .setMessage("To detect the word 'dog' on screen, please enable the TextDetector accessibility service in your device settings.\n\nLook for 'TextDetector' in the list and toggle it ON.")
                .setPositiveButton("Open Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Later", null)
                .setCancelable(false)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Log current permission status when returning to the app
        val hasAccessibility = isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)
        Log.d("PermissionCheck", "onResume - Accessibility enabled: $hasAccessibility")
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