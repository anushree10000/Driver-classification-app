# Driving Style Classifier — Completion Notes

This app collects accelerometer/gyroscope data while driving (`MainActivity`),
lets you browse past recordings (`RecordingListActivity`), visualize them
(`VisualizationActivity`), and now **classifies each recording's driving
style** (Calm / Normal / Aggressive) fully on-device.

## What was added

| File | Purpose |
|---|---|
| `app/src/main/java/.../FeatureExtractor.kt` | Reads a recorded CSV, computes 9 engineered features |
| `app/src/main/java/.../DrivingClassifier.kt` | Loads the TFLite model + scaler, runs on-device inference |
| `app/src/main/assets/driving_classifier.tflite` | Trained classifier (3.4 KB) |
| `app/src/main/assets/feature_scaler.json` | Feature normalization params + label order, exported from training |
| `ml-training/` | The Python pipeline used to generate data, train, and export the model |

`RecordingListActivity.kt`'s `action_analyze` menu item, previously a
"coming soon" stub, now runs the classifier and shows a result dialog with
the predicted style and per-class confidence.

## How it works end-to-end

1. **Feature engineering, not raw time series.** Instead of feeding raw
   accelerometer/gyroscope samples into a model, each recording is reduced
   to 9 summary features: mean/std/max of acceleration magnitude, mean/std/max
   of gyroscope (turn rate) magnitude, std and max of jerk (rate of change
   of acceleration — the "smoothness" of driving), and the rate of harsh
   events (acceleration spikes beyond 2 standard deviations). This is a
   deliberate choice: raw time series of varying length is awkward for a
   tiny on-device model, while a fixed-length feature vector is fast,
   interpretable, and trains well even on small data.

2. **Training data** (`ml-training/generate_data.py`): synthetic sessions
   for three driving styles, parameterized by realistic differences —
   aggressive driving has higher acceleration variance, more frequent harsh
   events, sharper turns, and jerkier motion than calm driving. 600 sessions
   total (200 per class), each 30 seconds at 10Hz, matching the app's real
   logging rate.

3. **Model** (`ml-training/train_and_export.py`): a small feedforward
   network (9 → 16 → 8 → 3, ReLU/softmax) trained on standardized features,
   exported to TensorFlow Lite. The model is intentionally tiny (~3.4 KB) —
   this is a 9-feature classification problem, not something that needs a
   large network.

4. **On-device inference**: `DrivingClassifier.kt` memory-maps the `.tflite`
   file from assets (no network calls, works fully offline), applies the
   same standardization used in training (mean/scale loaded from
   `feature_scaler.json`), and runs inference directly on the phone.

## Important honesty note for interviews

The training data is **synthetic**, generated to reflect realistic
differences between driving styles (not scraped from real sensors or a
public dataset) — say this plainly if asked. The architecture — feature
engineering → small NN → TFLite → on-device inference — is exactly what
you'd use with real labeled driving data; only the training set would
change. This is worth stating clearly and confidently rather than
implying it's trained on real driving logs.

## To retrain with real data

If you collect real labeled sessions (drive and label each recording as
Calm/Normal/Aggressive yourself), replace `driving_features.csv` with
features extracted from real CSVs using the same `extract_features()`
logic, rerun `train_and_export.py`, and drop the new `.tflite` and
`feature_scaler.json` into `app/src/main/assets/`, replacing the existing
files. No Kotlin code changes needed as long as the feature set stays the
same.

## Running the training pipeline yourself

```bash
cd ml-training
pip install -r requirements.txt
python3 generate_data.py       # regenerates driving_features.csv
python3 train_and_export.py    # retrains, writes .tflite + scaler json
```
