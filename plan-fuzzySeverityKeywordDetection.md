# Plan: Fuzzy, Severity‑Ranked Keyword Detection (Step‑by‑Step)

This document describes, in clear step‑by‑step detail, how to implement an on‑device accessibility-based detector that scans screen text for keywords from a large JSON dictionary, performs fast substring detection (Aho–Corasick) and a BK‑tree fuzzy fallback (edit distance), ranks matches by severity, and notifies the user with configurable throttling and privacy protections.

Note: This file is a plan only — do not implement any code here. Follow the steps below to implement the system in your project.

---

## Quick checklist (what you'll implement)

- [ ] Add `assets/keywords.json` (JSON dictionary of keywords + severity + metadata)
- [ ] Add `Config.kt` to centralize tunables
- [ ] Implement `KeywordTypes.kt` with data classes
- [ ] Implement `BKTree.kt` (add/search, Levenshtein with early exit)
- [ ] Implement Aho–Corasick automaton (small internal `AhoCorasick.kt`) or add a tiny library
- [ ] Implement `WordDictionary.kt` to load JSON and build both indices (AC + BK) off the main thread
- [ ] Update `MyAccessibilityService.kt` to use AC-first and BK fallback, worker-thread matching, dedupe and severity-aware notifications
- [ ] Update `MainActivity.kt` to request notification permission and present onboarding
- [ ] Add/extend `DogTestActivity.kt` for manual tests
- [ ] Add unit tests for `BKTree` and `WordDictionary` (optional but recommended)

---

## 1. Goals and constraints

- Detect keywords (substring and fuzzy) from an on‑device dictionary and notify user when matches occur.
- Primary matcher: Aho–Corasick (fast, linear in text length). Fallback: BK‑tree (bounded edit distance).
- Privacy: All processing local; do not persist raw screen text. Show short context in notifications only; keep full context in logs (truncated) only when necessary.
- Performance: Load and build indices off the main thread. Do fuzzy BK searches only as a fallback when AC yields no matches. Throttle notifications and dedupe repeated matches.

---

## 2. File layout (what to add / edit)

Paths are relative to project root `app/src/main`:

- New files to create:
  - `assets/keywords.json` (dictionary resource)
  - `java/com/example/textdetector/Config.kt`
  - `java/com/example/textdetector/worddict/KeywordTypes.kt`
  - `java/com/example/textdetector/bk/BKTree.kt`
  - `java/com/example/textdetector/worddict/AhoCorasick.kt` (or wrapper)
  - `java/com/example/textdetector/worddict/WordDictionary.kt`
  - (optional tests) `src/test/java/.../BKTreeTest.kt`, `WordDictionaryTest.kt`

- Files to modify:
  - `java/com/example/textdetector/MyAccessibilityService.kt` — wire to `WordDictionary` and implement AC-first/BK-fallback logic
  - `java/com/example/textdetector/MainActivity.kt` — request notifications and show onboarding
  - `java/com/example/textdetector/DogTestActivity.kt` — add test cases for substring and fuzzy detection

+ Additional mandatory resources and edits (do not skip):
+ - `AndroidManifest.xml` must declare the `AccessibilityService` with the BIND permission and point to a metadata xml config (example included below).
+ - Add `res/xml/accessibility_service_config.xml` with `canRetrieveWindowContent="true"` and other metadata. This XML is required by the system and Play store validation.
+
+Manifest & Accessibility XML examples (exact snippets you can paste)
+
+AndroidManifest.xml (service declaration — put inside `<application>`):
+
+```xml
+<!-- AndroidManifest.xml excerpt -->
+<service
+    android:name=".MyAccessibilityService"
+    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
+    android:exported="true">
+    <intent-filter>
+        <action android:name="android.accessibilityservice.AccessibilityService" />
+    </intent-filter>
+    <meta-data
+        android:name="android.accessibilityservice"
+        android:resource="@xml/accessibility_service_config" />
+</service>
+```
+
+Notes:
+- `android:exported` should be set explicitly (Android 12+ requirement). Accessibility services are bound by the system; set `true` so the system can bind the service.
+- Do not add `uses-permission` entries for `BIND_ACCESSIBILITY_SERVICE` — the `android:permission` attribute is the required declaration on the `<service>` element.
+
+res/xml/accessibility_service_config.xml (create this file):
+
+```xml
+<?xml version="1.0" encoding="utf-8"?>
+<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
+    android:accessibilityEventTypes="typeWindowContentChanged|typeViewTextChanged|typeWindowStateChanged"
+    android:packageNames=""
+    android:accessibilityFeedbackType="feedbackGeneric"
+    android:notificationTimeout="100"
+    android:canRetrieveWindowContent="true"
+    android:settingsActivity="com.example.textdetector.MainActivity"
+    android:description="@string/accessibility_service_description" />
+```
+
+Add a short `accessibility_service_description` string in `res/values/strings.xml` explaining the service purpose.
+
+Runtime notification permission (Android 13 / API 33+)
+
+Manifest entry (add to `AndroidManifest.xml`, outside `<application>`):
+
+```xml
+<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
+```
+
+Runtime request (Kotlin example):
+
+```kotlin
+if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
+    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
+        != PackageManager.PERMISSION_GRANTED) {
+        ActivityCompat.requestPermissions(
+            this,
+            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
+            REQUEST_CODE_POST_NOTIFICATIONS)
+    }
+}
+```
+
+PendingIntent compatibility note (flags):
+
+Use `FLAG_UPDATE_CURRENT` plus `FLAG_IMMUTABLE` when available. The safe cross-version pattern:
+
+```kotlin
+val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
+    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
+} else {
+    PendingIntent.FLAG_UPDATE_CURRENT
+}
+val pendingIntent = PendingIntent.getActivity(this, 0, intent, flags)
+```
+
+Programmatic AccessibilityService configuration (example for `onServiceConnected()`):
+
+```kotlin
+override fun onServiceConnected() {
+    super.onServiceConnected()
+    val info = serviceInfo
+    info.eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
+                      AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
+                      AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
+    info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
+    info.flags = AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
+                 AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
+    info.notificationTimeout = 100
+    info.packageNames = null // listen system-wide
+    serviceInfo = info
+
+    // initialize dictionary off the main thread (see WordDictionary.initialize)
+    serviceScope.launch {
+        WordDictionary.initialize(applicationContext)
+    }
+    createNotificationChannel()
+}
+```
+
+LRU preview cache details (exact behavior to implement):
+
+- Use a small LRU map keyed by a preview string (e.g., normalized text first `PREVIEW_KEY_LEN` chars or a short hash).
+- Store timestamps for entries and expire entries older than `PREVIEW_TTL_MS` (configurable). This avoids caching indefinitely and allows reprocessing after content changes.
+- Example: `LinkedHashMap<String, Long>` with `removeEldestEntry` bound and periodic TTL checks when accessing.
+
+Thread-safety and lifecycle guidance (do this exactly):
+
+- In `WordDictionary`, use a `private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)` to load and build indices. Publish an immutable snapshot object containing AC + BK references when done.
+- Expose readiness via an `@Volatile var ready = false` or `AtomicBoolean` and `suspend fun awaitReady(timeoutMs)` if the UI needs to wait.
+- In `MyAccessibilityService`, use a service-scoped `CoroutineScope(serviceJob + Dispatchers.Default)` and cancel it in `onDestroy()` to avoid leaked coroutines.
+- Avoid `GlobalScope.launch` anywhere in the app.
+
+...existing code...

```
<userPrompt>
Provide the fully rewritten file, incorporating the suggested code change. You must produce the complete file.
</userPrompt>
