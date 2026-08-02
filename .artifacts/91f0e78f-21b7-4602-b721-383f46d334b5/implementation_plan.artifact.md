# Implementation Plan - Stable Road Gradient Calculation

The goal is to fix the erratic road gradient readings by implementing a more robust calculation algorithm. Instead of comparing just two points, we will use all data points in a 40-meter window to calculate a "best-fit" slope using Linear Regression.

## User Review Required

> [!TIP]
> - **Smoothing**: I am adding a low-pass filter to the raw barometric data to remove "jitter" before it even reaches the gradient calculator.
> - **Linear Regression**: This method effectively ignores outlier data points (noise), resulting in a much steadier percentage on your screen.

## Proposed Changes

### 1. Tracking Service (RideTrackingService.kt)

#### [MODIFY] [RideTrackingService.kt](file:///C:/Users/keith/AndroidStudioProjects/ridetracker/app/src/main/java/com/example/ridetracker/service/RideTrackingService.kt)
- **New Constants**:
    - Increase `GRADIENT_WINDOW_METERS` to **40.0**.
    - Add `ALTITUDE_SMOOTHING_FACTOR` (e.g., 0.2) for a low-pass filter.
- **Filtering Logic**:
    - Implement a `smoothedAltitude` variable.
    - Every altitude update, update `smoothedAltitude = (current * factor) + (old * (1 - factor))`.
- **Linear Regression Algorithm**:
    - Replace the simple `calculateGradient` logic.
    - The new function will sum up the distance ($x$) and altitude ($y$) for all points in the 40m window.
    - It will calculate the slope ($m$) using the formula: $m = \frac{n\sum(xy) - \sum x \sum y}{n\sum(x^2) - (\sum x)^2}$.
    - This provides a much more stable average gradient over the window.

## Verification Plan

### Manual Verification
- Deploy to Pixel 9 Pro XL.
- Test in a variety of environments:
    - **Flat ground**: Verify the gradient stays near 0.0% without jumping to 2-3% randomly.
    - **Consistent climb**: Verify the reading remains steady (e.g., staying at 5.5% rather than swinging between 3% and 8%).
    - **Stop**: Verify the gradient resets or stays stable when not moving.
