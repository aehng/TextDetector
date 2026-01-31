# google-services.json - PLACEHOLDER FILE

⚠️ **WARNING: This is a PLACEHOLDER file with dummy Firebase configuration.**

## What You Need To Do

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create or select your Firebase project
3. Add an Android app with package name: `com.example.stayaccountable`
4. Download the real `google-services.json` file
5. **Replace this file** with your downloaded file

## Why This Placeholder Exists

This placeholder file was created to allow the Gradle build configuration to be tested without requiring immediate access to a real Firebase project. However, **Firebase functionality will NOT work** with this placeholder.

## Configuration Details

- **Package Name**: `com.example.stayaccountable`
- **Location**: `app/google-services.json`

The package name must match:
- `AndroidManifest.xml` → `package="com.example.stayaccountable"`
- `app/build.gradle.kts` → `namespace = "com.example.stayaccountable"`
- Firebase Console → Android app package name

## Do Not Use in Production

This file contains:
- Dummy project ID
- Dummy API keys
- Dummy app ID

**These credentials will NOT connect to any real Firebase backend.**
