# StayAccountable

StayAccountable is an app designed to help users stay accountable for their activity online. The app looks through text shown on the screen, flags any explicit words, and syncs these events to a secure cloud database. Eventually, these flagged words will be available for a trusted partner to view and monitor.

## Instructions for Build and Use

Steps to build and/or run the software:

1. Open project in Android Studio.
2. Either plug in a device (with USB debugging enabled) or use an emulator to run the project.

Instructions for using the software:

1. Open the app.
2. Create an account or sign in using your email and password.
3. Allow the requested permissions and follow the on-screen instructions to enable the accessibility service.
4. From the main screen, ensure the monitoring service switch is enabled.
5. Navigate your phone as normal. If trigger words are found, you will receive a notification.
6. As words are logged, they are saved to a local database on the device and then automatically synced to your account on the cloud. You can view all synced and pending logs in the app.
7. You can visit the Profile page to view your account email and set a display name.

## Development Environment

To recreate the development environment, you need the following software and/or libraries with the specified versions:

* Android Studio
* Firebase

## Useful Websites to Learn More

I found these websites useful in developing this software:

* [Android Mobile Developers](https://developer.android.com/)
* [Firebase Authentication](https://firebase.google.com/docs/auth/android/start)
* [Cloud Firestore](https://firebase.google.com/docs/firestore)
* [Github Copilot](https://github.com/features/copilot)

## Future Work

The following items I plan to fix, improve, and/or add to this project in the future:

* [X] Link to Database and sync events.
* [X] Save Username and Password data and check if they are correct when logging in.
* [ ] Allow viewing a partner's logs from a different device.
