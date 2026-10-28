"""Real Earth's height map for the "Real Earth" world type, from NOAA's ETOPO5 (5 arc-minute
land and sea-floor elevations, public domain: https://www.ngdc.noaa.gov/mgg/global/etopo5.HTML).

Writes src/main/resources/data/stevespace/earth/elevation.png: equirectangular, 4096 x 2048,
longitude -180..180 left to right, latitude 90..-90 top to bottom (the mod's map layout). One
byte per point: 128 + 127*sqrt(h/8800) above sea level, 127 - 127*sqrt(-h/11000) below it, so
low ground and shallow sea keep fine steps.

Run:  python3 tools/earth/make_dem.py path/to/ETOPO5.DAT
"""
import os
import sys

import numpy as np
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/data/stevespace/earth/elevation.png')
W, H = 4096, 2048


def main():
    raw = np.fromfile(sys.argv[1], dtype='>i2').reshape(2160, 4320).astype(np.float64)
    # ETOPO5 columns start at 0 E: turn so they start at 180 W.
    raw = np.roll(raw, 2160, axis=1)
    # Resample (area-weighted enough at this ratio: bilinear on cell centres).
    ys = (np.arange(H) + 0.5) / H * 2160 - 0.5
    xs = (np.arange(W) + 0.5) / W * 4320 - 0.5
    y0 = np.clip(np.floor(ys).astype(int), 0, 2158)
    fy = np.clip(ys - y0, 0, 1)[:, None]
    x0 = np.floor(xs).astype(int) % 4320
    x1 = (x0 + 1) % 4320
    fx = (xs - np.floor(xs))[None, :]
    a = raw[y0][:, x0] * (1 - fx) + raw[y0][:, x1] * fx
    b = raw[y0 + 1][:, x0] * (1 - fx) + raw[y0 + 1][:, x1] * fx
    h = a * (1 - fy) + b * fy
    v = np.where(h >= 0, 128 + 127 * np.sqrt(np.clip(h, 0, 8800) / 8800),
                 127 - 127 * np.sqrt(np.clip(-h, 0, 11000) / 11000))
    img = Image.fromarray(np.round(v).astype(np.uint8), 'L')
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT, optimize=True)
    print('wrote', OUT, os.path.getsize(OUT), 'bytes; land', round(float((h >= 0).mean()), 3))


if __name__ == '__main__':
    main()
