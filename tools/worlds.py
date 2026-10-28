"""The ground of every world: one table of blocks, their textures, and everything Minecraft needs.

Each row is a block: id, English name, behaviour, map colour, and a texture recipe. Running this
writes the textures, block states, models, item models, loot tables, tags, names and the Java
registration table (registry/WorldBlockTable.java). Add a block here, run it, done.

    python3 tools/worlds.py            # everything
    python3 tools/worlds.py preview    # also a preview sheet beside vanilla blocks

Textures follow the house rules (tools/textures/texlib.py): natural ground has flat lighting,
1-pixel detail, a small palette and a brightness spread close to its vanilla cousin
(stone ~10, sand ~8, ice ~12). No outlines, no bevels on natural blocks.
"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.join(os.path.dirname(__file__), 'textures'))
from texlib import *  # noqa: E402,F401

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/stevespace')
DATA = os.path.join(ROOT, 'src/main/resources/data/stevespace')
JAVA = os.path.join(ROOT, 'src/main/java/com/thepearlking/stevespace/registry/WorldBlockTable.java')
NS = 'stevespace'


# ---------------------------------------------------------------------------------------------
# Texture recipes: natural materials
# ---------------------------------------------------------------------------------------------

def grains(img, seed, count, light, dark):
    r = rng(seed)
    for _ in range(count):
        x, y = r.integers(0, SIZE, 2)
        img[y, x, :3] += light if r.random() < 0.5 else dark
    return img


def tones(seed, cell=4, octaves=2, levels=2):
    """Patchy tones like vanilla stone: noise scaled to unit spread, then cut into a few steps
    (-levels..levels). Patches are 2-5 pixels across and tile seamlessly."""
    n = value_noise(seed, cell=cell, octaves=octaves + 1)
    n = n / max(1e-6, n.std())
    return np.clip(np.round(n * 0.9), -levels, levels)


def dust(base, seed, step=7, pebble=4):
    """Loose fine ground (regolith, sand, ash): a fine two-tone grain over gentle patches,
    plus a few small stones with a lit top and a shadow below."""
    img = fill(hexcol(base))
    shade(img, tones(seed, cell=8, octaves=2, levels=1) * step * 0.6)
    fine = rng(seed + 1).integers(-1, 2, (SIZE, SIZE))
    shade(img, fine * step)
    r = rng(seed + 3)
    for _ in range(pebble):
        x, y = r.integers(0, SIZE - 1, 2)
        img[y, x, :3] += step * 1.6
        img[y + 1, x, :3] -= step * 1.8
        img[y + 1, (x + 1) % SIZE, :3] -= step
    return img


def rock(base, seed, step=9, strata=0.0, cracks=0):
    """Solid rock: vanilla-stone-like patches in four or five tones, a little grain; optional
    soft layering (sedimentary or folded rock) and a few hairline cracks."""
    img = fill(hexcol(base))
    shade(img, tones(seed) * step + rng(seed + 1).integers(-1, 2, (SIZE, SIZE)) * step * 0.25)
    if strata:
        y = np.arange(SIZE)[:, None] + value_noise(seed + 2, cell=8) * 2.0
        shade(img, np.round(np.sin(y * (2 * np.pi / 5.33)) * 1.2) * strata)
    r = rng(seed + 3)
    for _ in range(cracks):
        x, y = r.integers(0, SIZE, 2)
        for _ in range(r.integers(3, 7)):
            img[y % SIZE, x % SIZE, :3] -= step * 1.6
            img[y % SIZE, (x + 1) % SIZE, :3] += step * 0.5
            x += r.integers(-1, 2)
            y += 1
    return img


def vesicular(base, seed, step=8, holes=14):
    """Basalt and scoria: rock pocked with small gas holes (dark pit, lit lower rim)."""
    img = rock(base, seed, step=step)
    r = rng(seed + 9)
    for _ in range(holes):
        x, y = r.integers(0, SIZE - 1, 2)
        img[y, x, :3] -= step * 2.6
        if r.random() < 0.5:
            img[y, x + 1, :3] -= step * 1.8
        img[y + 1, x, :3] += step * 1.0
    return img


def glassy(base, seed, sheen=24):
    """Natural glass (obsidian-like): very dark, with pale curved fracture highlights."""
    img = fill(hexcol(base))
    shade(img, tones(seed, cell=8) * 4)
    r = rng(seed + 2)
    for _ in range(3):
        cx, cy = r.integers(0, SIZE, 2)
        rad = r.integers(2, 5)
        a0 = r.uniform(0, 2 * np.pi)
        for a in np.linspace(a0, a0 + 1.6, 7):
            x, y = int(round(cx + np.cos(a) * rad)) % SIZE, int(round(cy - np.sin(a) * rad)) % SIZE
            img[y, x, :3] += sheen
        img[cy % SIZE, cx % SIZE, :3] -= 6
    return img


def ice(base, seed, cracks=3, bubbles=6, step=6):
    """Ice: smooth cloudy patches, long thin cracks with a bright edge, trapped bubbles."""
    img = fill(hexcol(base))
    shade(img, tones(seed, cell=8, levels=2) * step)
    r = rng(seed + 2)
    for _ in range(cracks):
        x, y = r.integers(0, SIZE, 2)
        dx = r.choice([-1, 1])
        for _ in range(r.integers(6, 12)):
            img[y % SIZE, x % SIZE, :3] -= step * 1.8
            img[(y + 1) % SIZE, x % SIZE, :3] += step * 0.8
            x += dx
            y += 1 if r.random() < 0.5 else 0
    grains(img, seed + 3, bubbles, step * 2.2, 0)
    return img


def frost(base, seed, sparkle=14, step=5):
    """Frost and snowy crusts: bright soft patches, small shadowed hollows, glints."""
    img = fill(hexcol(base))
    shade(img, tones(seed, cell=4, levels=2) * step)
    r = rng(seed + 1)
    for _ in range(10):
        x, y = r.integers(0, SIZE, 2)
        img[y, x, :3] -= step * 2
        img[(y + 1) % SIZE, x, :3] -= step
    grains(img, seed + 2, 12, sparkle, 0)
    return img


def crystalline(base, seed, facet=12):
    """Crystal masses (sulphur, salts): small blocky crystals, lit on their upper-left faces."""
    img = fill(hexcol(base))
    shade(img, tones(seed, cell=4) * facet * 0.4)
    r = rng(seed + 1)
    for _ in range(12):
        x, y = r.integers(0, SIZE - 2, 2)
        w, h = r.integers(2, 4, 2)
        img[y:y + h, x:x + w, :3] += r.uniform(-facet * 0.6, facet * 0.6)
        img[y, x:x + w, :3] += facet * 0.7
        img[y:y + h, x, :3] += facet * 0.4
        img[min(SIZE - 1, y + h), x:x + w, :3] -= facet * 0.8
    return img


def glowing(base, glow, seed):
    """Rock split by hot glowing cracks that branch and fade toward their tips."""
    img = vesicular(base, seed, step=7, holes=6)
    r = rng(seed + 5)
    g = hexcol(glow)
    for _ in range(2):
        x, y = r.integers(0, SIZE, 2)
        dx, dy = r.choice([-1, 1]), r.choice([-1, 1])
        length = r.integers(10, 16)
        for i in range(length):
            heat = 1 - i / length * 0.7
            img[y % SIZE, x % SIZE, :3] = g * heat + hexcol(base) * (1 - heat) + r.uniform(-10, 10)
            if r.random() < 0.55:
                x += dx
            else:
                y += dy
            if r.random() < 0.15:
                dx = -dx if r.random() < 0.5 else dx
                dy = -dy if r.random() < 0.5 else dy
    return img


def pebbles(base, seed, step=10, count=11, gap='#000000'):
    """Rounded cobbles and pebbles (Titan's ice pebbles, breccia clasts): each one lit on its
    upper left with a shadow on the lower right, set in finer ground."""
    img = dust(base, seed, step=step * 0.5, pebble=0)
    r = rng(seed + 7)
    for _ in range(count):
        cx, cy = r.integers(0, SIZE, 2)
        rad = r.uniform(1.2, 2.6)
        tone = r.uniform(-step, step)
        for dy in range(-3, 4):
            for dx in range(-3, 4):
                d = (dx * dx + dy * dy) ** 0.5
                if d <= rad:
                    x, y = (cx + dx) % SIZE, (cy + dy) % SIZE
                    light = (-dx - dy) / max(1.0, rad) * step * 0.6
                    img[y, x, :3] = hexcol(base) + tone + light + step * 0.4
                elif d <= rad + 0.9 and dx + dy > 0:
                    x, y = (cx + dx) % SIZE, (cy + dy) % SIZE
                    img[y, x, :3] -= step * 0.9
    return img


def layered(base, seed, step=8, bands=(), wobble=1.5):
    """Sedimentary layers: horizontal beds of slightly different tones that pinch and swell,
    with a little grain. {@code bands} are colour offsets from the base, top to bottom."""
    img = rock(base, seed, step=step * 0.6)
    warp = value_noise(seed + 4, cell=8) * wobble
    ys = np.arange(SIZE)[:, None] + warp
    bands = bands or (0, step, -step * 0.6, step * 0.4)
    idx = np.floor(ys / (SIZE / len(bands))).astype(int) % len(bands)
    for i, off in enumerate(bands):
        img[idx == i, :3] += off
    return img


def plates(base, seed, step=7, edge=12):
    """Chaos terrain ice: broken, tilted plates of different tones with dark cracks between."""
    img = fill(hexcol(base))
    r = rng(seed)
    pts = r.integers(0, SIZE, (6, 2))
    tones_ = r.uniform(-step, step, 6)
    for y in range(SIZE):
        for x in range(SIZE):
            d = [min(abs(x - px), SIZE - abs(x - px)) ** 2 + min(abs(y - py), SIZE - abs(y - py)) ** 2 for px, py in pts]
            o = np.argsort(d)
            img[y, x, :3] += tones_[o[0]]
            if d[o[1]] - d[o[0]] < 6:
                img[y, x, :3] -= edge
    grains(img, seed + 3, 8, step, -step * 0.5)
    return img


def dimpled(base, seed, step=8, pits=7):
    """Cantaloupe terrain: a crust of small round pits, each with a shadowed upper rim and a lit
    lower rim (light from the upper left)."""
    img = frost(base, seed, sparkle=6, step=step * 0.5)
    r = rng(seed + 5)
    for _ in range(pits):
        cx, cy = r.integers(0, SIZE, 2)
        for dy in range(-2, 3):
            for dx in range(-2, 3):
                d = (dx * dx + dy * dy) ** 0.5
                x, y = (cx + dx) % SIZE, (cy + dy) % SIZE
                if d < 1.3:
                    img[y, x, :3] -= step * 0.8
                elif d < 2.4:
                    img[y, x, :3] += (-step if dy < 0 or dx < 0 else step * 0.8) * 0.9
    return img


def beads(base, seed, step=12, count=40):
    """Volcanic glass beads (the orange soil of the Moon): a fine sand of tiny shiny spheres."""
    img = dust(base, seed, step=step * 0.4, pebble=0)
    r = rng(seed + 2)
    for _ in range(count):
        x, y = r.integers(0, SIZE, 2)
        img[y, x, :3] += step
        img[(y + 1) % SIZE, x, :3] -= step * 0.6
    return img


def blades(base, seed, step=9):
    """Bladed ice: tall thin fins with lit left faces and shadowed right faces."""
    img = fill(hexcol(base))
    r = rng(seed)
    x = 0
    while x < SIZE:
        w = int(r.integers(2, 4))
        tone = r.uniform(-step * 0.5, step * 0.5)
        for i in range(w):
            col = (x + i) % SIZE
            img[:, col, :3] += tone + (step * 0.7 if i == 0 else (-step * 0.9 if i == w - 1 else 0))
        x += w
    shade(img, value_noise(seed + 1, cell=8) * step * 0.4)
    grains(img, seed + 2, 6, step, 0)
    return img


def geyser_top(base, vent, seed):
    """A geyser vent: frosted ice around a dark central throat, rimmed with fresh frost."""
    img = frost(base, seed, sparkle=10, step=4)
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5
            if d < 3.2:
                img[y, x, :3] = hexcol(vent) + (d - 3) * 3
            elif d < 4.6:
                img[y, x, :3] += 14 if (x - c) + (y - c) < 0 else -6
    return img


def lava_vent_top(base, glow, seed):
    """A lava vent: dark spatter round a glowing throat, cooling to crust toward the rim."""
    img = vesicular(base, seed, step=7, holes=6)
    g, b = hexcol(glow), hexcol(base)
    r = rng(seed + 3)
    c = (SIZE - 1) / 2
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - c) ** 2 + (y - c) ** 2) ** 0.5 + r.uniform(-0.5, 0.5)
            if d < 2.6:
                img[y, x, :3] = g * (1.05 - d * 0.06)
            elif d < 4.4:
                heat = (4.4 - d) / 1.8 * 0.75
                img[y, x, :3] = g * heat + b * (1 - heat)
    return img


def ore(host, seed, mineral, lit, count=6):
    """An ore: host rock with mineral nuggets (3-5 pixels): a dark rim where they sit in the
    rock, the mineral colour, and one lit pixel on the upper left."""
    img = host
    r = rng(seed)
    m, l = hexcol(mineral), hexcol(lit)
    taken = set()
    for _ in range(count):
        x, y = r.integers(1, SIZE - 3, 2)
        # A random blob grown from one pixel, 2-5 pixels, no two nuggets touching.
        shape = [(0, 0)]
        target = r.integers(2, 6)
        while len(shape) < target:
            bx, by = shape[r.integers(0, len(shape))]
            dx, dy = [(1, 0), (0, 1), (-1, 0), (0, -1)][r.integers(0, 4)]
            if (bx + dx, by + dy) not in shape:
                shape.append((bx + dx, by + dy))
        cells = [((x + dx) % SIZE, (y + dy) % SIZE) for dx, dy in shape]
        if any(((cx + ox) % SIZE, (cy + oy) % SIZE) in taken for cx, cy in cells for ox in (-1, 0, 1) for oy in (-1, 0, 1)):
            continue
        taken.update(cells)
        for cx, cy in cells:
            img[(cy + 1) % SIZE, cx, :3] -= 14  # seat in the rock
        for cx, cy in cells:
            img[cy, cx, :3] = m + r.uniform(-8, 8)
        top = min(cells, key=lambda c: (c[1], c[0]))
        img[top[1], top[0], :3] = l
    return img


# ---------------------------------------------------------------------------------------------
# The blocks
# ---------------------------------------------------------------------------------------------
# kind: stone | soil | ice | glass | glow | vent | ore
# map: Minecraft MapColor constant (for maps and the block's dust particles)
# The texture is a function of nothing, so each block's look is written down right here.

BLOCKS = [
    # Venus: basalt plains weathered rusty by the hot air, folded pale tesserae highlands,
    # volcanoes of scoria and fresh glassy lava, and a silvery metallic frost on the peaks.
    ('venus_basalt', 'Venusian Basalt', 'stone', 'TERRACOTTA_GRAY', 2.0,
     lambda: vesicular('#4d4642', 501, step=8)),
    ('weathered_basalt', 'Weathered Basalt', 'stone', 'TERRACOTTA_BROWN', 1.6,
     lambda: rock('#735b4a', 502, step=9, cracks=2)),
    ('venus_dust', 'Venusian Dust', 'soil', 'TERRACOTTA_ORANGE', 0.6,
     lambda: dust('#83603f', 503, step=7)),
    ('tessera_rock', 'Tessera Rock', 'stone', 'TERRACOTTA_WHITE', 2.2,
     lambda: rock('#a89a83', 504, step=8, strata=7)),
    ('venus_scoria', 'Venusian Scoria', 'stone', 'TERRACOTTA_RED', 1.2,
     lambda: vesicular('#5e3c33', 505, step=9, holes=22)),
    ('lava_glass', 'Lava Glass', 'glass', 'COLOR_BLACK', 1.8,
     lambda: glassy('#302c29', 506, sheen=16)),
    ('metal_frost', 'Metal Frost', 'stone', 'METAL', 1.2,
     lambda: frost('#bdbab2', 507, sparkle=16)),
    ('vent_rock', 'Vent Rock', 'vent', 'NETHER', 1.6,
     lambda: glowing('#3a2a26', '#f08a32', 508)),
    ('telluride_ore', 'Telluride Ore', 'ore', 'TERRACOTTA_WHITE', 3.0,
     lambda: ore(rock('#a89a83', 504, step=8, strata=7), 509, '#5e5b55', '#ece6c6', count=7)),

    # The Moon (besides the regolith, mare regolith and anorthosite blocks): dark mare basalt
    # under the mare dust, breccia on crater floors, black impact melt glass in young craters,
    # the orange glass-bead soil, helium-3 rich regolith, and ice in the polar cold traps.
    ('mare_basalt', 'Mare Basalt', 'stone', 'DEEPSLATE', 1.8,
     lambda: vesicular('#4a4947', 701, step=7, holes=10)),
    ('lunar_breccia', 'Lunar Breccia', 'stone', 'STONE', 1.6,
     lambda: pebbles('#8e8c88', 702, step=9, count=9)),
    ('impact_melt', 'Impact Melt', 'glass', 'COLOR_BLACK', 1.6,
     lambda: glassy('#2f2e2d', 703, sheen=14)),
    ('orange_glass_beads', 'Orange Glass Beads', 'soil', 'TERRACOTTA_ORANGE', 0.5,
     lambda: beads('#9a6a3c', 704, step=11)),
    ('helium_regolith', 'Helium-3 Regolith', 'ore', 'COLOR_LIGHT_GRAY', 1.0,
     lambda: ore(dust('#8b8984', 705, step=6), 706, '#b9c2c6', '#eef4f6', count=6)),
    ('lunar_ice', 'Lunar Ice', 'ice', 'ICE', 0.8,
     lambda: ice('#a8acb0', 707, cracks=2, bubbles=3, step=5)),

    # Mercury: dark grey ground with a brownish cast, the very dark graphite-rich crust of the
    # old plains, bright bluish hollows, smooth lava plains, layered scarp walls, ilmenite, and
    # ice in the permanently dark polar craters.
    ('mercury_regolith', 'Mercurian Regolith', 'soil', 'COLOR_GRAY', 0.6,
     lambda: dust('#6e6a66', 711, step=6)),
    ('mercury_rock', 'Mercurian Rock', 'stone', 'COLOR_GRAY', 1.8,
     lambda: rock('#5f5c59', 712, step=8, cracks=1)),
    ('graphite_crust', 'Graphite Crust', 'stone', 'COLOR_BLACK', 1.6,
     lambda: rock('#3a3937', 713, step=6)),
    ('hollow_deposit', 'Hollow Deposit', 'soil', 'COLOR_LIGHT_BLUE', 0.5,
     lambda: frost('#a9b0b6', 714, sparkle=10, step=5)),
    ('plains_basalt', 'Plains Basalt', 'stone', 'TERRACOTTA_GRAY', 1.8,
     lambda: vesicular('#56524d', 715, step=7, holes=8)),
    ('scarp_rock', 'Scarp Rock', 'stone', 'TERRACOTTA_LIGHT_GRAY', 2.0,
     lambda: layered('#77716a', 716, step=7, bands=(4, -5, 8, -2))),
    ('ilmenite_ore', 'Ilmenite Ore', 'ore', 'COLOR_GRAY', 3.0,
     lambda: ore(rock('#5f5c59', 712, step=8, cracks=1), 717, '#2b2a2c', '#8f96a0', count=7)),
    ('mercury_ice', 'Polar Ice', 'ice', 'ICE', 0.8,
     lambda: ice('#9ea3a6', 718, cracks=3, bubbles=2, step=5)),

    # Mars (besides the regolith, rock and basalt sand blocks): fine bright storm dust, layered
    # sediment in the canyon walls, ancient lake clay, olivine sand, white gypsum dunes near
    # the north pole, dark volcanic basalt, hematite "blueberries", and polar ices.
    ('martian_dust', 'Martian Dust', 'soil', 'COLOR_ORANGE', 0.4,
     lambda: dust('#b0703f', 721, step=5, pebble=1)),
    ('layered_sediment', 'Layered Sediment', 'stone', 'TERRACOTTA_PINK', 1.4,
     lambda: layered('#a8735a', 722, step=8, bands=(6, -4, 10, -8, 2))),
    ('martian_clay', 'Martian Clay', 'soil', 'TERRACOTTA_LIGHT_GRAY', 0.8,
     lambda: rock('#8a7f6c', 723, step=5, cracks=3)),
    ('olivine_sand', 'Olivine Sand', 'soil', 'TERRACOTTA_GREEN', 0.5,
     lambda: dust('#6a6a44', 724, step=7)),
    ('gypsum_sand', 'Gypsum Sand', 'soil', 'TERRACOTTA_WHITE', 0.5,
     lambda: dust('#cbbca6', 725, step=5)),
    ('martian_basalt', 'Martian Basalt', 'stone', 'TERRACOTTA_GRAY', 2.0,
     lambda: vesicular('#4b3d38', 726, step=7)),
    ('hematite_ore', 'Hematite Concretions', 'ore', 'TERRACOTTA_RED', 2.2,
     lambda: ore(dust('#8f5a3e', 727, step=6), 728, '#4a4c58', '#9aa0b4', count=9)),
    ('martian_ice', 'Martian Ice', 'ice', 'ICE', 0.8,
     lambda: ice('#d8d2ca', 729, cracks=2, bubbles=4, step=5)),
    ('dry_ice_frost', 'Carbon Dioxide Frost', 'soil', 'SNOW', 0.3,
     lambda: frost('#e6e4e0', 730, sparkle=10, step=4)),

    # Io: sulphur everywhere in yellows, whites and reds; dark silicate lava and ash; mountains
    # of old crust; glowing lava crust around the paterae.
    ('sulphur', 'Sulphur', 'soil', 'COLOR_YELLOW', 0.6,
     lambda: crystalline('#c9b048', 731, facet=10)),
    ('sulphur_frost', 'Sulphur Dioxide Frost', 'soil', 'TERRACOTTA_WHITE', 0.4,
     lambda: frost('#ddd6b0', 732, sparkle=10, step=5)),
    ('red_sulphur', 'Red Sulphur', 'soil', 'TERRACOTTA_RED', 0.6,
     lambda: crystalline('#a2543a', 733, facet=9)),
    ('sulphur_deposit', 'Sulphur Deposit', 'ore', 'COLOR_YELLOW', 1.6,
     lambda: ore(rock('#8c7a4a', 734, step=7), 735, '#e2c84c', '#fff2a0', count=8)),
    ('ionian_basalt', 'Ionian Basalt', 'stone', 'COLOR_BLACK', 2.0,
     lambda: vesicular('#3b3632', 736, step=7)),
    ('silicate_crust', 'Silicate Crust', 'stone', 'TERRACOTTA_BROWN', 2.2,
     lambda: layered('#7d6a52', 737, step=7, bands=(3, -5, 6, -2))),
    ('lava_crust', 'Lava Crust', 'vent', 'NETHER', 1.6,
     lambda: glowing('#2e2622', '#f0a040', 738)),
    ('lava_vent', 'Lava Vent', 'lavaVent', 'NETHER', 2.0,
     lambda: lava_vent_top('#3b3632', '#f08a30', 740)),
    ('volcanic_ash', 'Volcanic Ash', 'soil', 'COLOR_GRAY', 0.5,
     lambda: dust('#4c4844', 739, step=6)),

    # Europa: white ice crossed by reddish-brown salt-stained bands, broken chaos plates, salt
    # crusts; under the crust clear deep ice, then a dark ocean over a rocky sea floor with
    # mineral chimneys at its hot vents.
    ('europan_ice', 'Europan Ice', 'ice', 'ICE', 1.2,
     lambda: ice('#d4d6d4', 741, cracks=3, bubbles=4, step=5)),
    ('europan_frost', 'Europan Frost', 'soil', 'SNOW', 0.4,
     lambda: frost('#e0dfd8', 742, sparkle=12, step=4)),
    ('lineae_ice', 'Lineae Ice', 'ice', 'TERRACOTTA_BROWN', 1.2,
     lambda: ice('#9a7458', 743, cracks=4, bubbles=2, step=6)),
    ('chaos_ice', 'Chaos Ice', 'ice', 'TERRACOTTA_WHITE', 1.2,
     lambda: plates('#c2b6a6', 744, step=7, edge=12)),
    ('salt_crust', 'Salt Crust', 'stone', 'TERRACOTTA_YELLOW', 1.0,
     lambda: crystalline('#b49a72', 745, facet=8)),
    ('deep_ice', 'Deep Ice', 'ice', 'COLOR_LIGHT_BLUE', 1.4,
     lambda: ice('#8fb0c8', 746, cracks=2, bubbles=6, step=6)),
    ('seafloor_rock', 'Seafloor Rock', 'stone', 'DEEPSLATE', 2.2,
     lambda: rock('#3e423f', 747, step=7, cracks=2)),
    ('vent_chimney', 'Vent Chimney', 'vent', 'TERRACOTTA_ORANGE', 1.8,
     lambda: glowing('#4a3a2e', '#d09050', 748)),
    ('sulfide_ore', 'Sulfide Ore', 'ore', 'DEEPSLATE', 3.0,
     lambda: ore(rock('#3e423f', 747, step=7, cracks=2), 749, '#b8963c', '#f0dc8a', count=7)),

    # Ganymede: dark ancient crust, bright grooved ice in long bands, flat palimpsests (ghosts of
    # huge craters), frosted poles, magnetite in the dark crust.
    ('dark_crust', 'Dark Ice Crust', 'stone', 'TERRACOTTA_BROWN', 1.6,
     lambda: rock('#5c544a', 751, step=7)),
    ('grooved_ice', 'Grooved Ice', 'ice', 'TERRACOTTA_WHITE', 1.2,
     lambda: layered('#bcb6ac', 752, step=6, bands=(5, -4, 3, -6))),
    ('bright_ice', 'Bright Ice', 'ice', 'SNOW', 1.0,
     lambda: ice('#dcdad4', 753, cracks=2, bubbles=5, step=5)),
    ('ganymede_regolith', 'Ganymede Regolith', 'soil', 'COLOR_GRAY', 0.5,
     lambda: dust('#76706a', 754, step=6)),
    ('palimpsest_ice', 'Palimpsest Ice', 'ice', 'TERRACOTTA_LIGHT_GRAY', 1.2,
     lambda: frost('#a49a8c', 755, sparkle=6, step=5)),
    ('polar_frost', 'Polar Frost', 'soil', 'SNOW', 0.3,
     lambda: frost('#e4e4e2', 756, sparkle=12, step=4)),
    ('magnetite_ore', 'Magnetite Ore', 'ore', 'TERRACOTTA_BROWN', 3.0,
     lambda: ore(rock('#5c544a', 751, step=7), 757, '#2a2a2e', '#7a7c86', count=8)),

    # Callisto: almost black carbon-rich dust over dirty ice, bright ice spires and crater rims,
    # breccia in the great ring basins.
    ('callistan_dust', 'Callistan Dust', 'soil', 'COLOR_BROWN', 0.5,
     lambda: dust('#4a423a', 761, step=6)),
    ('callistan_ice', 'Callistan Ice', 'ice', 'SNOW', 1.2,
     lambda: ice('#cfcbc4', 762, cracks=2, bubbles=3, step=5)),
    ('dirty_ice', 'Dirty Ice', 'ice', 'TERRACOTTA_GRAY', 1.4,
     lambda: frost('#6c645a', 763, sparkle=8, step=6)),
    ('callistan_rock', 'Callistan Rock', 'stone', 'COLOR_BROWN', 2.0,
     lambda: rock('#4f4740', 764, step=7, cracks=1)),
    ('ring_breccia', 'Ring Breccia', 'stone', 'TERRACOTTA_GRAY', 1.8,
     lambda: pebbles('#5e564e', 765, step=9, count=10)),
    ('carbon_deposit', 'Carbonaceous Deposit', 'ore', 'COLOR_BLACK', 2.0,
     lambda: ore(dust('#4a423a', 761, step=6), 766, '#1c1b1a', '#5a5856', count=9)),

    # Titan: dark brown organic dune sand, ice bedrock as hard as rock, rounded ice pebbles on the
    # old river plains, tholin mud on lake shores, bright crust on the uplands, evaporite rings
    # around dry lake beds. (The lakes themselves are liquid methane.)
    ('tholin_sand', 'Tholin Sand', 'soil', 'TERRACOTTA_BROWN', 0.5,
     lambda: dust('#5a4632', 771, step=6)),
    ('ice_bedrock', 'Ice Bedrock', 'stone', 'TERRACOTTA_LIGHT_GRAY', 2.0,
     lambda: rock('#8e8578', 772, step=7, cracks=2)),
    ('ice_pebbles', 'Ice Pebbles', 'soil', 'TERRACOTTA_LIGHT_GRAY', 0.6,
     lambda: pebbles('#7d6c58', 773, step=10, count=12)),
    ('tholin_mud', 'Tholin Mud', 'soil', 'TERRACOTTA_BLACK', 0.5,
     lambda: rock('#3e3226', 774, step=5)),
    ('organic_crust', 'Organic Crust', 'stone', 'TERRACOTTA_YELLOW', 1.2,
     lambda: rock('#9a8462', 775, step=6, cracks=2)),
    ('evaporite', 'Evaporite', 'stone', 'TERRACOTTA_WHITE', 1.0,
     lambda: crystalline('#c2b49a', 776, facet=7)),

    # Enceladus: the whitest ground in the solar system (fresh plume snow), clean ice, blue ice
    # along the tiger-stripe fissures, banded ridged ice, and silica-rich ice near the vents.
    ('plume_snow', 'Plume Snow', 'soil', 'SNOW', 0.3,
     lambda: frost('#eef0f2', 781, sparkle=8, step=3)),
    ('enceladus_ice', 'Enceladus Ice', 'ice', 'ICE', 1.2,
     lambda: ice('#d8e0e6', 782, cracks=2, bubbles=4, step=5)),
    ('stripe_ice', 'Tiger-Stripe Ice', 'ice', 'COLOR_LIGHT_BLUE', 1.2,
     lambda: ice('#9cc0d8', 783, cracks=3, bubbles=5, step=6)),
    ('ridged_ice', 'Ridged Ice', 'ice', 'ICE', 1.4,
     lambda: layered('#c8d2d8', 784, step=5, bands=(4, -3, 2, -5))),
    ('silica_ice', 'Silica Ice', 'ore', 'COLOR_LIGHT_BLUE', 1.6,
     lambda: ore(ice('#d8e0e6', 782, cracks=2, bubbles=4, step=5), 785, '#a8b8c4', '#f4f8fa', count=8)),
    ('geyser_vent', 'Geyser Vent', 'geyser', 'COLOR_LIGHT_BLUE', 2.0,
     lambda: geyser_top('#b8ccd8', '#5a7084', 786)),

    # Triton: pinkish nitrogen frost, dimpled cantaloupe crust, dark streaks of geyser fallout,
    # methane ice, nitrogen geysers.
    ('nitrogen_ice', 'Nitrogen Ice', 'ice', 'TERRACOTTA_WHITE', 1.0,
     lambda: ice('#d6c4bc', 791, cracks=2, bubbles=3, step=5)),
    ('pink_frost', 'Pink Frost', 'soil', 'TERRACOTTA_PINK', 0.3,
     lambda: frost('#d8beb4', 792, sparkle=8, step=4)),
    ('cantaloupe_crust', 'Cantaloupe Crust', 'stone', 'TERRACOTTA_LIGHT_GRAY', 1.4,
     lambda: dimpled('#a89a90', 793, step=8)),
    ('plume_dust', 'Plume Dust', 'soil', 'TERRACOTTA_GRAY', 0.4,
     lambda: dust('#5e5450', 794, step=5)),
    ('methane_ice', 'Methane Ice', 'ice', 'TERRACOTTA_WHITE', 1.0,
     lambda: ice('#cfc6b6', 795, cracks=3, bubbles=2, step=5)),
    ('nitrogen_geyser', 'Nitrogen Geyser', 'geyser', 'TERRACOTTA_GRAY', 2.0,
     lambda: geyser_top('#a89890', '#3e3634', 796)),

    # Pluto: the nitrogen-ice plain in its convection cells with darker troughs, red-brown
    # tholins, water-ice mountains with methane snowcaps, bladed methane ice, carbon monoxide ice.
    ('sputnik_ice', 'Sputnik Ice', 'ice', 'SNOW', 1.0,
     lambda: ice('#e2dcd2', 801, cracks=2, bubbles=3, step=4)),
    ('trough_ice', 'Trough Ice', 'ice', 'TERRACOTTA_LIGHT_GRAY', 1.0,
     lambda: frost('#b0a494', 802, sparkle=6, step=5)),
    ('tholins', 'Tholins', 'soil', 'TERRACOTTA_RED', 0.5,
     lambda: dust('#6a3a2a', 803, step=6)),
    ('mountain_ice', 'Mountain Ice', 'stone', 'TERRACOTTA_WHITE', 2.2,
     lambda: rock('#a49a90', 804, step=7, cracks=2)),
    ('methane_snow', 'Methane Snow', 'soil', 'SNOW', 0.3,
     lambda: frost('#ece6dc', 805, sparkle=10, step=4)),
    ('bladed_ice', 'Bladed Ice', 'ice', 'TERRACOTTA_LIGHT_GRAY', 1.2,
     lambda: blades('#b8aa9c', 806, step=9)),
    ('carbon_monoxide_ice', 'Carbon Monoxide Ice', 'ice', 'ICE', 0.8,
     lambda: ice('#d4d4cc', 807, cracks=3, bubbles=4, step=4)),
    ('tholin_frost', 'Tholin Frost', 'soil', 'TERRACOTTA_ORANGE', 0.5,
     lambda: frost('#c9a886', 808, sparkle=4, step=5)),

    # Charon: grey water ice and regolith, the dark red tholin cap of Mordor Macula, and bright
    # ammonia ice thrown out of fresh craters.
    ('charon_ice', 'Charon Ice', 'ice', 'COLOR_LIGHT_GRAY', 1.4,
     lambda: ice('#9c9894', 821, cracks=3, bubbles=2, step=5)),
    ('charon_regolith', 'Charon Regolith', 'soil', 'COLOR_GRAY', 0.5,
     lambda: dust('#7c7874', 822, step=6)),
    ('mordor_tholin', 'Mordor Tholin', 'soil', 'TERRACOTTA_RED', 0.5,
     lambda: dust('#5a3428', 823, step=6)),
    ('ammonia_ice', 'Ammonia Ice', 'ice', 'COLOR_LIGHT_BLUE', 1.0,
     lambda: frost('#c8d0d4', 824, sparkle=12, step=4)),

    # Makemake: coarse, glinting methane ice, streaked reddish-brown with tholins, ethane frost in the hollows.
    ('makemake_methane_ice', 'Coarse Methane Ice', 'ice', 'TERRACOTTA_WHITE', 1.2,
     lambda: crystalline('#e6d8cc', 841, facet=9)),
    ('makemake_tholin', 'Makemake Tholin', 'soil', 'TERRACOTTA_BROWN', 0.5,
     lambda: dust('#8a5a40', 842, step=6)),
    ('ethane_frost', 'Ethane Frost', 'soil', 'SNOW', 0.3,
     lambda: frost('#f2ece4', 843, sparkle=8, step=4)),

    # Eris: almost the brightest thing out there, a skin of fresh methane frost over everything,
    # pitted where it boils off, grey rock under.
    ('eris_frost', 'Eris Frost', 'soil', 'SNOW', 0.3,
     lambda: frost('#f6f4f0', 851, sparkle=16, step=4)),
    ('eris_rock', 'Eris Rock', 'stone', 'COLOR_GRAY', 2.4,
     lambda: rock('#77706a', 852, step=8, cracks=2)),

    # Haumea: clean crystalline water ice, blue-white, with a dark red spot of mineral-rich ice;
    # dense rock underneath (it's mostly rock).
    ('crystalline_ice', 'Crystalline Ice', 'ice', 'ICE', 1.4,
     lambda: crystalline('#dfe8f0', 861, facet=12)),
    ('red_spot_ice', 'Red Spot Ice', 'ice', 'TERRACOTTA_RED', 1.4,
     lambda: ice('#7a3a30', 862, cracks=3, bubbles=3, step=5)),
    ('haumea_rock', 'Haumea Rock', 'stone', 'DEEPSLATE', 3.0,
     lambda: rock('#4a4644', 863, step=7, cracks=3)),

    # Acheron: a crust of an odd, too-even green; bloodstone where it's red, a darker green rim
    # round it, one pale spot; and under it all a dull green-grey rock.
    ('acheron_crust', 'Acheron Crust', 'soil', 'COLOR_GREEN', 0.7,
     lambda: dust('#35874f', 871, step=11, pebble=7)),
    ('acheron_rim', 'Dark Acheron Crust', 'soil', 'COLOR_GREEN', 0.7,
     lambda: dust('#317747', 872, step=11, pebble=7)),
    ('acheron_pale', 'Pale Acheron Crust', 'soil', 'COLOR_LIGHT_GREEN', 0.7,
     lambda: dust('#4a9863', 873, step=10, pebble=6)),
    ('bloodstone', 'Bloodstone', 'stone', 'COLOR_RED', 2.0,
     lambda: rock('#873636', 874, step=12, cracks=3)),
    ('acheron_rock', 'Acheron Rock', 'stone', 'COLOR_GRAY', 2.4,
     lambda: rock('#4c5650', 875, step=11, cracks=2)),

    # Deep under Triton and Pluto: the dark rock of their cores, with uraninite in it.
    ('core_rock', 'Core Rock', 'stone', 'DEEPSLATE', 2.6,
     lambda: rock('#423c3a', 811, step=7, cracks=2)),
    ('uraninite_ore', 'Uraninite Ore', 'ore', 'DEEPSLATE', 3.5,
     lambda: ore(rock('#423c3a', 811, step=7, cracks=2), 812, '#3a3c2a', '#9cae6a', count=6)),
]

# What ores drop (raw material items); everything else drops itself.
ORE_DROPS = {
    'telluride_ore': ('tellurium', 'Tellurium'),
    'helium_regolith': ('helium_3', 'Helium-3'),
    'ilmenite_ore': ('titanium', 'Titanium'),
    'hematite_ore': ('hematite', 'Hematite'),
    'sulphur_deposit': ('sulphur_crystals', 'Sulphur Crystals'),
    'sulfide_ore': ('minecraft:raw_gold', None),
    'magnetite_ore': ('magnetite', 'Magnetite'),
    'carbon_deposit': ('carbon', 'Carbon'),
    'silica_ice': ('silica', 'Silica'),
    'uraninite_ore': ('uraninite', 'Uraninite'),
}

# Items made from the raw materials (no block of their own).
EXTRA_ITEMS = {
    'aerogel': 'Aerogel',
    'carbon_fibre': 'Carbon Fibre',
}


def shard_item(body, light, dark, seed):
    """Item icon: a small cluster of crystal shards, lit from the upper left, transparent around."""
    img = np.zeros((SIZE, SIZE, 4))
    b, l, d = hexcol(body), hexcol(light), hexcol(dark)
    r = rng(seed)
    shards = [(4, 13, 3, 9), (7, 14, 3, 12), (10, 13, 3, 8)]
    for x0, base_y, w, h in shards:
        x0 += int(r.integers(-1, 2))
        for i in range(h):
            y = base_y - i
            half = max(0, int(round((w / 2) * (1 - max(0, i - h + 3) / 3))))
            for x in range(x0 - half, x0 + half + 1):
                col = l if x < x0 else (b if x == x0 else d)
                img[y, x, :3] = col + r.uniform(-6, 6)
                img[y, x, 3] = 255
    # Dark outline like vanilla items.
    alpha = img[:, :, 3] > 0
    edge = np.zeros_like(alpha)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(alpha, dy, 0), dx, 1) & ~alpha
    img[edge, :3] = d * 0.45
    img[edge, 3] = 255
    return img


def outline(img, dark):
    alpha = img[:, :, 3] > 0
    edge = np.zeros_like(alpha)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(alpha, dy, 0), dx, 1) & ~alpha
    img[edge, :3] = hexcol(dark) * 0.45
    img[edge, 3] = 255
    return img


def blob(img, cx, cy, rad, body, light, dark, r):
    """A rounded lump lit from the upper left."""
    b, l, d = hexcol(body), hexcol(light), hexcol(dark)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x - cx, y - cy
            if (dx * dx) / (rad[0] ** 2) + (dy * dy) / (rad[1] ** 2) <= 1:
                t = (dx + dy) / (rad[0] + rad[1])
                col = l if t < -0.35 else (d if t > 0.3 else b)
                img[y, x, :3] = col + r.uniform(-5, 5)
                img[y, x, 3] = 255


def lump_item(body, light, dark, seed, lumps=((7.5, 8.5, 4.5, 3.6),)):
    """Item icon: one or more rough mineral lumps."""
    img = np.zeros((SIZE, SIZE, 4))
    r = rng(seed)
    for cx, cy, rx, ry in lumps:
        blob(img, cx, cy, (rx, ry), body, light, dark, r)
    # A few facets so it reads as rock, not a ball.
    for _ in range(5):
        x, y = r.integers(3, 13, 2)
        if img[y, x, 3] > 0:
            img[y, x, :3] += r.choice([-14, 12])
    return outline(img, dark)


def ingot_item(body, light, dark, seed):
    """Item icon: an ingot like vanilla's, seen from above at an angle."""
    img = np.zeros((SIZE, SIZE, 4))
    b, l, d = hexcol(body), hexcol(light), hexcol(dark)
    r = rng(seed)
    for y in range(5, 12):
        for x in range(2, 14):
            # A parallelogram: the top face leans right as it goes up.
            if 2 + (11 - y) // 2 <= x <= 13 - (y - 5) // 3:
                top = y < 8
                col = l if top and x < 9 else (b if top else d)
                img[y, x, :3] = col + r.uniform(-4, 4)
                img[y, x, 3] = 255
    return outline(img, dark)


def vial_item(gas, glass, cap, seed):
    """Item icon: a small sealed gas cylinder (helium-3)."""
    img = np.zeros((SIZE, SIZE, 4))
    r = rng(seed)
    g, gl, c = hexcol(gas), hexcol(glass), hexcol(cap)
    for y in range(2, 15):
        for x in range(5, 11):
            if y < 4:
                if 6 <= x <= 9:
                    img[y, x, :3] = c + (12 if x == 6 else 0)
                    img[y, x, 3] = 255
            else:
                edge = x in (5, 10) or y == 14
                img[y, x, :3] = (gl if edge else g) + (14 if x == 6 else 0) + r.uniform(-4, 4)
                img[y, x, 3] = 255
    return outline(img, cap)


def weave_item(a, b, dark, seed):
    """Item icon: a folded sheet of woven fibre."""
    img = np.zeros((SIZE, SIZE, 4))
    ca, cb = hexcol(a), hexcol(b)
    for y in range(3, 13):
        for x in range(3, 13):
            if x + (y - 3) // 3 < 14:
                img[y, x, :3] = ca if ((x // 2) + (y // 2)) % 2 else cb
                img[y, x, 3] = 255
    return outline(img, dark)


def cube_item(body, light, dark, seed):
    """Item icon: a pale translucent-looking block (aerogel)."""
    img = np.zeros((SIZE, SIZE, 4))
    b, l, d = hexcol(body), hexcol(light), hexcol(dark)
    for y in range(3, 14):
        for x in range(3, 14):
            if y < 6 and x >= 3 + (6 - y):
                img[y, x, :3] = l
            elif y >= 6 and x < 11:
                img[y, x, :3] = b
            elif y >= 6 - (x - 11) and x >= 11:
                img[y, x, :3] = d
            else:
                continue
            img[y, x, 3] = 255
    return outline(img, dark)


ITEM_TEXTURES = {
    'tellurium': lambda: shard_item('#b8b6aa', '#ecead8', '#6c6a60', 601),
    'helium_3': lambda: vial_item('#b8d8e8', '#e8f4fa', '#6a7480', 602),
    'titanium': lambda: ingot_item('#a8acb2', '#d8dce2', '#6a6e76', 603),
    'hematite': lambda: lump_item('#4c4e5a', '#9aa0b4', '#2a2a32', 604,
                                  lumps=((5, 9, 2.6, 2.6), (10, 6, 2.6, 2.6), (10.5, 11, 2.4, 2.4))),
    'sulphur_crystals': lambda: shard_item('#d8c040', '#fff2a0', '#8a7420', 605),
    'magnetite': lambda: lump_item('#34343a', '#7a7c86', '#18181c', 606),
    'carbon': lambda: lump_item('#2a2826', '#5a5856', '#121110', 607, lumps=((6, 9, 3.5, 3), (10.5, 7.5, 3, 2.6))),
    'silica': lambda: shard_item('#d0dce4', '#f8fcff', '#8098a8', 608),
    'uraninite': lambda: lump_item('#3a3c2a', '#9cae6a', '#1c1e14', 609),
    'aerogel': lambda: cube_item('#b4cce0', '#dcecf8', '#7c98b0', 610),
    'carbon_fibre': lambda: weave_item('#3a3a3c', '#26262a', '#101012', 611),
}


# ---------------------------------------------------------------------------------------------
# Writing it all out
# ---------------------------------------------------------------------------------------------

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        if isinstance(data, str):
            f.write(data)
        else:
            json.dump(data, f, indent=2)
            f.write('\n')


def textures():
    out = os.path.join(ASSETS, 'textures/block')
    os.makedirs(out, exist_ok=True)
    stats = []
    for bid, _, _, _, _, recipe in BLOCKS:
        im = to_image(recipe())
        im.save(os.path.join(out, bid + '.png'))
        stats.append((bid,) + luminance_stats(im))
    item_out = os.path.join(ASSETS, 'textures/item')
    os.makedirs(item_out, exist_ok=True)
    for item, recipe in ITEM_TEXTURES.items():
        to_image(recipe()).save(os.path.join(item_out, item + '.png'))
    for bid, mean, std in stats:
        print(f'  {bid:22s} mean {mean:6.1f}  std {std:5.1f}')


def resources():
    lang_path = os.path.join(ROOT, 'tools/lang_extra.json')
    lang = json.load(open(lang_path))
    pick, shovel, stone_tool = [], [], []
    for bid, name, kind, _, _, _ in BLOCKS:
        write(f'{ASSETS}/blockstates/{bid}.json', {'variants': {'': {'model': f'{NS}:block/{bid}'}}})
        if kind in ('geyser', 'lavaVent'):
            # The vent on top, the plain ground round it on the sides and bottom.
            side = {'geyser_vent': 'enceladus_ice', 'nitrogen_geyser': 'nitrogen_ice', 'lava_vent': 'ionian_basalt'}[bid]
            write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
                'top': f'{NS}:block/{bid}', 'side': f'{NS}:block/{side}', 'bottom': f'{NS}:block/{side}'}})
        else:
            write(f'{ASSETS}/models/block/{bid}.json', {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{bid}'}})
        write(f'{ASSETS}/models/item/{bid}.json', {'parent': f'{NS}:block/{bid}'})
        lang[f'block.{NS}.{bid}'] = name
        drop = ORE_DROPS.get(bid)
        if drop:
            entry = {'type': 'minecraft:alternatives', 'children': [
                {'type': 'minecraft:item', 'name': f'{NS}:{bid}', 'conditions': [silk_touch()]},
                {'type': 'minecraft:item', 'name': drop[0] if ':' in drop[0] else f'{NS}:{drop[0]}', 'functions': [
                    {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                    {'function': 'minecraft:explosion_decay'}]}]}
        else:
            entry = {'type': 'minecraft:item', 'name': f'{NS}:{bid}'}
        write(f'{DATA}/loot_table/blocks/{bid}.json', {'type': 'minecraft:block', 'pools': [
            {'rolls': 1, 'entries': [entry], 'conditions': [] if drop else [{'condition': 'minecraft:survives_explosion'}]}]})
        (shovel if kind == 'soil' else pick).append(f'{NS}:{bid}')
        if kind == 'ore':
            stone_tool.append(f'{NS}:{bid}')
    for item, name in list(ORE_DROPS.values()) + list(EXTRA_ITEMS.items()):
        if ':' in item:
            continue  # a vanilla item
        lang[f'item.{NS}.{item}'] = name
        write(f'{ASSETS}/models/item/{item}.json', {'parent': 'minecraft:item/generated',
                                                     'textures': {'layer0': f'{NS}:item/{item}'}})
    write(lang_path, lang)
    # Smelting: hematite gives iron, silica foams into aerogel.
    for src, out in (('hematite', 'minecraft:iron_ingot'), ('silica', f'{NS}:aerogel')):
        write(f'{DATA}/recipe/{src}_smelting.json', {'type': 'minecraft:smelting', 'category': 'misc',
              'ingredient': {'item': f'{NS}:{src}'}, 'result': {'id': out}, 'experience': 0.5, 'cookingtime': 200})
    # Off-world water ice the fuel loader turns into propellant.
    ices = ['lunar_ice', 'mercury_ice', 'martian_ice', 'europan_ice', 'deep_ice', 'bright_ice', 'callistan_ice', 'enceladus_ice',
            'stripe_ice', 'ice_bedrock']
    write(f'{DATA}/tags/item/propellant_ice.json', {'replace': False, 'values': [f'{NS}:{i}' for i in ices]})
    # Tags are merged with whatever tools/resources.py writes (it owns the other blocks).
    merge_tag('minecraft', 'block/mineable/pickaxe', pick)
    merge_tag('minecraft', 'block/mineable/shovel', shovel)
    merge_tag('minecraft', 'block/needs_stone_tool', stone_tool)
    merge_tag(NS, 'block/not_vessel', [f'{NS}:{b[0]}' for b in BLOCKS])


def silk_touch():
    return {'condition': 'minecraft:match_tool', 'predicate': {'predicates': {'minecraft:enchantments': [
        {'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}


def merge_tag(namespace, name, values):
    path = os.path.join(ROOT, f'src/main/resources/data/{namespace}/tags/{name}.json')
    tag = json.load(open(path)) if os.path.exists(path) else {'replace': False, 'values': []}
    for v in values:
        if v not in tag['values']:
            tag['values'].append(v)
    write(path, tag)


def java():
    lines = []
    for bid, _, kind, color, hardness, _ in BLOCKS:
        lines.append(f'        add("{bid}", WorldBlocks.{kind}(MapColor.{color}, {hardness}f));')
    items = [f'        item("{item}");' for item, _ in list(ORE_DROPS.values()) + list(EXTRA_ITEMS.items()) if ':' not in item]
    write(JAVA, f'''package com.thepearlking.stevespace.registry;

import net.minecraft.world.level.material.MapColor;

/**
 * Generated by tools/worlds.py from its block table: edit the table there and run it again.
 * Every natural block of the other worlds, and the raw materials their ores drop.
 */
final class WorldBlockTable {{

    private WorldBlockTable() {{
    }}

    static void registerAll() {{
{chr(10).join(lines)}
{chr(10).join(items)}
    }}

    private static void add(String id, java.util.function.Supplier<net.minecraft.world.level.block.Block> block) {{
        WorldBlocks.add(id, block);
    }}

    private static void item(String id) {{
        WorldBlocks.addItem(id);
    }}
}}
''')


def preview():
    from PIL import Image
    vanilla = os.path.expanduser('~/.cache/stevespace-ref/vanilla/assets/minecraft/textures/block')
    refs = ['stone', 'basalt_side', 'red_sand', 'packed_ice', 'obsidian', 'deepslate', 'tuff']
    tiles = [(b[0], Image.open(os.path.join(ASSETS, 'textures/block', b[0] + '.png')).convert('RGBA')) for b in BLOCKS]
    tiles += [('v:' + n, Image.open(os.path.join(vanilla, n + '.png')).convert('RGBA').crop((0, 0, 16, 16))) for n in refs]
    scale, cols = 4, 6
    cell = 48 * scale + 8
    rows = (len(tiles) + cols - 1) // cols
    sheet = Image.new('RGBA', (cols * cell, rows * cell), (40, 44, 52, 255))
    for i, (_, t) in enumerate(tiles):
        wall = Image.new('RGBA', (48, 48))
        for a in range(3):
            for b in range(3):
                wall.paste(t, (a * 16, b * 16))
        sheet.paste(wall.resize((48 * scale, 48 * scale), Image.NEAREST), ((i % cols) * cell, (i // cols) * cell))
    path = os.path.join(ROOT, 'tools/previews/worlds.png')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    sheet.save(path)
    print('preview:', path)


def build():
    """The world blocks, on top of what tools/resources.py has just written."""
    textures()
    resources()
    java()
    # The language file is assembled by resources.py; add the world names to it.
    lang_path = os.path.join(ASSETS, 'lang/en_us.json')
    lang = json.load(open(lang_path))
    lang.update(json.load(open(os.path.join(ROOT, 'tools/lang_extra.json'))))
    write(lang_path, dict(sorted(lang.items())))
    print(f'{len(BLOCKS)} world blocks written')


if __name__ == '__main__':
    import resources as base  # the mod's own blocks first (it rewrites the shared tags)
    base.main()
    build()
    if 'preview' in sys.argv:
        preview()
