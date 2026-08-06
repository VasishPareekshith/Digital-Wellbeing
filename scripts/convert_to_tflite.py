#!/usr/bin/env python3
"""
Convert a trained Keras/TensorFlow SavedModel to TensorFlow Lite.

Requirements:
- Uses default optimizations
- Saves output as timeloss.tflite (configurable via --out)

Example (PowerShell):
  python scripts/convert_to_tflite.py --saved-model .\models\lstm_ae\saved_model --out .\models\tflite\timeloss.tflite
"""
import argparse
import os
import sys

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

try:
    import tensorflow as tf
except Exception:
    print("ERROR: TensorFlow is required to run this script.")
    raise

def convert(saved_model_dir: str, out_path: str) -> None:
    if not os.path.isdir(saved_model_dir):
        raise FileNotFoundError(f"SavedModel directory not found: {saved_model_dir}")

    os.makedirs(os.path.dirname(out_path), exist_ok=True)

    converter = tf.lite.TFLiteConverter.from_saved_model(saved_model_dir)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    tflite_model = converter.convert()

    with open(out_path, "wb") as f:
        f.write(tflite_model)
    print(f"Saved TFLite model to: {out_path}")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--saved-model", type=str, required=True, help="Path to SavedModel directory")
    parser.add_argument("--out", type=str, default=os.path.join(".", "models", "tflite", "timeloss.tflite"))
    args = parser.parse_args()

    convert(args.saved_model, args.out)


if __name__ == "__main__":
    main()
