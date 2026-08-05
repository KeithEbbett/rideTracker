# Walkthrough - Strava Integration

I have successfully integrated Strava into your Ride Tracker app! You can now securely connect your account and upload your cycling activities with a single tap.

## Key Features

### 1. Secure OAuth Connection
I've implemented the official "Connect to Strava" flow.
- **[Settings Screen](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/SettingsScreen.kt)**: You'll find a new **"Connect"** button in the "Strava Integration" section.
- **How it works**: Tapping Connect will open Strava (or your browser) for you to authorize the app. Once you hit "Authorize," Strava will automatically redirect back to the app using a custom deep link (`ridetracker://strava`).

### 2. Activity Uploading
You can now share your rides to your Strava feed.
- **[Ride History](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/RideHistoryScreen.kt)**: Each ride in your history now has a Strava upload icon (orange arrow).
- **One-Tap Export**: When you tap the icon, the app generates a high-precision GPX file containing your GPS path, altitude, and heart rate data, and securely uploads it to Strava.

### 3. Safety & Security
- **Token Management**: I used **Encrypted SharedPreferences** to store your Strava tokens. This ensures that your private access data is protected by your phone's hardware-level security.
- **Key Protection**: Your API Client ID and Secret are safely read from `local.properties` and are never committed to your code repository.

## How to Test
1. Redeploy the app to your Pixel 9 Pro XL.
2. Go to **Settings** and tap **Connect** in the Strava section.
3. Follow the prompts in the Strava app/website to authorize.
4. Once returned to the app, verify it says **"Connected to Strava"**.
5. Go to your **Ride History**, find a past ride, and tap the orange upload icon.
6. Check your Strava feed—your ride should appear there shortly!
