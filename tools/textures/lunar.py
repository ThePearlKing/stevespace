"""Ground textures for the Moon (regolith, mare regolith, anorthosite) and Mars (dust, rock, basalt sand).

Run:  python3 tools/textures/lunar.py   (writes PNGs + tools/previews/lunar.png)
Matched against vanilla gravel, concrete powder, stone and smooth basalt: same grain size,
similar brightness spread, flat lighting (natural ground, no bevels or edges).
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from texlib import *  # noqa: E402,F401

OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/stevespace/textures/block')
TEXTURES = {}


def texture(name):
    def register(fn):
        TEXTURES[name] = fn
        return fn
    return register


def grains(img, seed, count, light, dark):
    """Scattered single-pixel grains, some lighter and some darker than the dust."""
    r = rng(seed)
    for _ in range(count):
        x, y = r.integers(0, SIZE, 2)
        img[y, x, :3] += light if r.random() < 0.5 else dark
    return img


def pebbles(img, seed, count, dark, highlight):
    """A few two-pixel stones: dark body with a lit top-left pixel."""
    r = rng(seed)
    for _ in range(count):
        x, y = r.integers(0, SIZE - 1, 2)
        img[y + 1, x + 1, :3] += dark
        img[y + 1, x, :3] += dark * 0.6
        img[y, x + 1, :3] += highlight
    return img


@texture('regolith')
def regolith():
    # Highland dust: pale, slightly warm grey powder, finer than gravel.
    img = fill(hexcol('#9c9a95'))
    shade(img, value_noise(101, cell=8, octaves=3) * 9 + speckle(102) * 10)
    grains(img, 103, 30, 16, -16)
    pebbles(img, 104, 4, -22, 10)
    return img


@texture('mare_regolith')
def mare_regolith():
    # Mare dust: darker basaltic grey with a cool cast.
    img = fill(hexcol('#605f60'))
    shade(img, value_noise(111, cell=8, octaves=3) * 7 + speckle(112) * 9)
    grains(img, 113, 30, 14, -12)
    pebbles(img, 114, 4, -16, 9)
    return img


@texture('anorthosite')
def anorthosite():
    # Highland bedrock: light grey stone with paler feldspar flecks, like a softer diorite.
    img = fill(hexcol('#aeaca7'))
    shade(img, value_noise(121, cell=4, octaves=3) * 10 + speckle(122) * 7)
    r = rng(123)
    for _ in range(9):
        # small irregular clusters of pale crystal
        x, y = r.integers(0, SIZE - 1, 2)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
            if r.random() < 0.6:
                img[(y + dy) % SIZE, (x + dx) % SIZE, :3] += 16
    grains(img, 124, 22, 0, -18)
    return img


@texture('mars_regolith')
def mars_regolith():
    # Martian dust: muted rust, fine grain, a few darker grit grains.
    img = fill(hexcol('#a35d3c'))
    shade(img, value_noise(131, cell=8, octaves=3) * 8 + speckle(132) * 9)
    grains(img, 133, 28, 13, -14)
    pebbles(img, 134, 3, -18, 9)
    return img


@texture('mars_rock')
def mars_rock():
    # Bare Martian rock: dark red-brown basalt with paler weathered flecks.
    img = fill(hexcol('#6e4636'))
    shade(img, value_noise(141, cell=4, octaves=3) * 9 + speckle(142) * 7)
    grains(img, 143, 24, 12, -12)
    return img


@texture('basalt_sand')
def basalt_sand():
    # Dark basaltic sand of the volcanic plains and dunes.
    img = fill(hexcol('#4e3c36'))
    shade(img, value_noise(151, cell=8, octaves=3) * 6 + speckle(152) * 9)
    grains(img, 153, 30, 11, -10)
    return img


def preview(names):
    from PIL import Image
    vanilla = os.path.expanduser('~/.cache/stevespace-ref/vanilla/assets/minecraft/textures/block')
    refs = ['red_sand', 'gravel', 'terracotta', 'smooth_basalt', 'stone']
    tiles = [(n, Image.open(os.path.join(OUT, n + '.png')).convert('RGBA')) for n in names]
    tiles += [('vanilla:' + n, Image.open(os.path.join(vanilla, n + '.png')).convert('RGBA').crop((0, 0, 16, 16))) for n in refs]
    scale, cols = 5, 4
    cell = 48 * scale + 10
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * cell), (40, 44, 52, 255))
    for i, (n, t) in enumerate(tiles):
        wall = Image.new('RGBA', (48, 48))
        for a in range(3):
            for b in range(3):
                wall.paste(t, (a * 16, b * 16))
        sheet.paste(wall.resize((48 * scale, 48 * scale), Image.NEAREST), ((i % cols) * cell, (i // cols) * cell))
    path = os.path.join(os.path.dirname(__file__), '../previews')
    os.makedirs(path, exist_ok=True)
    sheet.save(os.path.join(path, 'lunar.png'))
    return os.path.join(path, 'lunar.png')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in TEXTURES.items():
        im = to_image(fn())
        im.save(os.path.join(OUT, name + '.png'))
        mean, std = luminance_stats(im)
        print(f'{name:24s} mean {mean:6.1f}  std {std:5.1f}')
    print(preview(list(TEXTURES)))
