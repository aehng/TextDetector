## Plan: Accessibility Service for "dog" Detection

Develop an Android accessibility service that monitors all on-screen text for the word "dog" (case-insensitive), and notifies the user at most once every 5 seconds if detected. The service will efficiently process only relevant accessibility events (including page switches and typing), and notifications will be user-friendly and non-spammy. All processing is local for privacy.

### Steps
1. Create [MyAccessibilityService.kt](app/src/main/java/com/example/textdetector/MyAccessibilityService.kt) extending `AccessibilityService`.
2. In the service, filter for relevant event types: `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_VIEW_TEXT_CHANGED`, and `TYPE_WINDOW_STATE_CHANGED` to capture page switches and typing.
3. Extract all visible text from the event, check for "dog" (case-insensitive), and use debounce logic to limit notifications to once every 5 seconds.
4. Use Android's notification APIs to alert the user in a clear, non-intrusive way.
5. Register the service in [AndroidManifest.xml](app/src/main/AndroidManifest.xml) with the correct intent filter and permissions.
6. Update [MainActivity.kt](app/src/main/java/com/example/textdetector/MainActivity.kt) to guide users to enable the accessibility service.

### Further Considerations
1. Efficiency: Only process necessary events and debounce notifications to minimize battery impact.
2. Privacy: All text processing is local; inform users about accessibility service usage in the UI or onboarding.
3. Extensibility: The architecture allows for easy addition of more keywords in the future.
4. Notification: Ensure notifications are clear, actionable, and not spammy due to throttling.

Please review and confirm if this plan meets your requirements or if you want to adjust any details before proceeding.

