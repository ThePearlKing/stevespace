"""Pixel art for the basic power machines (coal generator, electric furnace, crusher, batteries,
power pipe) and the crushed-ore dusts. Same casing palette as the instrument blocks."""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from texlib import *  # noqa
import blocks  # noqa
from instruments import CASE, draw

texture = blocks.texture

# Item icons, written to textures/item by blocks.py.
ITEMS = {}


def item(name):
    def wrap(fn):
        ITEMS[name] = fn
        return fn
    return wrap


GRATE = [
    "CCCCCCCCCCCCCCCc",
    "CccccccccccccccK",
    "CcbbbbbbbbbbbbcK",
    "CcbgfgfgfgfgfbcK",
    "CcbgfgfgfgfgfbcK",
    "CcbgfgfgfgfgfbcK",
    "CcbgfgfgfgfgfbcK",
    "CcbgfgfgfgfgfbcK",
    "CcbeeeeeeeeeebcK",
    "CcbbbbbbbbbbbbcK",
    "CccccccccccccccK",
    "CccvvvvvvvvvvccK",
    "CccccccccccccccK",
    "CccvvvvvvvvvvccK",
    "CccccccccccccccK",
    "ckkkkkkkkkkkkkkk",
]


def generator(lit):
    pal = dict(CASE)
    pal.update({'K': '#4f5357', 'v': '#2a2c2f', 'g': '#3a3d40'})
    if lit:
        pal.update({'f': '#e0782a', 'e': '#f0a040'})
    else:
        pal.update({'f': '#141518', 'e': '#26282b'})
    img = draw(GRATE, pal, noise_keys='cCkK', seed=901, noise=1.8)
    if lit:
        # Brighter at the bottom of the fire box, flickering patches.
        r = np.random.default_rng(902)
        for y in range(3, 9):
            for x in range(3, 13):
                if GRATE[y][x] == 'f':
                    img[y, x, :3] += (y - 3) * 6 + r.integers(-8, 9)
    return img


@texture('coal_generator_front')
def coal_generator_front():
    return generator(False)


@texture('coal_generator_front_on')
def coal_generator_front_on():
    return generator(True)


FURNACE = [
    "CCCCCCCCCCCCCCCc",
    "CppppppppppppppK",
    "CpPPPPPPPPPPPPpK",
    "CpPbbbbbbbbbbPpK",
    "CpPbwwwwwwwwbPpK",
    "CpPbwhhhhhhwbPpK",
    "CpPbwwwwwwwwbPpK",
    "CpPbwhhhhhhwbPpK",
    "CpPbwwwwwwwwbPpK",
    "CpPbbbbbbbbbbPpK",
    "CpPPPPPPPPPPPPpK",
    "CppppppppppppppK",
    "CppddppppppllppK",
    "CppddppppppllppK",
    "CppppppppppppppK",
    "ckkkkkkkkkkkkkkk",
]


def furnace(lit):
    pal = dict(CASE)
    pal.update({'K': '#4f5357', 'd': '#6b7075'})
    if lit:
        pal.update({'w': '#5a2a14', 'h': '#f08a30', 'l': '#e05030'})
    else:
        pal.update({'w': '#121316', 'h': '#3a2a24', 'l': '#4a2420'})
    return draw(FURNACE, pal, noise_keys='cCkK', seed=911, noise=1.8)


@texture('electric_furnace_front')
def electric_furnace_front():
    return furnace(False)


@texture('electric_furnace_front_on')
def electric_furnace_front_on():
    return furnace(True)


CRUSHER = [
    "CCCCCCCCCCCCCCCc",
    "CppppppppppppppK",
    "CpbbbbbbbbbbbbpK",
    "CpbsssssssssbbpK",
    "CpbtStStStStSbpK",
    "CpbtttttttttbbpK",
    "CpbqqqqqqqqqqbpK",
    "CpbTTTTTTTTTbbpK",
    "CpbtTtTtTtTtTbpK",
    "CpbsssssssssbbpK",
    "CpbbbbbbbbbbbbpK",
    "CppppppppppppppK",
    "CppppppppppplppK",
    "CppvvvvvvppppppK",
    "CppppppppppppppK",
    "ckkkkkkkkkkkkkkk",
]


def crusher(lit):
    pal = dict(CASE)
    pal.update({'K': '#4f5357', 's': '#3e4246', 't': '#8a9096', 'T': '#6c7278', 'S': '#a8aeb4',
                'q': '#141518', 'v': '#2a2c2f', 'l': '#58c47a' if lit else '#2f4a38'})
    return draw(CRUSHER, pal, noise_keys='cCkKst', seed=921, noise=1.8)


@texture('crusher_front')
def crusher_front():
    return crusher(False)


@texture('crusher_front_on')
def crusher_front_on():
    return crusher(True)


def battery(cells, case, case_hi, case_lo):
    pal = dict(CASE)
    pal.update({'C': case_hi, 'c': case, 'k': case_lo, 'K': case_lo, 'g': '#58c47a', 'G': '#2f7a48', 'w': '#9aa0a6'})
    if cells == 2:
        rows = [
            "CCCCCCCCCCCCCCCc",
            "CccccccccccccccK",
            "CcppppppppppppcK",
            "CcpbbbbppbbbbpcK",
            "CcpbGGbppbGGbpcK",
            "CcpbggbppbggbpcK",
            "CcpbggbppbggbpcK",
            "CcpbggbppbggbpcK",
            "CcpbggbppbggbpcK",
            "CcpbbbbppbbbbpcK",
            "CcppppppppppppcK",
            "CcpwwppppppwwpcK",
            "CcppppppppppppcK",
            "CccccccccccccccK",
            "CccccccccccccccK",
            "ckkkkkkkkkkkkkkk",
        ]
    else:
        rows = [
            "CCCCCCCCCCCCCCCc",
            "CppppppppppppppK",
            "CpbbbpbbbpbbbppK",
            "CpbGbpbGbpbGbppK",
            "CpbgbpbgbpbgbppK",
            "CpbgbpbgbpbgbppK",
            "CpbbbpbbbpbbbppK",
            "CppppppppppppppK",
            "CpbbbpbbbpbbbppK",
            "CpbGbpbGbpbGbppK",
            "CpbgbpbgbpbgbppK",
            "CpbgbpbgbpbgbppK",
            "CpbbbpbbbpbbbppK",
            "CpwwppppppppwwpK",
            "CppppppppppppppK",
            "ckkkkkkkkkkkkkkk",
        ]
    return draw(rows, pal, noise_keys='cCkK', seed=930 + cells, noise=1.8)


@texture('small_battery_front')
def small_battery_front():
    return battery(2, '#a8643c', '#c07a4c', '#7a4428')


@texture('large_battery_front')
def large_battery_front():
    return battery(6, '#9ea4aa', '#b8bec4', '#70767c')


@texture('power_pipe')
def power_pipe():
    # Copper tube with a dark insulating band every half block, a soft highlight along it.
    img = fill(hexcol('#b4683e'))
    shade(img, speckle(941) * 3)
    y, x = np.mgrid[0:16, 0:16]
    img[(x % 8) == 0, :3] = hexcol('#3a2c26')
    img[(y >= 6) & (y <= 7) & ((x % 8) != 0), :3] += 22
    img[(y >= 10) & ((x % 8) != 0), :3] -= 16
    return img


def dust(base, light, dark, seed):
    """A small heap of powder, lighter on top, with a few sparkling grains."""
    img = np.zeros((16, 16, 4))
    b, l, d = hexcol(base), hexcol(light), hexcol(dark)
    r = np.random.default_rng(seed)
    for y in range(5, 15):
        half = (y - 4) * 0.75 + 0.5
        for x in range(16):
            if abs(x - 7.5) <= half:
                t = (y - 5) / 9
                col = l * (1 - t) + b * t if x < 8 else b * (1 - t) + d * t
                img[y, x, :3] = col + r.integers(-10, 11)
                img[y, x, 3] = 255
    for _ in range(6):
        x, y = r.integers(3, 13), r.integers(8, 14)
        if img[y, x, 3] > 0:
            img[y, x, :3] = l + 20
    # Dark outline like vanilla items.
    a = img[:, :, 3] > 0
    edge = np.zeros_like(a)
    edge[1:, :] |= a[:-1, :] & ~a[1:, :]
    edge[:-1, :] |= a[1:, :] & ~a[:-1, :]
    edge[:, 1:] |= a[:, :-1] & ~a[:, 1:]
    edge[:, :-1] |= a[:, 1:] & ~a[:, :-1]
    img[edge, :3] = d * 0.6
    img[edge, 3] = 255
    return img


@item('iron_dust')
def iron_dust():
    return dust('#b8b4ae', '#e0dcd6', '#7a7670', 951)


@item('gold_dust')
def gold_dust():
    return dust('#e0b83a', '#fce67a', '#9a7414', 952)


@item('copper_dust')
def copper_dust():
    return dust('#c47a4c', '#e8a27a', '#7e4426', 953)


MINE = ['coal_generator_front', 'coal_generator_front_on', 'electric_furnace_front', 'electric_furnace_front_on',
        'crusher_front', 'crusher_front_on', 'small_battery_front', 'large_battery_front', 'power_pipe']

if __name__ == '__main__':
    for name in MINE:
        im = to_image(blocks.TEXTURES[name]())
        im.save(os.path.join(blocks.OUT, name + '.png'))
        mean, std = luminance_stats(im)
        print(f'{name:24s} mean {mean:6.1f}  std {std:5.1f}')
    item_out = os.path.join(blocks.OUT, '../item')
    for name, fn in ITEMS.items():
        to_image(fn()).save(os.path.join(item_out, name + '.png'))
    print(blocks.preview(MINE))
