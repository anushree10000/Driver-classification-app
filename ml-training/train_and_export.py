"""
Trains a small feedforward classifier on the engineered driving-style
features and exports it as a quantized TFLite model ready to embed in the
Android app's assets folder.

Usage: python3 train_and_export.py
Output: driving_classifier.tflite, feature_scaler.json, labels.txt
"""
import json

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder, StandardScaler

FEATURE_COLS = [
    "acc_mag_mean", "acc_mag_std", "acc_mag_max",
    "gyro_mag_mean", "gyro_mag_std", "gyro_mag_max",
    "jerk_std", "jerk_max_abs", "harsh_event_rate",
]

df = pd.read_csv("driving_features.csv")
X = df[FEATURE_COLS].values.astype("float32")
y_raw = df["label"].values

label_encoder = LabelEncoder()
y = label_encoder.fit_transform(y_raw)
labels_in_order = list(label_encoder.classes_)
print("Label order (index -> class):", labels_in_order)

scaler = StandardScaler()
X_scaled = scaler.fit_transform(X).astype("float32")

X_train, X_test, y_train, y_test = train_test_split(
    X_scaled, y, test_size=0.2, stratify=y, random_state=42
)

model = tf.keras.Sequential([
    tf.keras.layers.Input(shape=(len(FEATURE_COLS),)),
    tf.keras.layers.Dense(16, activation="relu"),
    tf.keras.layers.Dense(8, activation="relu"),
    tf.keras.layers.Dense(len(labels_in_order), activation="softmax"),
])
model.compile(optimizer="adam", loss="sparse_categorical_crossentropy", metrics=["accuracy"])
model.fit(X_train, y_train, epochs=40, batch_size=16, validation_split=0.15, verbose=0)

test_loss, test_acc = model.evaluate(X_test, y_test, verbose=0)
print(f"Test accuracy: {test_acc:.4f}")

# --- Export to TFLite ---
converter = tf.lite.TFLiteConverter.from_keras_model(model)
tflite_model = converter.convert()
with open("driving_classifier.tflite", "wb") as f:
    f.write(tflite_model)
print(f"Wrote driving_classifier.tflite ({len(tflite_model)} bytes)")

# --- Export the scaler params + label order for Kotlin to use at inference time ---
scaler_json = {
    "feature_order": FEATURE_COLS,
    "mean": scaler.mean_.tolist(),
    "scale": scaler.scale_.tolist(),
    "labels": labels_in_order,
}
with open("feature_scaler.json", "w") as f:
    json.dump(scaler_json, f, indent=2)
print("Wrote feature_scaler.json")

with open("labels.txt", "w") as f:
    f.write("\n".join(labels_in_order))
print("Wrote labels.txt")
