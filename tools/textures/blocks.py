"""Block textures for Steve's Space Program, batch 1: hull, insulation, tanks, engines.

Run:  python3 tools/textures/blocks.py   (writes PNGs + a preview sheet)
Every texture here is checked in the preview next to vanilla iron, concrete and stone.
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


# ------------------------------------------------------------------ hull plating
def painted_panel(base, seed, mottling=3.0, seam_dark=-16, fasteners=True):
    """Painted aluminium panel: almost flat colour, faint mottling, a seam at the edge."""
    img = fill(hexcol(base))
    shade(img, value_noise(seed, cell=8) * mottling + speckle(seed + 1) * 1.2)
    seam(img, dark=seam_dark, light=6)
    if fasteners:
        for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
            rivet(img, x, y, light=7, dark=-9)
    return img


@texture('hull_white')
def hull_white():
    return painted_panel('#d8d5ce', 11)


@texture('hull_grey')
def hull_grey():
    return painted_panel('#9da1a2', 12)


@texture('hull_dark')
def hull_dark():
    # Dark grey composite panel: same structure as the painted panels, slightly rougher.
    img = fill(hexcol('#45484d'))
    shade(img, value_noise(13, cell=8) * 2.5 + speckle(14) * 2.0)
    seam(img, dark=-11, light=6)
    for x, y in ((2, 2), (12, 2), (2, 12), (12, 12)):
        rivet(img, x, y, light=6, dark=-6)
    return img


@texture('hull_white_band')
def hull_white_band():
    # White panel with a narrow dark band across the middle (rocket roll markings).
    img = painted_panel('#d8d5ce', 14, fasteners=False)
    band = np.zeros((16, 16))
    band[7:9, :] = 1
    tint_towards(img, hexcol('#3b3e43'), 1.0, mask=band)
    shade(img, speckle(15) * 1.2, mask=band)
    img[6, :15, :3] -= 6    # soft shadow line above the band
    return img


@texture('heat_shield')
def heat_shield():
    # Black ceramic tiles, four per block, each a slightly different shade (like the Shuttle).
    img = fill(hexcol('#2a2b2e'))
    r = rng(21)
    for ty in range(2):
        for tx in range(2):
            x0, y0 = tx * 8, ty * 8
            img[y0:y0 + 8, x0:x0 + 8, :3] += r.uniform(-5, 6)
    shade(img, value_noise(22, cell=4) * 3 + speckle(23) * 2)
    for t in (0, 8):
        img[t, :, :3] += 7         # top edge of each tile row catches the light
        img[:, t, :3] += 5
        img[(t + 7) % 16, :, :3] -= 9
        img[:, (t + 7) % 16, :3] -= 9
    return img


@texture('insulation_foil')
def insulation_foil():
    # Gold multi-layer insulation: crinkled foil made of small flat facets, each catching the
    # light a little differently, with soft creases between them. Tiles seamlessly.
    r = rng(31)
    img = fill(hexcol('#b08a40'))
    points = r.uniform(0, 16, (10, 2))
    facet = r.uniform(-1, 1, len(points))
    y, x = np.mgrid[0:16, 0:16].astype(float) + 0.5
    dist = []
    for px, py in points:
        dx = np.abs(x - px)
        dy = np.abs(y - py)
        dx = np.minimum(dx, 16 - dx)   # wrap around: seamless tiling
        dy = np.minimum(dy, 16 - dy)
        dist.append(np.sqrt(dx * dx + dy * dy))
    dist = np.array(dist)
    order = np.argsort(dist, axis=0)
    nearest = order[0]
    gap = np.take_along_axis(dist, order[1:2], 0)[0] - np.take_along_axis(dist, order[0:1], 0)[0]
    shade(img, facet[nearest] * 7 + speckle(33) * 1.5)
    crease = gap < 0.8
    # Creases: a fine darker fold, lit on the side facing the top-left light.
    lit = (facet[nearest] > 0)
    shade(img, np.where(lit, 5.0, -7.0), mask=crease.astype(float))
    return img


@texture('aluminium_frame')
def aluminium_frame():
    # Structural frame block: brushed aluminium border around a darker recessed centre.
    img = fill(hexcol('#b9bcbd'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.sin(y * 2.1) * 1.5 + speckle(41) * 2)   # brushing
    rect(img, 3, 3, 12, 12, hexcol('#9a9ea0'))
    shade(img, speckle(42) * 2, mask=((x >= 3) & (x <= 12) & (y >= 3) & (y <= 12)))
    frame(img, 3, 3, 12, 12, hexcol('#878b8d'))
    img[3, 3:13, :3] -= 4     # inner shadow under the top edge
    img[12, 3:13, :3] += 6    # lit bottom lip of the recess
    bevel(img, light=9, dark=-14)
    for px, py in ((1, 1), (14, 1), (1, 14), (14, 14)):
        rivet(img, px, py, light=6, dark=-8)
    return img


# ------------------------------------------------------------------ tanks
@texture('propellant_tank_side')
def tank_side():
    # White tank wall with weld seams at the top and bottom of each block section.
    img = painted_panel('#dedbd4', 51, mottling=2.5, seam_dark=0, fasteners=False)
    img[0, :, :3] -= 10
    img[1, :, :3] += 6
    img[15, :, :3] -= 12
    # Subtle vertical curvature shading: tanks are round, the left side faces the light.
    x = np.arange(16)
    shade(img, np.tile(np.interp(x, [0, 5, 15], [4, 3, -8]), (16, 1)))
    return img


@texture('propellant_tank_top')
def tank_top():
    img = fill(hexcol('#c9c7c1'))
    shade(img, value_noise(52, cell=8) * 2.5 + speckle(53) * 1.5)
    y, x = np.mgrid[0:16, 0:16]
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    ring = (d > 5.2) & (d < 6.3)
    shade(img, -14, mask=ring)
    shade(img, 8, mask=(d > 4.4) & (d <= 5.2) & (y < 8))
    for a in range(8):
        ang = a * np.pi / 4
        rivet(img, int(round(7.5 + 6.8 * np.cos(ang))), int(round(7.5 + 6.8 * np.sin(ang))), light=8, dark=-8)
    rect(img, 6, 6, 9, 9, hexcol('#8f9294'))     # fill valve
    img[6, 6:10, :3] += 10
    seam(img, dark=-10, light=4)
    return img


# ------------------------------------------------------------------ engines
@texture('engine_bell')
def engine_bell():
    # Regeneratively cooled nozzle: dark heat-tinted metal with fine vertical cooling tubes.
    img = fill(hexcol('#4a4440'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.where(x % 2 == 0, 4, -4))
    shade(img, value_noise(61, cell=8) * 3 + speckle(62) * 2)
    # Heat tint: bronze near the throat (top), blued toward the exit (bottom).
    tint_towards(img, hexcol('#7a5a3c'), np.clip((8 - y) / 10, 0, 0.45))
    tint_towards(img, hexcol('#3e4452'), np.clip((y - 8) / 12, 0, 0.35))
    return img


@texture('engine_body')
def engine_body():
    # Turbopump housing: grey machined metal with a pipe and a flange.
    img = fill(hexcol('#8b8e90'))
    shade(img, value_noise(71, cell=8) * 3 + speckle(72) * 2)
    rect(img, 0, 3, 15, 4, hexcol('#6f7274'))    # flange band
    img[3, :, :3] += 12
    rect(img, 9, 6, 11, 15, hexcol('#a8834e'))    # copper feed pipe
    img[6:16, 9, :3] += 10
    img[6:16, 11, :3] -= 12
    for px in (2, 6, 13):
        rivet(img, px, 3, light=10, dark=-10)
    seam(img, dark=-12, light=4)
    return img


@texture('engine_bell_inside')
def engine_bell_inside():
    # Looking into the nozzle: soot-dark with a faint warm glow at the centre.
    img = fill(hexcol('#2a2624'))
    y, x = np.mgrid[0:16, 0:16]
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    shade(img, np.clip(8 - d * 1.5, 0, 8))
    tint_towards(img, hexcol('#5a3a22'), np.clip(1 - d / 6, 0, 0.5))
    shade(img, speckle(81) * 2)
    return img


@texture('vacuum_engine_bell')
def vacuum_engine_bell():
    # Radiatively cooled niobium extension: dark grey with a violet heat tint, smooth bands.
    img = fill(hexcol('#504c56'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.where(y % 4 == 0, -5, 0))
    shade(img, value_noise(91, cell=8) * 3 + speckle(92) * 2)
    tint_towards(img, hexcol('#5c4a72'), np.clip((y - 4) / 14, 0, 0.35))
    return img


@texture('vacuum_engine_body')
def vacuum_engine_body():
    # A lighter titanium housing with a ring of small radiator fins.
    img = fill(hexcol('#9ea2a6'))
    shade(img, value_noise(93, cell=8) * 3 + speckle(94) * 2)
    rect(img, 0, 3, 15, 4, hexcol('#7e8286'))
    img[3, :, :3] += 12
    for px in range(1, 16, 3):
        rect(img, px, 7, px, 13, hexcol('#83878b'))
        img[7:14, px, :3] += 8
    seam(img, dark=-12, light=4)
    return img


@texture('nuclear_engine_body')
def nuclear_engine_body():
    # Reactor housing: thick dark grey shielding, a yellow band, bolted access plate.
    img = fill(hexcol('#5c5f62'))
    shade(img, value_noise(95, cell=8) * 3 + speckle(96) * 2)
    rect(img, 0, 2, 15, 4, hexcol('#b89a3c'))
    img[2, :, :3] += 10
    img[4, :, :3] -= 10
    rect(img, 4, 7, 11, 13, hexcol('#4e5154'))
    for px, py in ((5, 8), (10, 8), (5, 12), (10, 12)):
        rivet(img, px, py, light=10, dark=-10)
    seam(img, dark=-12, light=4)
    return img


@texture('carbon_hull')
def carbon_hull():
    # Carbon-fibre panel: a fine dark twill weave, satin sheen, edge seams.
    img = fill(hexcol('#323336'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.where(((x + y) // 2) % 2 == 0, 5, -3))
    shade(img, value_noise(97, cell=8) * 2)
    seam(img, dark=-10, light=6)
    return img


def preview(names):
    """Sheet: each texture tiled 3x3 at 6x, beside vanilla neighbours, so seams and contrast
    can be judged the way they appear in a wall."""
    from PIL import Image
    vanilla = os.path.expanduser('~/.cache/stevespace-ref/vanilla/assets/minecraft/textures/block')
    refs = ['iron_block', 'white_concrete', 'smooth_stone', 'gold_block', 'polished_blackstone']
    tiles = [(n, Image.open(os.path.join(OUT, n + '.png')).convert('RGBA')) for n in names]
    tiles += [('vanilla:' + n, Image.open(os.path.join(vanilla, n + '.png')).convert('RGBA').crop((0, 0, 16, 16))) for n in refs]
    scale, cols = 5, 5
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
    sheet.save(os.path.join(path, 'blocks_batch1.png'))
    return os.path.join(path, 'blocks_batch1.png')


if __name__ == '__main__':
    os.makedirs(OUT, exist_ok=True)
    for name, fn in TEXTURES.items():
        im = to_image(fn())
        im.save(os.path.join(OUT, name + '.png'))
        mean, std = luminance_stats(im)
        print(f'{name:24s} mean {mean:6.1f}  std {std:5.1f}')
    print(preview(list(TEXTURES)))
