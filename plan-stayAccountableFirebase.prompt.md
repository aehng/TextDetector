## Unified Plan: Secure Local Auth → Firebase Sync & Companion Features

Purpose: provide a single, beginner-friendly roadmap that starts with secure local authentication using EncryptedSharedPreferences, then layers in Firebase Auth, Firestore event syncing (<1 min latency), randomized heartbeat monitoring, one-to-one pairing, data retention (48 h TTL), and onboarding transparency. Follow phases in order; each lists files to touch, references, and acceptance criteria.

---

### Phase 0 – Project Setup & Orientation
1. **Review existing code** – skim `MainActivity.kt`, `AccServiceSwitch.kt`, `MyAccessibilityService.kt`, and `EventDatabaseHelper.kt` to understand current login UI, accessibility logging, and RecyclerView display.
2. **Document baseline flow** – in `readme.md` (or new `docs/architecture.md`), draw the current data path: *user ➜ accessibility service ➜ SQLite ➜ UI*. Use [Android app architecture guidance](https://developer.android.com/topic/architecture) for terminology.
3. **Create Firebase project** –
   - Go to https://console.firebase.google.com and click **Add project**.
   - Choose a name (e.g., “stay-accountable-dev”), disable Google Analytics to stay on free tier, and confirm the region defaults to **us-central (Iowa)**.
   - After the project finishes provisioning, click the Android icon to **Add Firebase to your Android app**.
   - Enter the exact package name from `app/src/main/AndroidManifest.xml` (e.g., `com.example.stayaccountable`), optionally set an app nickname, leave SHA-1 blank for now, and click **Register app**.
   - Download the generated `google-services.json` file when prompted.
   - In Android Studio, drag the JSON into the `app/` module root (same folder as `build.gradle.kts`). If prompted, choose **Refactor > Move** to keep Gradle aware.
   - Back in the wizard, click **Next** to see Gradle snippets; you’ll add those in Step 4.
   - Verify the file exists at `app/google-services.json` and is tracked/ignored per `.gitignore` rules (keep it out of source control unless required).
4. **Update Gradle** –
   - Root `build.gradle.kts`: add `classpath("com.google.gms:google-services:4.4.2")` inside `dependencies` if missing.
   - Module `app/build.gradle.kts`:
     ```kotlin
     plugins {
         id("com.google.gms.google-services")
     }
     dependencies {
         implementation(platform("com.google.firebase:firebase-bom:33.4.0"))
         implementation("com.google.firebase:firebase-auth-ktx")
         implementation("com.google.firebase:firebase-firestore-ktx")
         implementation("androidx.work:work-runtime-ktx:2.9.1")
         implementation("androidx.security:security-crypto:1.1.0-alpha06")
         implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
     }
     ```
   - Sync Gradle (Android Studio toolbar). Fix any version conflicts before moving on.

---

### Phase 1 – Secure Local Auth with EncryptedSharedPreferences
Files: `app/build.gradle.kts`, `AuthRepository.kt`, `MainActivity.kt`
1. **Dependency check** – confirm `security-crypto` line exists after the Gradle sync. If not, add it and resync.
2. **Create `AuthRepository`** –
   - Location: `app/src/main/java/com/example/stayaccountable/AuthRepository.kt`.
   - Constructor should take `applicationContext` to avoid leaking an Activity.
   - Wrap the `EncryptedSharedPreferences.create` call in `try/catch (ex: GeneralSecurityException)` and log/return false on failure.
3. **Password hashing helper** – keep helper private to the repository or in `AuthUtils.kt`; run hashing on background thread if you later support very long passwords.
4. **Account creation flow** – update `MainActivity` to:
   - Detect mode: if `repository.getSavedUsername() == null`, show a “Create account” banner/button.
   - `createAccount()` should refuse blank input, mismatched confirm password, and duplicates; show errors via `TextInputLayout.setError`.
   - After success, switch UI to login mode automatically.
5. **Login flow** –
   - Pre-fill username field, request focus on password box.
   - On submit, disable button, call repository, then re-enable to prevent double taps.
   - Navigate to `AccServiceSwitch` on success.
6. **Logout flow** – add `logoutButton` in `activity_service_switch.xml` that calls `repository.logout()` and `startActivity(Intent(this, MainActivity::class.java))`; also `finish()` to clear back stack.
7. **Delete account (optional)** – confirm via dialog and call `repository.deleteAccount()`, then return to create-account mode.
8. **Acceptance** – verify by running the app: create account ➜ close app ➜ reopen ➜ login works without re-entering username.

---

### Phase 2 – Interface Abstraction & Firebase Auth Migration
Files: `IAuthRepository.kt`, `FirebaseAuthRepository.kt`, `MainActivity.kt`
1. **Define interface** – place `IAuthRepository.kt` alongside `AuthRepository`. Include coroutine `suspend` signatures for network-bound methods; document each method.
2. **Adapt local repo** – implement `IAuthRepository`; for synchronous local logic, wrap return types or mark methods `override suspend` even if immediate.
3. **Implement `FirebaseAuthRepository`** –
   - Store a reference to `FirebaseAuth`.
   - Use `Tasks.await()` (KTX `await()`) within `Dispatchers.IO` to avoid blocking main thread.
   - Cache fields like `uid`, `email`, `idToken` in `EncryptedSharedPreferences` so the UI can show who is logged in.
4. **Repository selector** – create `object RepositoryProvider` with a function that checks a `BuildConfig` flag or `SharedPreferences` toggle (e.g., devs can switch via hidden setting).
5. **Update UI** – in `MainActivity`, inject repository via provider; wrap button actions in `lifecycleScope.launch {}`; show `ProgressBar` while awaiting `login()`/`createAccount()`.
6. **Docs** – follow Firebase quickstart to enable email/password in Firebase console (**Build ➜ Authentication ➜ Sign-in method** ➜ enable Email/Password).
7. **Acceptance** – toggle provider between local/Firebase and confirm UI logic does not change.

---

### Phase 3 – Cloud Event Sync with Offline Queue & Severity Notifications
Files: `Event.kt`, `EventDatabaseHelper.kt`, `MyAccessibilityService.kt`, `CloudEventRepository.kt` (new), `SyncEventsWorker.kt` (new), `AndroidManifest.xml`
1. **Data model update** – update `Event.kt` and `EventDatabaseHelper.kt` schema; run a destructive migration during development if easier (clear data once).
2. **Local DB changes** – bump `DB_VERSION` and implement `onUpgrade` with SQL `ALTER TABLE` or `DROP TABLE + onCreate` as appropriate.
3. **Create `CloudEventRepository`** – detail `enqueueEvent`, `syncPendingEvents`, `markDelivered` methods. Use `FirebaseFirestore.getInstance().enableNetwork()` defaults (persistence on by default).
4. **WorkManager integration** – add `SyncEventsWorker.enqueue()` helper that builds a `OneTimeWorkRequest` with `setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)` so urgent syncs run quickly.
5. **Severity notifications** – ensure channel importance is `IMPORTANCE_HIGH`; include action button to open logs screen.
6. **Offline behavior** – rely on local SQLite queue; also let Firestore cache writes but treat SQLite as source of truth for purge timestamps.
7. **Acceptance** – simulate by disabling Wi-Fi, triggering detection, re-enabling Wi-Fi, and observing Firestore console receiving the event within a minute.

---

### Phase 4 – Randomized Heartbeat & Offline Detection
Files: `HeartbeatWorker.kt` (new), `RepositoryProvider`, `AccServiceSwitch.kt`, Firestore rules
1. **Worker design** – create `HeartbeatWorker.kt`; store `uid` via `inputData`. Use `Random.nextInt(1, 21)` and document the distribution.
2. **Initialization** – after successful login, enqueue initial heartbeat worker (no delay). On logout, call `WorkManager.getInstance(context).cancelUniqueWork("heartbeat")`.
3. **Listener** – `AccServiceSwitch` uses `FirebaseFirestore` snapshot listener on `heartbeats/{partnerUid}`; show `TextView` “Partner offline for X minutes” if stale.
4. **Notifications** – create channel `HEARTBEAT_STATUS`; use `setTimeoutAfter()` so stale alerts auto-dismiss once resolved.
5. **Acceptance** – throttle device heartbeat by turning on airplane mode and confirm partner device receives offline alert after 20 minutes (or simulate by manually editing timestamp in Firestore).

---

### Phase 5 – Companion Pairing (One-to-One, 30-Minute Codes)
Files: `PairingActivity.kt`, `PairingViewModel.kt`, `pairing_layout.xml`, Firestore rules, `readme.md`
1. **Schema** – in Firestore console, enable **Firestore in Native mode**. Create indexes if queries require `ownerUid` + `expiresAt`. Set TTL on `pairCodes.expiresAt` via console (**Firestore Database ➜ TTL**).
2. **Generate code UI** – build `PairingActivity` with a “Generate Code” button; when tapped, call repository method that writes a doc:
   ```kotlin
   val code = (100000..999999).random().toString()
   firestore.collection("pairCodes").document(code).set(
       mapOf(
           "ownerUid" to uid,
           "createdAt" to FieldValue.serverTimestamp(),
           "expiresAt" to Timestamp.now().toDate().also { it.time += 30*60*1000 }
       )
   )
   ```
   Display the code and a countdown timer (use `CountDownTimer`).
3. **Consume code** – on entry, run Firestore transaction: ensure doc exists, not expired, and `partnerUid` null, then write `pairings` docs for both users and delete the code.
4. **Security rules** – update `firestore.rules` so `match /events/{userId}/entries/{eventId}` allows read if `request.auth.uid == userId || request.auth.uid == resource.data.partnerUid`. Use custom function that reads `pairings` to verify relationship.
5. **UI updates** – `AccServiceSwitch` should display partner name/UID and allow unlink via menu.
6. **Acceptance** – two emulator accounts link via code; partner feed shows remote events; entering expired code yields friendly error.

---

### Phase 6 – Data Retention & Transparency
Files: `MainActivity.kt`, `TransparencyDialogFragment.kt`, `strings.xml`, `readme.md`, Firestore console
1. **Firestore TTL** – in console, add TTL policy on `events/{uid}/entries` for field `delivered_at`. Document steps: **Firestore Database ➜ TTL ➜ Add policy ➜ Select collection** ➜ enter field name ➜ save.
2. **Delivery acknowledgment** – when partner fetches events, update doc with `delivered_at = FieldValue.serverTimestamp()` using batched writes; schedule follow-up worker if writes fail.
3. **Transparency dialog** – create `TransparencyDialogFragment` with bullet list; show after login if `prefs.getBoolean("acknowledged_transparency", false)` is false. Provide “I Understand” checkbox to enable OK button.
4. **Documentation** – add a `docs/privacy.md` summarizing retention rules, heartbeat purpose, and Accessibility Service scope; link it from README.
5. **Acceptance** – confirm TTL is deleting documents after 48h (check Firestore `statistics` tab) and users can re-open the dialog from settings.

---

### Phase 7 – Testing & Verification
1. **Unit tests** – use Robolectric/JUnit for repository logic; mock Firebase via emulator or fake implementations. Example command: `./gradlew testDebug`.
2. **Instrumentation tests** – add Espresso tests under `app/src/androidTest`. Use Firebase emulators during CI to avoid hitting production.
3. **WorkManager tests** – leverage `TestDriver` to trigger workers instantly while asserting delay bounds.
4. **Manual QA checklist** – convert bullet list into a shareable Google Doc or markdown checklist for QA teammates.

---

### Quick Checklists & Code Examples
#### Phase 0 – Setup
- [ ] Read `MainActivity.kt`, `AccServiceSwitch.kt`, `MyAccessibilityService.kt`, `EventDatabaseHelper.kt`.
- [ ] Create Firebase project (us-central) and add `google-services.json` to `app/`.
- [ ] Apply `com.google.gms.google-services` plugin and add Firebase + WorkManager + security dependencies, then sync Gradle.

#### Phase 1 – Local Auth
- [ ] Add `security-crypto` dependency if missing.
- [ ] Implement `AuthRepository` with hashing + EncryptedSharedPreferences.
- [ ] Hook `MainActivity` login/create flows and add logout/delete buttons.
```kotlin
class AuthRepository(context: Context) : IAuthRepository {
    private val prefs = EncryptedSharedPreferences.create(
        "auth_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context.applicationContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    override fun createAccount(username: String, password: String) =
        prefs.edit().putString("username", username).putString("hash", hash(password)).commit()
    override fun login(username: String, password: String) =
        prefs.getString("username", null) == username && prefs.getString("hash", null) == hash(password)
    // ...logout, deleteAccount, helpers...
}
```

#### Phase 2 – Firebase Auth
- [ ] Create `IAuthRepository` interface and `RepositoryProvider`.
- [ ] Implement `FirebaseAuthRepository` using `FirebaseAuth` + EncryptedSharedPreferences cache.
- [ ] Update `MainActivity` to call repository methods in coroutines and show loading/errors.
```kotlin
class FirebaseAuthRepository(private val context: Context) : IAuthRepository {
    private val auth = FirebaseAuth.getInstance()
    override suspend fun login(username: String, password: String): Boolean =
        auth.signInWithEmailAndPassword(username, password).await().user != null
    override fun logout() {
        auth.signOut()
        encryptedPrefs.edit().clear().apply()
    }
    // ...createAccount, deleteAccount, currentUser...
}
```

#### Phase 3 – Event Sync & Alerts
- [ ] Extend `Event` + SQLite schema with `uuid`, `synced`, `partnerDeliveredAt`.
- [ ] Build `CloudEventRepository` + `SyncEventsWorker` for <1 min Firestore uploads.
- [ ] Trigger `NotificationCompat` when `severity >= 7`.
```kotlin
class CloudEventRepository(
    private val db: EventDatabaseHelper,
    private val firestore: FirebaseFirestore,
    private val uid: String
) {
    fun enqueueEvent(word: String, severity: Int) {
        db.insertEvent(word, severity, synced = false)
        SyncEventsWorker.enqueue()
    }
    suspend fun syncPendingEvents() {
        val pending = db.getPendingEvents()
        val batch = firestore.batch()
        pending.forEach { event ->
            val ref = firestore.collection("events").document(uid).collection("entries").document(event.uuid)
            batch.set(ref, event.toMap())
        }
        batch.commit().await()
        db.markSynced(pending.map { it.uuid })
    }
}
```

#### Phase 4 – Heartbeat
- [ ] Create `HeartbeatWorker` that writes `heartbeats/{uid}` and schedules next run with random 1–20 min delay.
- [ ] Start worker on login, cancel on logout.
- [ ] Listen to partner heartbeat and notify if >20 min stale.
```kotlin
class HeartbeatWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        firestore.collection("heartbeats").document(uid)
            .set(mapOf("lastPing" to FieldValue.serverTimestamp(), "device" to Build.MODEL))
        val delayMinutes = (1..20).random().toLong()
        OneTimeWorkRequestBuilder<HeartbeatWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build().also { WorkManager.getInstance(applicationContext).enqueue(it) }
        return Result.success()
    }
}
```

#### Phase 5 – Pairing
- [ ] Create `pairCodes` + `pairings` collections with TTL on `pairCodes.expiresAt`.
- [ ] Build UI to generate 6-digit code and to enter/consume a code.
- [ ] Update Firestore security rules so only partners can read each other’s data.
```kotlin
suspend fun consumePairCode(code: String) {
    firestore.runTransaction { txn ->
        val codeRef = firestore.collection("pairCodes").document(code)
        val snapshot = txn.get()
        require(snapshot.exists()) { "Invalid code" }
        val ownerUid = snapshot.getString("ownerUid")!!
        txn.set(firestore.collection("pairings").document(ownerUid), mapOf("partnerUid" to uid))
        txn.set(firestore.collection("pairings").document(uid), mapOf("partnerUid" to ownerUid))
        txn.delete(codeRef)
    }
}
```

#### Phase 6 – Retention & Transparency
- [ ] Enable Firestore TTL on `events/{uid}/entries.delivered_at` (48 h) and on `pairCodes.expiresAt` (30 min).
- [ ] Mark `delivered_at` once partner fetches events.
- [ ] Show onboarding dialog explaining accessibility usage + retention policy; store acknowledgment.

#### Phase 7 – Testing
- [ ] Write unit tests for repositories (local + Firebase + pairing).
- [ ] Add Espresso tests for login/pairing/partner feed.
- [ ] Use `TestWorkerBuilder` to validate heartbeat and sync workers.

---

### Open Questions
1. **Partner UI scope** – should the dashboard show both partner’s feed and push-style alerts (Option C) or only list view? Decide before finalizing adapter logic.
2. **Notification style** – prefer heads-up notifications (high priority) for severity/heartbeat or standard notifications with custom sounds/vibration?
3. **Future multi-partner** – when expanding, will we store `partnerIds: List<String>` or create a dedicated `groups` collection? Plan now to avoid migration pain later.
