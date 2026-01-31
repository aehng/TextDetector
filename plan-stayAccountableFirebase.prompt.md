## Plan: StayAccountable Firebase Enhancements

Purpose: guide a beginner-friendly implementation that finishes Firebase Auth, near-real-time Firestore syncing (with offline queue, severity and heartbeat alerts), one-to-one pairing, data-retention transparency, and randomized heartbeat monitoring while running entirely on Firebase us-central free tier.

---

### Phase 0 – Prep & Alignment
- **0.1 Read current code** – skim `MainActivity.kt`, `AccServiceSwitch.kt`, `MyAccessibilityService.kt`, and `EventDatabaseHelper.kt` so you know where login, event logging, and RecyclerView display already live.
- **0.2 Document flows** – in `readme.md` (or a new `docs/architecture.md`), jot down the current data flow (user ➜ accessibility service ➜ SQLite ➜ RecyclerView). This helps when you start adding Firebase layers. Use the [app architecture guide](https://developer.android.com/topic/architecture) for vocabulary.
- **0.3 Create Firebase project** – log into the Firebase console, create a project in the **us-central** region, enable Analytics off (optional), and register the Android app (use your package name). Download `google-services.json` and place it inside `app/`.
- **0.4 Update Gradle** – in `app/build.gradle.kts`, add Firebase BoM, Auth, Firestore, WorkManager, Kotlin coroutines, and `androidx.security:security-crypto`. Run Gradle sync.

---

### Phase 1 – Firebase Auth + Encrypted Sessions
Files: `app/build.gradle.kts`, `AuthRepository.kt` (new), `FirebaseAuthRepository.kt` (new), `MainActivity.kt`, `readme.md`
1. **Add dependency + plugin** – apply `com.google.gms.google-services` in `build.gradle`, add Firebase BoM + `firebase-auth-ktx`, confirm `security-crypto` already present.
2. **Create repository interface** – new file `AuthRepository.kt` with methods `createAccount`, `login`, `logout`, `currentUser`, `deleteAccount`.
3. **Implement Firebase version** – `FirebaseAuthRepository.kt` wraps `FirebaseAuth` calls; on successful login, save UID + refresh token in `EncryptedSharedPreferences` (show code comments referencing [EncryptedSharedPreferences docs](https://developer.android.com/topic/security/data#use-encryptedsharedpreferences)).
4. **Wire MainActivity** – replace manual username/password checks with repository calls. Show progress indicator while awaiting Firebase results, handle errors with `Toast` or inline error labels, and auto-fill username using stored value.
5. **Add logout/delete UI** – simple buttons in `activity_service_switch.xml` or menu to call repository `logout()` / `deleteAccount()`.
6. **Docs to follow** – [Firebase email/password tutorial](https://firebase.google.com/docs/auth/android/password-auth).
7. **Acceptance** – user can create account, log in, close/reopen app within a session thanks to encrypted token storage, and log out without clearing stored credentials.

---

### Phase 2 – Cloud Event Sync + Severity Alerts
Files: `MyAccessibilityService.kt`, `EventDatabaseHelper.kt`, `Event.kt`, new `CloudEventRepository.kt`, new `SyncEventsWorker.kt`, `AndroidManifest.xml`
1. **Model changes** – extend `Event` data class with fields `id`, `synced`, `partnerDeliveredAt`.
2. **Add Firestore dependency** – already added in Phase 0; initialize `FirebaseFirestore` with settings enabling offline persistence.
3. **Create CloudEventRepository** – handles `enqueueEvent(event)` (write to SQLite + mark unsynced) and `syncPendingEvents()` (push to Firestore collection `events/{userId}/entries`). Use a batch write limited to <500 docs.
4. **Hook into accessibility service** – when `MyAccessibilityService` detects a word, call repository instead of directly inserting into DB. Keep local insert for UI speed.
5. **WorkManager sync** – implement `SyncEventsWorker` scheduled with `OneTimeWorkRequest` whenever a new event arrives and again when connectivity returns. For offline queueing guidance see [WorkManager + Room sync](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work#sync-db).
6. **Severity notifications** – inside repository or service, trigger `NotificationCompat` when `severity >= 7` using docs: [Android notifications](https://developer.android.com/develop/ui/views/notifications).
7. **Firestore TTL** – add a `delivered_at` timestamp field to every Firestore event document. Configure TTL policy in Firebase console to delete documents 48h after this timestamp. (Doc: [Firestore TTL](https://firebase.google.com/docs/firestore/ttl)).
8. **Acceptance** – events appear locally immediately, sync to cloud in <1 min, and severity ≥7 alerts show via in-app notification channel.

---

### Phase 3 – Randomized Heartbeat + Partner Alerts
Files: new `HeartbeatWorker.kt`, `AccServiceSwitch.kt`, `MyAccessibilityService.kt`, `FirebaseFirestore rules`
1. **Worker setup** – create `HeartbeatWorker` extending `CoroutineWorker`. In `doWork`, generate random delay 1–20 minutes using `Random.nextLong(1, 20)` and `ForemanWorkerBuilder.setInitialDelay()` for next run while keeping average ≈15. [Periodic & flex docs](https://developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work#periodic).
2. **Firestore schema** – store `heartbeats/{userId}` document with `lastPing`, `deviceInfo`, `status`.
3. **UI listener** – `AccServiceSwitch` (or new dashboard) listens to partner heartbeat doc via `addSnapshotListener`. If partner `lastPing` older than 20 minutes, push notification + highlight UI.
4. **Notifications** – reuse channel from severity alerts or create a dedicated heartbeat channel.
5. **Acceptance** – worker runs indefinitely with jitter, Firestore shows updated timestamp, partner sees alert when >20 minutes without heartbeat.

---

### Phase 4 – Companion Pairing (One-to-One, 30‑min Codes)
Files: new `PairingViewModel.kt`, `PairingActivity.kt` (or fragment), `pairing_layout.xml`, `FirebaseFirestore rules`, `readme.md`
1. **Schema** – Firestore collections:
   - `pairCodes/{code}` ➜ `{ ownerUid, createdAt (TTL), consumed: bool }` (TTL 30 minutes).
   - `pairings/{ownerUid}` ➜ `{ partnerUid, status, createdAt }`.
2. **Generate code** – from UI, call Cloud Function? (not needed). Use `FirebaseFirestore` to create doc with random 6-digit code, display to user, show countdown.
3. **Consume code** – second user enters code; query `pairCodes`, verify TTL, update `pairings` for both UIDs (mirror docs for easier reads).
4. **Security rules** – ensure only owner can create codes, only authenticated user can read their `pairings` doc, and events/heartbeats restricted to users whose UID matches either `ownerUid` or `partnerUid`.
5. **UI updates** – modify dashboards so each device primarily shows partner data. Provide “Swap partner” or “Unlink” in settings.
6. **Docs** – [Firestore add data](https://firebase.google.com/docs/firestore/manage-data/add-data) and [security rules](https://firebase.google.com/docs/firestore/security/get-started).
7. **Acceptance** – pairing code valid 30 minutes, both devices see partner events/heartbeats, attempts to view non-partner data are blocked by security rules.

---

### Phase 5 – Transparency & Accessibility Notices
Files: `MainActivity.kt`, new `TransparencyDialogFragment.kt`, `strings.xml`, `readme.md`
1. **Onboarding dialog** – on first launch (post-login), show dialog explaining: data stored locally, cloud copy deleted 48h after delivery, heartbeat pings keep partners informed, app uses Accessibility Service to read on-screen text. Provide “I Understand” checkbox stored in prefs.
2. **Accessibility disclosure** – update Play Store-ready README (or in-app “About” section) describing why the accessibility service runs and what data it captures. Reference [Accessibility Service docs](https://developer.android.com/guide/topics/ui/accessibility/service) and [Google Play data safety](https://support.google.com/googleplay/android-developer/answer/10787469).
3. **Settings toggle** – allow user to revisit the notice any time.
4. **Acceptance** – every user acknowledges notice once; documentation clearly states retention and accessibility usage.

---

### Phase 6 – Testing & Verification
1. **Unit tests** – use JUnit to cover `AuthRepository`, `CloudEventRepository`, and pairing logic.
2. **Instrumentation tests** – verify login flow, pairing UI, and RecyclerView updates on partner events.
3. **WorkManager tests** – use `TestWorkerBuilder` to simulate heartbeat schedule and ensure random intervals respect bounds.
4. **Manual QA checklist**:
   - Offline detection ➜ reconnect ➜ events sync to Firestore < 1 min.
   - Severity ≥7 ➜ immediate in-app notification.
   - Heartbeat failure (>20 min) ➜ notification and UI banner.
   - Pairing code expires after 30 minutes.
   - Firestore TTL removes events 48h after `delivered_at` while local DB keeps history.
   - Transparency dialog appears once and is accessible later.

---

### Open Questions / Follow-ups
1. **Partner data UI** – Should partner info show as list + push notifications (Option C) or only list? Need final answer before locking schema.
2. **Notification style** – prefer heads-up notifications for severity/heartbeat or standard notifications with custom sound?
3. **Future multi-partner** – when expanding, should we store `partnerIds: List<String>` now or refactor later once requirements solidify?
