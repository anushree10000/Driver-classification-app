"""
Generates synthetic accelerometer/gyroscope sessions for three driving
styles (Calm, Normal, Aggressive), matching the exact CSV schema the
Android app logs: timestamp,acc_x,acc_y,acc_z,gyro_x,gyro_y,gyro_z at 10Hz
(100ms intervals, matching MainActivity.kt's logIntervalMillis).

The three styles differ in the parameters that actually distinguish real
driving styles: acceleration/braking magnitude and frequency, turn
sharpness (gyro), and how "jerky" the motion is (rate of change of
acceleration).
"""
import numpy as np
import pandas as pd

rng = np.random.default_rng(42)

STYLES = ["Calm", "Normal", "Aggressive"]
SESSIONS_PER_STYLE = 200
SAMPLES_PER_SESSION = 300  # 30 seconds at 10Hz

# style_params: (accel_std, event_rate, event_magnitude, gyro_std, jerk_scale)
STYLE_PARAMS = {
    "Calm":       dict(accel_std=0.15, event_rate=0.01, event_mag=1.0, gyro_std=0.08, jerk_scale=0.05),
    "Normal":     dict(accel_std=0.30, event_rate=0.03, event_mag=2.0, gyro_std=0.18, jerk_scale=0.15),
    "Aggressive": dict(accel_std=0.55, event_rate=0.08, event_mag=3.5, gyro_std=0.40, jerk_scale=0.35),
}

GRAVITY = 9.81


def generate_session(style: str, n=SAMPLES_PER_SESSION):
    p = STYLE_PARAMS[style]
    # base accelerometer signal: gravity on z, small noise on x/y (lateral/forward)
    acc_x = rng.normal(0, p["accel_std"], n)
    acc_y = rng.normal(0, p["accel_std"], n)
    acc_z = rng.normal(GRAVITY, p["accel_std"] * 0.3, n)

    # inject occasional harsh acceleration/braking/turn "events"
    event_mask = rng.random(n) < p["event_rate"]
    acc_x[event_mask] += rng.normal(0, p["event_mag"], event_mask.sum())
    acc_y[event_mask] += rng.normal(0, p["event_mag"], event_mask.sum())

    gyro_x = rng.normal(0, p["gyro_std"], n)
    gyro_y = rng.normal(0, p["gyro_std"], n)
    gyro_z = rng.normal(0, p["gyro_std"], n)  # yaw rate -- turning sharpness
    gyro_z[event_mask] += rng.normal(0, p["event_mag"] * 0.5, event_mask.sum())

    # smooth slightly (mimics the app's low-pass filter, alpha=0.25)
    def smooth(arr, alpha=0.25):
        out = np.zeros_like(arr)
        out[0] = arr[0]
        for i in range(1, len(arr)):
            out[i] = out[i - 1] + alpha * (arr[i] - out[i - 1])
        return out

    return pd.DataFrame({
        "acc_x": smooth(acc_x), "acc_y": smooth(acc_y), "acc_z": smooth(acc_z),
        "gyro_x": smooth(gyro_x), "gyro_y": smooth(gyro_y), "gyro_z": smooth(gyro_z),
    })


def extract_features(df: pd.DataFrame) -> dict:
    """
    Engineered features per session. These are the SAME features
    FeatureExtractor.kt computes on-device, so training and inference
    must stay in sync if you change this.
    """
    acc_mag = np.sqrt(df["acc_x"]**2 + df["acc_y"]**2 + (df["acc_z"] - GRAVITY)**2)
    gyro_mag = np.sqrt(df["gyro_x"]**2 + df["gyro_y"]**2 + df["gyro_z"]**2)
    jerk = np.diff(acc_mag)  # rate of change of acceleration magnitude

    return {
        "acc_mag_mean": acc_mag.mean(),
        "acc_mag_std": acc_mag.std(),
        "acc_mag_max": acc_mag.max(),
        "gyro_mag_mean": gyro_mag.mean(),
        "gyro_mag_std": gyro_mag.std(),
        "gyro_mag_max": gyro_mag.max(),
        "jerk_std": jerk.std(),
        "jerk_max_abs": np.abs(jerk).max(),
        "harsh_event_rate": (acc_mag > acc_mag.mean() + 2 * acc_mag.std()).mean(),
    }


def main():
    rows = []
    for style in STYLES:
        for _ in range(SESSIONS_PER_STYLE):
            df = generate_session(style)
            feats = extract_features(df)
            feats["label"] = style
            rows.append(feats)

    out = pd.DataFrame(rows)
    out.to_csv("driving_features.csv", index=False)
    print(f"Generated {len(out)} sessions across {len(STYLES)} styles")
    print(out.groupby("label").mean(numeric_only=True).round(3))


if __name__ == "__main__":
    main()
