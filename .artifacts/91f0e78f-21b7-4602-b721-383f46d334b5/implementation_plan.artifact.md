# Implementation Plan - Enhanced Button Controls (Start/Pause/Finish)

The goal is to implement a more standard cycling computer button flow:
1.  **Start**: Begins the ride.
2.  **Pause**: Manually pauses the timer and distance accumulation.
3.  **Resume**: Continues the ride from a manual pause.
4.  **Finish**: Ends and saves the ride (triggered by a long press while paused).

## User Review Required

> [!IMPORTANT]
> - **States**: The button will now have three distinct labels: **START RIDE**, **PAUSE**, and **RESUME**.
> - **Long Press**: To prevent accidental ride endings, you must **long-press (hold for 1 second)** the button while it's in the "Paused" state to finish and save your ride.

## Proposed Changes

### 1. State Management

#### [MODIFY] [RideSessionManager.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/data/RideSessionManager.kt)
- Add `isManuallyPaused: Boolean = false` to `RideState`.
- Manual pause is "sticky" and overrides auto-pause logic.

### 2. Service Logic

#### [MODIFY] [RideTrackingService.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/service/RideTrackingService.kt)
- Add `ACTION_PAUSE` and `ACTION_RESUME` intents.
- Update the timer and distance logic to only accumulate when `isTracking == true` AND `isAutoPaused == false` AND `isManuallyPaused == false`.
- Update notifications to show "Paused" when manually paused.

### 3. User Interface

#### [MODIFY] [RideViewModel.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/RideViewModel.kt)
- Add `pauseRide()`, `resumeRide()`, and `finishRide()` methods that send the appropriate intents to the service.

#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/ui/DashboardScreen.kt)
- Implement logic to show the correct button label based on state.
- Use `Modifier.pointerInput` or `Modifier.combinedClickable` to detect both **Taps** (Pause/Resume) and **Long Presses** (Finish).

## Verification Plan

### Manual Verification
- Deploy to Pixel 9 Pro XL.
- Tap **START RIDE**: Verify metrics begin.
- Tap **PAUSE**: Verify timer stops and button changes to **RESUME**.
- Tap **RESUME**: Verify tracking continues.
- Tap **PAUSE**, then **Long Press**: Verify the ride ends, saves to history, and returns to the initial state.
