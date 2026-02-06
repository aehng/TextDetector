
// AccServiceSwitch.kt
// This activity provides the main user interface after login, showing event logs and a switch to control the accessibility service.

package com.example.stayaccountable

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AccServiceSwitch : AppCompatActivity() {

    // UI Components
    private lateinit var accSwitch: SwitchCompat
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: EventAdapter
    private lateinit var profileButton: Button

    // Data & Helpers
    private lateinit var prefs: SharedPreferences
    private val eventList = mutableListOf<Event>()
    private lateinit var dbHelper: EventDatabaseHelper
    private val firestore by lazy { Firebase.firestore }

    companion object {
        const val PREFS_NAME = "MyAccessibilityPrefs"
        const val KEY_SERVICE_ACTIVE = "isServiceActive"
    }

    private val eventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val desc = intent?.getStringExtra("description") ?: return
            val sev = intent.getIntExtra("severity", 0)
            Log.d("AccServiceSwitch", "Received event via broadcast: $desc, severity: $sev")
            runOnUiThread { refreshLogs() }
        }
    }

    @Suppress("UnspecifiedRegisterReceiverFlag")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_service_switch)

        // --- Initialization ---
        accSwitch = findViewById(R.id.accessibility_switch)
        recyclerView = findViewById(R.id.event_list)
        profileButton = findViewById(R.id.profileButton)
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        dbHelper = EventDatabaseHelper(this)

        adapter = EventAdapter(eventList)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        refreshLogs()

        // --- Event & Broadcast Handling ---
        val filter = IntentFilter("com.example.stayaccountable.EVENT_BROADCAST")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(eventReceiver, filter)
        }

        profileButton.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        accSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
                prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, isChecked) }
            } else if (isChecked) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
        LogSyncScheduler.scheduleSync(applicationContext)
        updateSwitchState()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(eventReceiver)
    }

    private fun updateSwitchState() {
        if (isAccessibilityServiceEnabled(this, MyAccessibilityService::class.java)) {
            accSwitch.isEnabled = true
            accSwitch.isChecked = prefs.getBoolean(KEY_SERVICE_ACTIVE, true)
        } else {
            accSwitch.isEnabled = false
            accSwitch.isChecked = false
            prefs.edit { putBoolean(KEY_SERVICE_ACTIVE, false) }
        }
    }

    private fun refreshLogs() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            eventList.clear()
            eventList.addAll(dbHelper.getAllEvents())
            adapter.notifyDataSetChanged()
            return
        }

        firestore.collection("users")
            .document(currentUser.uid)
            .collection("logs")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val remoteEvents = snapshot.documents.mapNotNull { doc ->
                    Event(
                        id = 0,
                        description = doc.getString("description") ?: return@mapNotNull null,
                        severity = (doc.getLong("severity") ?: 0L).toInt(),
                        timestamp = doc.getLong("timestamp") ?: 0L,
                        userId = currentUser.uid
                    )
                }

                val pendingEvents = dbHelper.getAllEvents(currentUser.uid)

                eventList.clear()
                eventList.addAll(pendingEvents)
                eventList.addAll(remoteEvents)
                adapter.notifyDataSetChanged()
            }
            .addOnFailureListener { error ->
                Log.e("AccServiceSwitch", "Failed to load logs from Firestore", error)
                eventList.clear()
                eventList.addAll(dbHelper.getAllEvents(currentUser.uid))
                adapter.notifyDataSetChanged()
            }
    }

    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServices = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
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
