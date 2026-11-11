"""Generates the dense storm cloud masks (tileable 256x256, binary alpha like the other layers).
Run: python3 tools/generate_storm_clouds.py   (needs numpy + Pillow)"""
import numpy as np
from PIL import Image

OUT = "src/main/resources/assets/skybox-overhaul/textures/environment/"
N = 256

def periodic_noise(seed, scale):
    """FFT-filtered white noise: periodic by construction, blobs of roughly `scale` cells."""
    rng = np.random.default_rng(seed)
    f = np.fft.fft2(rng.standard_normal((N, N)))
    fy = np.fft.fftfreq(N)[:, None]
    fx = np.fft.fftfreq(N)[None, :]
    r = np.sqrt(fx * fx + fy * fy) * N  # cycles per tile
    f *= np.exp(-(r * scale / N * 2.2) ** 2)  # gaussian low-pass
    n = np.real(np.fft.ifft2(f))
    return (n - n.mean()) / n.std()

def mask(seed, coverage):
    n = periodic_noise(seed, 9) + 0.5 * periodic_noise(seed + 1, 4) + 0.2 * periodic_noise(seed + 2, 2)
    thr = np.quantile(n, 1.0 - coverage)
    return n > thr

for name, seed, coverage in (("clouds_storm_1.png", 11, 0.75), ("clouds_storm_2.png", 29, 0.92)):
    m = mask(seed, coverage)
    a = np.zeros((N, N, 4), dtype=np.uint8)
    a[m] = 255
    Image.fromarray(a, "RGBA").save(OUT + name, optimize=True)
    print(name, "coverage", round(float(m.mean()), 3))
