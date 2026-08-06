#!/usr/bin/env python3
"""
Train an LSTM Autoencoder for smartphone usage sequences.

- Expects input sequences shaped (N, 12, 6)
- Loss: MSE
- Optimizer: Adam
- Epochs: configurable (<= 30)
- Saves trained Keras model (SavedModel directory) and training history CSV

Usage (Windows PowerShell):
  python scripts/train_lstm_autoencoder.py --data .\synthetic_out\normal.npy --epochs 20 --outdir .\models\lstm_ae
  python scripts/train_lstm_autoencoder.py --data .\synthetic_out\dataset.npz --dataset-key X --epochs 25
"""
import argparse
import os
import sys
from typing import Tuple

import numpy as np

os.environ["TF_CPP_MIN_LOG_LEVEL"] = "2"

try:
    import tensorflow as tf
    from tensorflow import keras
    from tensorflow.keras import layers
except Exception as e:
    print("ERROR: TensorFlow is required to run this script.")
    raise

TIMESTEPS = 12
FEATURES = 6


def load_sequences(path: str, dataset_key: str | None) -> np.ndarray:
    if not os.path.exists(path):
        raise FileNotFoundError(path)
    if path.endswith(".npz"):
        with np.load(path) as d:
            key = dataset_key or "X"
            X = d[key]
    else:
        X = np.load(path)
    if X.ndim != 3 or X.shape[1] != TIMESTEPS or X.shape[2] != FEATURES:
        raise ValueError(f"Expected shape (N, {TIMESTEPS}, {FEATURES}), got {X.shape}")
    X = X.astype(np.float32)
    return X


def build_model(latent_dim: int = 32) -> keras.Model:
    inputs = keras.Input(shape=(TIMESTEPS, FEATURES))
    x = layers.Masking(mask_value=0.0)(inputs)
    encoded = layers.LSTM(latent_dim, name="encoder_lstm")(x)
    x = layers.RepeatVector(TIMESTEPS)(encoded)
    x = layers.LSTM(latent_dim, return_sequences=True, name="decoder_lstm")(x)
    outputs = layers.TimeDistributed(layers.Dense(FEATURES), name="reconstruction")(x)
    model = keras.Model(inputs, outputs, name="lstm_autoencoder")
    model.compile(optimizer=keras.optimizers.Adam(), loss="mse")
    return model


def train_model(X: np.ndarray, epochs: int, batch_size: int, val_split: float, outdir: str) -> Tuple[keras.Model, dict]:
    os.makedirs(outdir, exist_ok=True)
    model = build_model()

    cbs = [
        keras.callbacks.ModelCheckpoint(
            filepath=os.path.join(outdir, "ckpt.keras"),
            monitor="val_loss",
            save_best_only=True,
            save_weights_only=False,
        ),
        keras.callbacks.EarlyStopping(
            monitor="val_loss", patience=5, restore_best_weights=True
        ),
        keras.callbacks.CSVLogger(os.path.join(outdir, "history.csv")),
    ]

    history = model.fit(
        X, X,
        epochs=epochs,
        batch_size=batch_size,
        validation_split=val_split,
        shuffle=True,
        callbacks=cbs,
        verbose=1,
    )

    # Save final model (SavedModel)
    save_dir = os.path.join(outdir, "saved_model")
    model.save(save_dir, include_optimizer=False)

    return model, {k: [float(v) for v in vals] for k, vals in history.history.items()}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=str, required=True, help="Path to .npy or .npz sequences")
    parser.add_argument("--dataset-key", type=str, default=None, help="Key for npz (default: X)")
    parser.add_argument("--epochs", type=int, default=20, help="<= 30")
    parser.add_argument("--batch-size", type=int, default=64)
    parser.add_argument("--val-split", type=float, default=0.1)
    parser.add_argument("--outdir", type=str, default="./models/lstm_ae")
    args = parser.parse_args()

    if args.epochs > 30:
        print("WARNING: epochs limited to 30; overriding to 30")
        args.epochs = 30

    X = load_sequences(args.data, args.dataset_key)

    # Optional normalization (min-max per feature across dataset)
    mins = X.min(axis=(0, 1), keepdims=True)
    maxs = X.max(axis=(0, 1), keepdims=True)
    denom = np.clip(maxs - mins, 1e-6, None)
    Xn = (X - mins) / denom

    model, hist = train_model(Xn, epochs=args.epochs, batch_size=args.batch_size, val_split=args.val_split, outdir=args.outdir)

    print("Training complete. Model saved to:", os.path.join(args.outdir, "saved_model"))


if __name__ == "__main__":
    main()
