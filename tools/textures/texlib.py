"""Small toolkit for making Minecraft-style 16x16 textures that sit well next to vanilla.

Rules we follow (measured from vanilla blocks):
  * smooth man-made materials: brightness std 6-20 (quartz 6, iron 18), never pure black/white
  * detail is 1 pixel wide, shading is 1-2 steps of a small palette, no gradients across a face
  * light comes from the top-left: highlights on top/left edges, shadows bottom/right
"""
import numpy as np
from PIL import Image

SIZE = 16


def rng(seed):
    return np.random.default_rng(seed)


def hexcol(h):
    h = h.lstrip('#')
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=float)


def value_noise(seed, size=SIZE, cell=4, octaves=2, tile=True):
    """Smooth tileable noise in [-1, 1]."""
    r = rng(seed)
    out = np.zeros((size, size))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        c = max(1, cell >> o)
        n = size // c
        grid = r.uniform(-1, 1, (n + 1, n + 1))
        if tile:
            grid[n, :] = grid[0, :]
            grid[:, n] = grid[:, 0]
        ys, xs = np.mgrid[0:size, 0:size] / c
        x0, y0 = xs.astype(int), ys.astype(int)
        fx, fy = xs - x0, ys - y0
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        v = (grid[y0, x0] * (1 - fx) * (1 - fy) + grid[y0, x0 + 1] * fx * (1 - fy)
             + grid[y0 + 1, x0] * (1 - fx) * fy + grid[y0 + 1, x0 + 1] * fx * fy)
        out += v * amp
        total += amp
        amp *= 0.5
    return out / total


def speckle(seed, size=SIZE):
    """Per-pixel white noise in [-1, 1] (vanilla's fine grain)."""
    return rng(seed).uniform(-1, 1, (size, size))


def fill(color):
    img = np.zeros((SIZE, SIZE, 4))
    img[:, :, :3] = color
    img[:, :, 3] = 255
    return img


def shade(img, amount, mask=None):
    """Brightness change in levels (+ brighter). amount may be an array."""
    a = np.broadcast_to(np.asarray(amount, dtype=float), (SIZE, SIZE))
    if mask is not None:
        a = a * mask
    img[:, :, :3] += a[:, :, None]
    return img


def tint_towards(img, color, weight, mask=None):
    w = np.broadcast_to(np.asarray(weight, dtype=float), (SIZE, SIZE))
    if mask is not None:
        w = w * mask
    img[:, :, :3] = img[:, :, :3] * (1 - w[:, :, None]) + color * w[:, :, None]
    return img


def bevel(img, light=10, dark=-12, inset=0, size=SIZE):
    """Raised square: highlight top/left edge, shadow bottom/right edge."""
    a, b = inset, size - 1 - inset
    img[a, a:b + 1, :3] += light
    img[a:b + 1, a, :3] += light
    img[b, a:b + 1, :3] += dark
    img[a:b + 1, b, :3] += dark
    return img


def seam(img, dark=-18, light=8):
    """Panel seam on the bottom/right edge (so tiled blocks show a seam grid), with a
    soft highlight just inside the top/left edge."""
    img[SIZE - 1, :, :3] += dark
    img[:, SIZE - 1, :3] += dark
    img[0, :SIZE - 1, :3] += light
    img[:SIZE - 1, 0, :3] += light
    return img


def rivet(img, x, y, light=14, dark=-14):
    img[y, x, :3] += light
    if x + 1 < SIZE and y + 1 < SIZE:
        img[y + 1, x + 1, :3] += dark
    return img


def rect(img, x0, y0, x1, y1, color):
    img[y0:y1 + 1, x0:x1 + 1, :3] = color
    img[y0:y1 + 1, x0:x1 + 1, 3] = 255
    return img


def frame(img, x0, y0, x1, y1, color):
    img[y0, x0:x1 + 1, :3] = color
    img[y1, x0:x1 + 1, :3] = color
    img[y0:y1 + 1, x0, :3] = color
    img[y0:y1 + 1, x1, :3] = color
    return img


def to_image(img):
    arr = np.clip(np.round(img), 0, 255).astype(np.uint8)
    return Image.fromarray(arr, 'RGBA')


def luminance_stats(img):
    a = np.asarray(img).astype(float)
    mask = a[:, :, 3] > 0
    lum = (a[:, :, :3] @ [0.299, 0.587, 0.114])[mask]
    return lum.mean(), lum.std()
