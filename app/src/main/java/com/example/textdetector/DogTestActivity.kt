package com.example.textdetector

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class DogTestActivity : AppCompatActivity() {
    private var testIndex = 0
    private val testTexts = arrayOf(
        "dog",
        "I have a dog",
        "DOG",
        "The quick brown dog jumps",
        "No animals here",
        "Doghouse",
        "Hot dog",
        "dog cat bird"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dog_test)

        Log.d("DogTestActivity", "Test activity created")

        val textView = findViewById<TextView>(R.id.dogTestTextView)
        val nextButton = findViewById<Button>(R.id.nextTestButton)
        val resetButton = findViewById<Button>(R.id.resetTestButton)

        // Set initial text
        textView.text = testTexts[testIndex]
        textView.contentDescription = textView.text
        // Make view accessible and focusable to improve event delivery
        textView.isFocusable = true
        textView.isFocusableInTouchMode = true
        textView.importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
        textView.requestFocus()
        // Announce initial text to accessibility
        textView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        window.decorView.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
        Log.d("DogTestActivity", "Initial text: ${testTexts[testIndex]}")

        nextButton.setOnClickListener {
            testIndex = (testIndex + 1) % testTexts.size
            textView.text = testTexts[testIndex]
            textView.contentDescription = textView.text
            // Force accessibility event for testing
            textView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
            // Announce explicitly to trigger accessibility services reliably
            val event = AccessibilityEvent.obtain()
            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            event.text.add(textView.text)
            textView.parent.requestSendAccessibilityEvent(textView, event)
            Log.d("DogTestActivity", "Changed text to: ${testTexts[testIndex]}")
        }

        resetButton.setOnClickListener {
            testIndex = 0
            textView.text = testTexts[testIndex]
            textView.contentDescription = textView.text
            textView.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED)
            val event = AccessibilityEvent.obtain()
            event.eventType = AccessibilityEvent.TYPE_ANNOUNCEMENT
            event.text.add(textView.text)
            textView.parent.requestSendAccessibilityEvent(textView, event)
            Log.d("DogTestActivity", "Reset to: ${testTexts[testIndex]}")
        }
    }
}
