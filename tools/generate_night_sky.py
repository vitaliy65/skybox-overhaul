#!/usr/bin/env python3
"""Generates a seamless night-sky cubemap (nebula + stars) in the 3x2 layout Skyboxify/OptiFine expects.

Layout (columns x rows), taken from the Skyboxify template pack:
    [ Bottom ][ Top   ][ South ]
    [ West   ][ North ][ East  ]

Minecraft axes: +X east, +Y up, +Z south. Run:  python tools/generate_night_sky.py [tile_size] [seed]
Needs: pip install numpy pillow
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

S = int(sys.argv[1]) if len(sys.argv) > 1 else 1024   # size of one face in pixels
SEED = int(sys.argv[2]) if len(sys.argv) > 2 else 7
OUT = Path(__file__).resolve().parent.parent / "src/main/resources/assets/minecraft/optifine/sky/skybox_overhaul/night.png"

# face name -> (column, row, direction function of (u, v); u right, v DOWN, both in [-1, 1])
FACES = {
    "bottom": (0, 0, lambda u, v: (u, -np.ones_like(u), v)),
    "top":    (1, 0, lambda u, v: (u, np.ones_like(u), -v)),
    "south":  (2, 0, lambda u, v: (-u, -v, np.ones_like(u))),
    "west":   (0, 1, lambda u, v: (-np.ones_like(u), -v, -u)),
    "north":  (1, 1, lambda u, v: (u, -v, -np.ones_like(u))),
    "east":   (2, 1, lambda u, v: (np.ones_like(u), -v, u)),
}


def hash3(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ (seed * 2654435761)
    h &= 0xFFFFFFFF
    h = ((h >> 13) ^ h) * 1274126177 & 0xFFFFFFFF
    h ^= h >> 16
    return (h & 0xFFFF).astype(np.float32) / 65535.0


def vnoise(p, seed):
    i = np.floor(p).astype(np.int64)
    f = (p - i).astype(np.float32)
    f = f * f * (3 - 2 * f)
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) \
                  * (f[..., 1] if dy else 1 - f[..., 1]) \
                  * (f[..., 2] if dz else 1 - f[..., 2])
                out = out + w * hash3(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, seed)
    return out


def fbm(p, seed, octaves=4):
    total, amp, norm = 0, 0.5, 0
    for o in range(octaves):
        total = total + amp * vnoise(p * (2.0 ** o), seed + o * 31)
        norm += amp
        amp *= 0.5
    return total / norm


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def nebula(d):
    """d: (..., 3) unit vectors -> (..., 3) additive RGB in 0..1."""
    q = fbm(d * 1.4 + 11.0, SEED)
    density = fbm(d * 2.4 + 2.2 * q[..., None], SEED + 100)
    density = smoothstep(0.42, 0.82, density)
    # a faint galactic band: stronger glow near a tilted great circle
    n = np.array([0.35, 0.8, 0.5], dtype=np.float32)
    n /= np.linalg.norm(n)
    band = np.exp(-((d @ n) / 0.38) ** 2)
    density = np.clip(density * (0.35 + 0.9 * band), 0, 1)
    mix = fbm(d * 1.1 + 40.0, SEED + 200)[..., None]
    purple = np.array([0.55, 0.18, 0.85], dtype=np.float32)
    teal = np.array([0.10, 0.55, 0.85], dtype=np.float32)
    pink = np.array([0.95, 0.30, 0.60], dtype=np.float32)
    col = np.where(mix < 0.5, purple + (teal - purple) * (mix / 0.5),
                   teal + (pink - teal) * ((mix - 0.5) / 0.5))
    return col * density[..., None] * 0.55


def direction_to_face(d):
    """(N,3) -> face name index array, u, v (inverse of FACES)."""
    ax, ay, az = np.abs(d[:, 0]), np.abs(d[:, 1]), np.abs(d[:, 2])
    names = np.empty(len(d), dtype=object)
    u = np.zeros(len(d), np.float32)
    v = np.zeros(len(d), np.float32)
    x, y, z = d[:, 0], d[:, 1], d[:, 2]
    m = (az >= ax) & (az >= ay) & (z < 0); names[m] = "north"; u[m] = x[m] / az[m];  v[m] = -y[m] / az[m]
    m = (az >= ax) & (az >= ay) & (z >= 0); names[m] = "south"; u[m] = -x[m] / az[m]; v[m] = -y[m] / az[m]
    m = (ax > az) & (ax >= ay) & (x > 0); names[m] = "east";  u[m] = z[m] / ax[m];  v[m] = -y[m] / ax[m]
    m = (ax > az) & (ax >= ay) & (x <= 0); names[m] = "west"; u[m] = -z[m] / ax[m]; v[m] = -y[m] / ax[m]
    m = (ay > ax) & (ay > az) & (y > 0); names[m] = "top";    u[m] = x[m] / ay[m];  v[m] = -z[m] / ay[m]
    m = (ay > ax) & (ay > az) & (y <= 0); names[m] = "bottom"; u[m] = x[m] / ay[m]; v[m] = z[m] / ay[m]
    return names, u, v


def main():
    img = np.zeros((2 * S, 3 * S, 3), np.float32)
    ij = (np.arange(S, dtype=np.float32) + 0.5) / S * 2 - 1
    u, v = np.meshgrid(ij, ij)  # v increases downward (row index)

    for name, (col, row, fn) in FACES.items():
        print("nebula:", name, flush=True)
        dx, dy, dz = fn(u, v)
        d = np.stack([dx, dy, dz], -1).astype(np.float32)
        d /= np.linalg.norm(d, axis=-1, keepdims=True)
        img[row * S:(row + 1) * S, col * S:(col + 1) * S] = nebula(d)

    # stars: uniform points on the sphere, a few more near the galactic band
    rng = np.random.default_rng(SEED)
    n_stars = 7000
    d = rng.normal(size=(n_stars, 3)).astype(np.float32)
    d /= np.linalg.norm(d, axis=1, keepdims=True)
    mag = rng.random(n_stars).astype(np.float32) ** 4          # most stars dim, few bright
    tint = rng.choice(3, n_stars, p=[0.6, 0.25, 0.15])
    tints = np.array([[1, 1, 1], [0.7, 0.85, 1.0], [1.0, 0.85, 0.65]], np.float32)
    names, su, sv = direction_to_face(d)
    for k in range(n_stars):
        col, row, _ = FACES[names[k]]
        px = (su[k] + 1) / 2 * S
        py = (sv[k] + 1) / 2 * S
        r = 1 + int(mag[k] > 0.35) + int(mag[k] > 0.8)
        sigma = 0.55 + 0.9 * mag[k]
        x0, y0 = int(px), int(py)
        for yy in range(y0 - r, y0 + r + 1):
            for xx in range(x0 - r, x0 + r + 1):
                if 0 <= xx < S and 0 <= yy < S:
                    g = np.exp(-((xx + 0.5 - px) ** 2 + (yy + 0.5 - py) ** 2) / (2 * sigma ** 2))
                    img[row * S + yy, col * S + xx] += tints[tint[k]] * g * (0.25 + 0.75 * mag[k] ** 0.5)

    out = (np.clip(img, 0, 1) ** (1 / 1.0) * 255).astype(np.uint8)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(out, "RGB").save(OUT, optimize=True)
    print("wrote", OUT, OUT.stat().st_size // 1024, "KiB")


if __name__ == "__main__":
    main()
