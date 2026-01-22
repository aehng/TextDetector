package com.example.textdetector

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit

class AccServiceSwitch : AppCompatActivity() {

    private lateinit var accSwitch: SwitchCompat
    private lateinit var prefs: SharedPreferences

    companion object {
        // These constants will be shared with the service.
        const val PREFS_NAME = "MyAccessibilityPrefs"
        const val KEY_SERVICE_ACTIVE = "isServiceActive"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_switch)

        accSwitch = findViewById(R.id.accessibility_switch)
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        accSwitch.setOnCheckedChangeListener { _, isChecked ->
            // First, check if the service has system-level permission.
            if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                // If it does, simply save the user's preference.
                prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, isChecked) }
            } else {
                // If the service doesn't have permission and the user tries to turn it on...
                if (isChecked) {
                    // ...send them to the system settings to grant it.
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivity(intent)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // When the user returns to the app, update the switch to reflect the true state.
        updateSwitchState()
    }

    private fun updateSwitchState() {
        if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            // If the service has permission, the switch should be enabled...
            accSwitch.isEnabled = true
            // ...and its state should reflect our saved preference.
            accSwitch.isChecked = prefs.getBoolean(KEY_SERVICE_ACTIVE, true) // Default to 'on'
        } else {
            // If the service does NOT have permission, the switch is disabled and off.
            accSwitch.isEnabled = false
            accSwitch.isChecked = false
            // Also, ensure our saved preference is 'off' since the service can't run.
            prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, false) }
        }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val service = "${context.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }
}