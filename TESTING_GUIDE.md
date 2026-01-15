# Testing Guide for Dog Detection App

## What Was Rebuilt

I completely rebuilt your app from scratch with the following improvements:

### 1. **MyAccessibilityService.kt**
- ✓ Recursive text extraction from accessibility nodes
- ✓ Better logging with emoji markers (🐕 for detection, ✓ for success, ⏳ for debounce)
- ✓ Higher priority notifications with vibration
- ✓ Proper error handling
- ✓ Logs every event it receives

### 2. **MainActivity.kt**
- ✓ Proper permission flow: Notification permission FIRST, then accessibility
- ✓ Uses `onRequestPermissionsResult` callback to ensure notification permission is handled before showing accessibility dialog
- ✓ Better UI with centered button
- ✓ Logs permission status on app open and resume
- ✓ Non-dismissible accessibility dialog

### 3. **DogTestActivity.kt**
- ✓ Multiple test scenarios: "dog", "I have a dog", "DOG", "Hot dog", etc.
- ✓ Large, visible text (48sp)
- ✓ Logs every text change
- ✓ Easy navigation through test cases

### 4. **Accessibility Service Config**
- ✓ Specific event types for better performance
- ✓ Flags for retrieving interactive windows and view IDs
- ✓ Proper configuration for text extraction

### 5. **Permissions**
- ✓ POST_NOTIFICATIONS (for Android 13+)
- ✓ VIBRATE (for notification vibration)

## How to Test

### Step 1: Install and Launch
1. Build and install the app on your device
2. Launch the app

### Step 2: Grant Permissions (IN ORDER)
1. **First**, you should see a notification permission dialog → **Allow**
2. **Then**, you'll see an accessibility service dialog → Click **Open Settings**
3. In accessibility settings, find "TextDetector" and toggle it **ON**
4. Return to the app

### Step 3: Check Logcat
Open Logcat and filter by `DogDetection` or `PermissionCheck`:

You should see:
```
PermissionCheck: ✓ Notification permission: true
PermissionCheck: ✓ Accessibility service: true
DogDetection: ✓ Accessibility service connected and running
DogDetection: ✓ Notification channel created
```

### Step 4: Test with Test Page
1. Click "Open Dog Test Page" button
2. You should immediately see a large "dog" text
3. Check Logcat for:
   ```
   DogDetection: Event: type=...
   DogDetection: Text found: dog
   DogDetection: 🐕 DETECTED 'dog' in text: dog
   DogDetection: ✓ Notification sent
   ```
4. You should receive a notification: "🐕 Dog Detected!"
5. Click "Next Test Text" to cycle through different test cases
6. The notification should only appear once every 5 seconds (debounce)

### Step 5: Test with Other Apps
1. Open Chrome or Messages
2. Type or view the word "dog"
3. Check if you get a notification

## Troubleshooting

### No "Accessibility service connected" log
- The service is not enabled. Go to Settings → Accessibility → TextDetector → Toggle ON
- You may need to restart the app after enabling

### No events received
- Check Logcat for "Event: type=" logs
- If you don't see ANY events, the service may not have permission to read screen content
- Try disabling and re-enabling the service in accessibility settings
- Some apps (especially browsers) may not expose text to accessibility services

### Notification permission not granted
- Check Logcat: `PermissionCheck: ✓ Notification permission: false`
- Go to Settings → Apps → TextDetector → Notifications → Allow

### IDs not found error in DogTestActivity
- This is an IDE sync issue
- In Android Studio: File → Invalidate Caches → Invalidate and Restart
- Or: Build → Clean Project, then Build → Rebuild Project

## Expected Behavior

✓ Notification permission requested BEFORE accessibility dialog
✓ Accessibility service runs system-wide (not just in your app)
✓ Detects "dog" case-insensitively anywhere on screen
✓ Shows notification with vibration
✓ Logs every detection to Logcat
✓ Respects 5-second debounce between notifications
✓ Works on page switches and text changes

## Logs to Watch For

```
✓ = Success
🐕 = Dog detected
⏳ = Debounced (skipped notification)
→ = Action being taken
```

Good luck! Check the logs carefully to see what's happening.

