#!/usr/bin/env python3
"""
Synthetic smartphone usage data generator.

Outputs NumPy arrays of shape (N, 12, 6) where features per timestep are:
[screenOnSeconds, foregroundSeconds, appSwitchCount, unlockCount, hourSin, hourCos]

Scenarios:
- normal: moderate usage across daytime
- binge: prolonged continuous usage with higher foreground time and switches
- late_night: usage concentrated during late-night hours (00:00-05:00)

Saves per-scenario .npy and an aggregate .npz.
"""
import argparse
import numpy as np

FEATURES = 6
TIMESTEPS = 12


def _hour_angle(minutes_of_day: float) -> float:
    return 2.0 * np.pi * (minutes_of_day / 1440.0)


def _hour_sin_cos(minutes_of_day: float) -> tuple[float, float]:
    a = _hour_angle(minutes_of_day)
    return float(np.sin(a)), float(np.cos(a))


def _gen_time_grid(start_minute: int, step: int, count: int) -> np.ndarray:
    return np.array([(start_minute + i * step) % 1440 for i in range(count)], dtype=np.int32)


def generate_normal(n: int, rng: np.random.Generator) -> np.ndarray:
    data = np.zeros((n, TIMESTEPS, FEATURES), dtype=np.float32)
    for i in range(n):
        # Start somewhere in the morning/afternoon
        start_min = int(rng.integers(8 * 60, 20 * 60))
        minutes = _gen_time_grid(start_min, step=5, count=TIMESTEPS)
        # Base levels
        base_screen = rng.normal(60, 20)  # seconds per 5-min
        base_fore = rng.normal(50, 20)
        base_switch = rng.normal(2, 1)
        base_unlock = rng.normal(1, 0.5)
        for t, m in enumerate(minutes):
            # Daytime modulation: less usage at edges
            day_factor = 0.6 + 0.4 * np.sin((m - 9 * 60) / (12 * 60) * np.pi)
            screen = max(0.0, rng.normal(base_screen * day_factor, 10))
            fore = max(0.0, min(screen, rng.normal(base_fore * day_factor, 10)))
            switches = max(0.0, rng.normal(base_switch * day_factor, 0.8))
            unlocks = max(0.0, rng.normal(base_unlock * day_factor, 0.4))
            hsin, hcos = _hour_sin_cos(float(m))
            data[i, t] = [screen, fore, switches, unlocks, hsin, hcos]
    return data


def generate_binge(n: int, rng: np.random.Generator) -> np.ndarray:
    data = np.zeros((n, TIMESTEPS, FEATURES), dtype=np.float32)
    for i in range(n):
        # Binge often in evening
        start_min = int(rng.integers(18 * 60, 24 * 60))
        minutes = _gen_time_grid(start_min, step=5, count=TIMESTEPS)
        base_screen = rng.normal(240, 40)  # very high per 5-min (max 300)
        base_fore = rng.normal(220, 40)
        base_switch = rng.normal(4, 1.5)
        base_unlock = rng.normal(0.5, 0.3)  # fewer unlocks during continuous session
        for t, m in enumerate(minutes):
            # Slight decay/growth across the session
            trend = 0.9 + 0.2 * (t / (TIMESTEPS - 1))
            screen = np.clip(rng.normal(base_screen * trend, 15), 60, 300)
            fore = np.clip(rng.normal(base_fore * trend, 15), 60, screen)
            switches = max(0.0, rng.normal(base_switch, 1.0))
            unlocks = max(0.0, rng.normal(base_unlock, 0.2))
            hsin, hcos = _hour_sin_cos(float(m))
            data[i, t] = [screen, fore, switches, unlocks, hsin, hcos]
    return data


def generate_late_night(n: int, rng: np.random.Generator) -> np.ndarray:
    data = np.zeros((n, TIMESTEPS, FEATURES), dtype=np.float32)
    for i in range(n):
        # Start between 00:00 and 03:00
        start_min = int(rng.integers(0, 3 * 60))
        minutes = _gen_time_grid(start_min, step=5, count=TIMESTEPS)
        base_screen = rng.normal(120, 40)
        base_fore = rng.normal(100, 35)
        base_switch = rng.normal(3, 1.0)
        base_unlock = rng.normal(0.7, 0.4)
        for t, m in enumerate(minutes):
            night_factor = 0.7 + 0.3 * np.sin((m - 2 * 60) / (3 * 60) * np.pi)
            screen = np.clip(rng.normal(base_screen * night_factor, 15), 0, 300)
            fore = np.clip(rng.normal(base_fore * night_factor, 15), 0, screen)
            switches = max(0.0, rng.normal(base_switch, 0.8))
            unlocks = max(0.0, rng.normal(base_unlock, 0.3))
            hsin, hcos = _hour_sin_cos(float(m))
            data[i, t] = [screen, fore, switches, unlocks, hsin, hcos]
    return data


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--normal", type=int, default=500, help="number of normal sequences")
    parser.add_argument("--binge", type=int, default=300, help="number of binge sequences")
    parser.add_argument("--late", type=int, default=300, help="number of late-night sequences")
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--outdir", type=str, default="./synthetic_out")
    args = parser.parse_args()

    rng = np.random.default_rng(args.seed)

    normal = generate_normal(args.normal, rng)
    binge = generate_binge(args.binge, rng)
    late = generate_late_night(args.late, rng)

    import os
    os.makedirs(args.outdir, exist_ok=True)

    np.save(os.path.join(args.outdir, "normal.npy"), normal)
    np.save(os.path.join(args.outdir, "binge.npy"), binge)
    np.save(os.path.join(args.outdir, "late_night.npy"), late)

    # Aggregate with labels for convenience: 0=normal, 1=binge, 2=late
    X = np.concatenate([normal, binge, late], axis=0)
    y = np.concatenate([
        np.zeros((normal.shape[0],), dtype=np.int64),
        np.ones((binge.shape[0],), dtype=np.int64),
        np.full((late.shape[0],), 2, dtype=np.int64)
    ], axis=0)
    np.savez_compressed(os.path.join(args.outdir, "dataset.npz"), X=X, y=y)

    print("Saved:")
    print(" ", os.path.join(args.outdir, "normal.npy"), normal.shape)
    print(" ", os.path.join(args.outdir, "binge.npy"), binge.shape)
    print(" ", os.path.join(args.outdir, "late_night.npy"), late.shape)
    print(" ", os.path.join(args.outdir, "dataset.npz"), X.shape, y.shape)


if __name__ == "__main__":
    main()
