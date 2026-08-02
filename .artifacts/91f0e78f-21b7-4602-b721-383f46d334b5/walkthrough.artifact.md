# Walkthrough - Stable Road Gradient Calculation

I have implemented a more robust road gradient calculation system to ensure that your readings are steady and accurate, even in noisy environments.

## Technical Improvements

### 1. Linear Regression (Best-Fit) Algorithm
Instead of just comparing the start and end of your path, the app now uses **Linear Regression** across a **40-meter rolling window**.
- **How it works**: The app looks at every altitude and distance point collected over the last 40 meters. It calculates the mathematical "best-fit" line through those points.
- **Benefit**: This effectively "ignores" outlier data or minor sensor blips, resulting in a much more stable percentage that doesn't flicker wildly.

### 2. Low-Pass Altitude Filtering
I added a digital **Low-Pass Filter** to the raw barometric data.
- **The Filter**: It uses a smoothing factor (`0.2`) to blend new readings with previous ones.
- **Benefit**: This removes the high-frequency "jitter" that is natural to barometric sensors, ensuring the altitude data used for gradient and elevation gain is clean and reliable.

### 3. Safety Clamping
I implemented realistic "clamping" for the gradient display.
- **Range**: The gradient is limited to **±25%**.
- **Reason**: This prevents extreme, unrealistic spikes (e.g., 80% gradient) from appearing if the GPS loses signal momentarily, maintaining a professional look and feel.

## Results
- **Smoothness**: You will notice the gradient number changes more gradually and accurately reflects the actual slope of the road.
- **Accuracy**: Elevation gain calculations are now more precise because they are based on filtered, noise-free altitude data.

## How to Test
1. Take the app for a ride on a road with a known steady incline.
2. Observe the gradient reading—it should settle into a stable number (e.g., 5.0%) rather than bouncing between 2% and 8%.
3. Verify that on flat ground, the reading stays very close to 0.0%.
