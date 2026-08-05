# Implementation Plan - Strava Integration

The goal is to connect the app to your Strava account so you can automatically or manually upload your rides.

## Strava API Setup (User Action Required)

Before I can apply the code changes, you need to get your unique API keys from Strava:

1.  Log in to [strava.com/settings/api](https://www.strava.com/settings/api).
2.  Create an application if you haven't already.
3.  **IMPORTANT**: Set the **"Authorization Callback Domain"** to `localhost`.
4.  Once created, find your **Client ID** and **Client Secret**.
5.  Open [local.properties](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/local.properties) and replace the placeholders:
    - `strava.client_id=12345`
    - `strava.client_secret=your_secret_here`

## Proposed Code Changes

### 1. Build Configuration

#### [MODIFY] [build.gradle.kts (app)](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/build.gradle.kts)
- Add logic to read `local.properties` and define `STRAVA_CLIENT_ID` and `STRAVA_CLIENT_SECRET` as `buildConfigField`.

### 2. Authentication Logic

#### [MODIFY] [StravaApi.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/data/strava/StravaApi.kt)
- Add `getTokens` endpoint to exchange the initial authorization code for access/refresh tokens.

#### [MODIFY] [StravaRepository.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/data/strava/StravaRepository.kt)
- Add `isLoggedIn` status check.
- Add `completeLogin(code)` method to perform the token exchange.
- Add `logout()` to clear tokens.

### 3. Deep Link Handling

#### [MODIFY] [AndroidManifest.xml](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/AndroidManifest.xml)
- Add an `intent-filter` to `MainActivity` to handle the `ridetracker://strava` deep link.

#### [MODIFY] [MainActivity.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/MainActivity.kt)
- Add logic to detect when the app is opened via a deep link and pass the Strava `code` to the ViewModel.

### 4. User Interface

#### [MODIFY] [RideViewModel.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/RideViewModel.kt)
- Add `stravaLoginUrl` generator.
- Add `isStravaConnected` state.
- Add `handleStravaCode(code)` logic.

#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/SettingsScreen.kt)
- Add a **"Strava Integration"** section with a **"Connect to Strava"** or **"Disconnect Strava"** button.

#### [MODIFY] [RideHistoryScreen.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/RideHistoryScreen.kt)
- Add a Strava upload icon/button to each ride entry in the list.

## Verification Plan

### Manual Verification
- Deploy the app.
- Go to Settings -> Tap **Connect to Strava**.
- Verify it opens the Strava app or website for authorization.
- After authorizing, verify the app returns and shows "Connected as [Name]".
- Go to History and upload a previous ride. Verify it appears on Strava!
