package com.example.stayaccountable

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.recyclerview.widget.RecyclerView

class AccServiceSwitch : AppCompatActivity() {

    private lateinit var accSwitch: SwitchCompat
    private lateinit var prefs: SharedPreferences

    companion object {
        // Shared preference constants
        const val PREFS_NAME = "MyAccessibilityPrefs"
        const val KEY_SERVICE_ACTIVE = "isServiceActive"
    }

    // List to hold events for the RecyclerView
    private val eventList = mutableListOf<Event>()
    private lateinit var adapter: EventAdapter
    private lateinit var dbHelper: EventDatabaseHelper

    // BroadcastReceiver to receive events from the AccessibilityService
    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val desc = intent?.getStringExtra("description") ?: return
            val sev = intent.getIntExtra("severity", 0)
            android.util.Log.d("AccServiceSwitch", "Received event: $desc, severity: $sev")
            // Insert event into database and update UI on main thread
            runOnUiThread {
                dbHelper.insertEvent(desc, sev)
                eventList.clear()
                eventList.addAll(dbHelper.getAllEvents())
                adapter.notifyDataSetChanged()
                android.util.Log.d("AccServiceSwitch", "Event list updated. Size: ${eventList.size}")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the event receiver to avoid leaks
        unregisterReceiver(eventReceiver)
    }

    @Suppress("UnspecifiedRegisterReceiverFlag") // Suppress lint warning for receiver flag on lower APIs
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_switch)

        // Initialize UI components and preferences
        accSwitch = findViewById(R.id.accessibility_switch)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        dbHelper = EventDatabaseHelper(this)

        // Load events from database
        eventList.addAll(dbHelper.getAllEvents())

        // Initialize the adapter right after the event list is ready
        adapter = EventAdapter(eventList)
        val recyclerView = findViewById<RecyclerView>(R.id.event_list)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)


        // Register the BroadcastReceiver for event updates
        val filter = IntentFilter("com.example.stayaccountable.EVENT_BROADCAST")
        // Use correct registerReceiver signature for each API level
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            // API 33+: Use receiver flag
            registerReceiver(eventReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // Below API 33: Use two-argument version (flag not available)
            registerReceiver(eventReceiver, filter)
        }

        // Handle switch toggling and permissions
        accSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                // Save the switch state if service is enabled
                prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, isChecked) }
            } else if (isChecked) {
                // If service is not enabled and switch is turned on, prompt user to enable it
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                startActivity(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Always reload the event list from the database when returning to the activity
        eventList.clear()
        eventList.addAll(dbHelper.getAllEvents())
        adapter.notifyDataSetChanged()
        // Update the switch state when returning to the activity
        updateSwitchState()
    }

    // Update the switch and preference state based on accessibility service status
    private fun updateSwitchState() {
        if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            accSwitch.isEnabled = true
            accSwitch.isChecked = prefs.getBoolean(KEY_SERVICE_ACTIVE, true) // Default to 'on'
        } else {
            accSwitch.isEnabled = false
            accSwitch.isChecked = false
            prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, false) }
        }
    }

    // Helper to check if the accessibility service is enabled
    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val service = "${context.packageName}/${serviceClass.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }
}