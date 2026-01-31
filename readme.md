# StayAccountable

StayAccountable is an app designed to help users stay accountable for their activity online. The app looks though text shown on the screen and flags any explicit words. 
Eventually these flagged words will be sent to a server where a second user would be able to view and monitor activity.

## Instructions for Build and Use

Steps to build and/or run the software:

1. Open project in Android Studio.
2. Either plug in a device (with USB debugging enabled) or use an emulator to run the project

Instructions for using the software:

1. Open app
2. Enter a username and password (for now they are not saved) and hit "Start"
3. Allow permissions and follow instructions to enable the accessibility service
4. Hit start and ensure the switch is enabled.
5. Navigate your phone as normal and if the trigger words are found (in testing mode the words "dog", "collar", and "bark") you will receive a notification.
6. As words are logged they are able to be viewed in the app. Word found, time, and severity are noted and saved to a database on device.


## Development Environment

To recreate the development environment, you need the following software and/or libraries with the specified versions:

* Android Studio

## Useful Websites to Learn More

I found these websites useful in developing this software:

* [Android Mobile Developers](https://developer.android.com/)
* [Github Copilot](https://github.com/features/copilot)

## Future Work

The following items I plan to fix, improve, and/or add to this project in the future:

* [ ] Link to Database
* [ ] Save Username and Password data to phone and database and check if they are correct when logging in
* [ ] Allow viewing a partner's logs

## Architecture Overview

Current high-level flow (aligned with Android app architecture terminology):

1. **User session (Presentation layer)** – `MainActivity` collects a username/password, validates input, and routes the user to `AccServiceSwitch` once permissions are granted.
2. **Accessibility monitoring (Domain/Service layer)** – `MyAccessibilityService` listens for `AccessibilityEvent`s, extracts on-screen text, runs the bad-word detection logic, and raises events.
3. **Local persistence (Data layer)** – `EventDatabaseHelper` writes each detection into the on-device SQLite database via the `events` table so history survives app restarts.
4. **UI rendering (Presentation layer)** – `AccServiceSwitch` registers a BroadcastReceiver, refreshes the RecyclerView (`EventAdapter`) from SQLite, and displays the log entries to the user.

This path can be summarized as: *User input ➜ Accessibility Service ➜ SQLite (`EventDatabaseHelper`) ➜ RecyclerView UI (`AccServiceSwitch`)*. Future Firebase sync work will hook into the data layer between detection and persistence while keeping this contract intact.
