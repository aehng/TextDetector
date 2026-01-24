## Detailed Plan: Secure Local Auth with EncryptedSharedPreferences (Firebase-Ready)

This plan is designed for students learning Android Studio. Each step includes explanations, common errors, and tips for robust implementation.

### 1. Add EncryptedSharedPreferences Dependency
- Open your project’s `app/build.gradle` file.
- Add to the `dependencies` block:
  ```gradle
  implementation "androidx.security:security-crypto:1.1.0-alpha06"
  ```
- **Why:** This library provides EncryptedSharedPreferences, which encrypts your data using the Android Keystore.
- **Common Errors:**
  - Forgetting to sync Gradle after editing `build.gradle` (always sync!).
  - Typos in the dependency string.

### 2. Create an AuthRepository
- Create a new Kotlin file, e.g., `AuthRepository.kt`.
- Define a class `AuthRepository` to handle all authentication logic (account creation, login, logout, session check).
- Use EncryptedSharedPreferences inside this class.
- **Why:** Keeps authentication logic separate from UI, making it easier to maintain and swap for Firebase later.
- **Example Skeleton:**
  ```kotlin
  class AuthRepository(context: Context) {
      private val prefs = EncryptedSharedPreferences.create(
          "auth_prefs",
          MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
          context,
          EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
          EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
      )
      fun createAccount(username: String, password: String): Boolean { /* ... */ }
      fun login(username: String, password: String): Boolean { /* ... */ }
      fun isLoggedIn(): Boolean { /* ... */ }
      fun logout() { /* ... */ }
      fun getSavedUsername(): String? { /* ... */ }
  }
  ```
- **Common Errors:**
  - Not using application context (use `applicationContext` if you get a memory leak warning).
  - Not handling exceptions from EncryptedSharedPreferences (wrap in try/catch).

### 3. Account Creation Flow
- In your login activity, check if credentials exist in the repository.
- If not, show a “Create Account” screen (or mode).
- When the user enters a username and password, hash the password (see below), and store both in EncryptedSharedPreferences via the repository.
- **Why:** You need a way to set up the first account before login is possible.
- **Password Hashing Example:**
  ```kotlin
  fun hashPassword(password: String): String {
      val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
      return bytes.joinToString("") { "%02x".format(it) }
  }
  ```
- **Common Errors:**
  - Storing the password in plain text (never do this).
  - Not checking for empty username/password fields.
  - Not handling duplicate account creation (should only allow one account for now).

### 4. Login Flow
- If credentials exist, show the login screen.
- Pre-fill the username field using `getSavedUsername()` from the repository.
- When the user enters their password, hash it and compare to the stored hash.
- If they match, allow login; otherwise, show an error.
- **Why:** Standard login process; pre-filling username improves UX.
- **Common Errors:**
  - Comparing the plain password to the hash (always hash the input before comparing).
  - Not handling null/empty input.
  - Not giving clear error messages for failed login.

### 5. Logout Flow
- Add a logout button in your main app screen.
- When clicked, call `logout()` in the repository, which should clear only the in-memory session (not the stored credentials).
- Return to the login screen.
- **Why:** Logout should end the session, not delete the account.
- **Common Errors:**
  - Accidentally deleting credentials on logout (make sure only session state is cleared).
  - Not clearing sensitive in-memory data (if you store tokens, etc.).

### 6. Prepare for Firebase
- Design your `AuthRepository` so that all UI code interacts only with its methods.
- Later, you can add a flag or subclass to switch between local and Firebase authentication.
- For now, keep all Firebase code out, but plan for it by using clear interfaces.
- **Why:** Makes it easy to add online authentication later without rewriting your UI.
- **Common Errors:**
  - Mixing UI and authentication logic (keep them separate).
  - Hardcoding logic that assumes only local auth.

### 7. Security Considerations
- Never store plain-text passwords.
- Use EncryptedSharedPreferences for all sensitive data.
- For production, consider using biometric authentication or Android Keystore for even more security.
- **Common Errors:**
  - Storing sensitive data in regular SharedPreferences.
  - Not handling exceptions from encryption APIs.

### Further Considerations
1. **Biometric Authentication:**
   - You can add biometric authentication (such as fingerprint or face unlock) for extra security. This can be done using Android's BiometricPrompt API.
   - **How to implement:**
     - Add the `androidx.biometric:biometric` dependency to your `build.gradle`.
     - Use the BiometricPrompt in your login flow to require biometric authentication before granting access.
     - Combine biometric authentication with password entry for two-factor security, or allow users to choose their preferred method.
   - **Common Errors:**
     - Not checking if the device supports biometrics before prompting.
     - Not handling cases where the user cancels or fails biometric authentication.
   - **Learning Tip:**
     - Practice by adding a simple biometric prompt to your login screen and testing on both emulator and real device.

2. **Account Deletion:**
   - Add a separate “Delete Account” option in your app’s settings or profile screen to allow users to remove their credentials from the device.
   - **How to implement:**
     - In your `AuthRepository`, add a `deleteAccount()` method that removes the username and password hash from EncryptedSharedPreferences.
     - In your UI, provide a button or menu item labeled “Delete Account.”
     - Confirm with the user before deleting (e.g., show a confirmation dialog).
     - After deletion, return the user to the account creation or login screen.
   - **Common Errors:**
     - Accidentally deleting the account without confirmation.
     - Not properly clearing all sensitive data.
   - **Learning Tip:**
     - Test the delete flow thoroughly to ensure no credentials remain after deletion.

3. **Code Structure:**
   - Keep your repository interface clean and abstract so you can swap out local authentication for Firebase Auth (or any other backend) with minimal changes to your UI code.
   - **How to implement:**
     - Define an interface (e.g., `IAuthRepository`) with methods like `createAccount`, `login`, `logout`, `deleteAccount`, etc.
     - Have your current `AuthRepository` implement this interface for local auth.
     - When ready to add Firebase, create a `FirebaseAuthRepository` that implements the same interface.
     - In your UI, depend only on the interface, not the concrete implementation.
     - Use dependency injection or a simple factory to choose which implementation to use at runtime.
   - **Common Errors:**
     - Tightly coupling UI code to a specific auth implementation.
     - Duplicating logic between local and Firebase repositories.
   - **Learning Tip:**
     - Practice refactoring your code to use interfaces and dependency injection for better flexibility and testability.

### Tips for Debugging
- If you get “KeyStoreException” or “IllegalStateException”, check that your device/emulator supports the required encryption.
- If login always fails, check that you are hashing the password both when saving and when checking.
- Use Logcat to print debug messages if something isn’t working as expected.
