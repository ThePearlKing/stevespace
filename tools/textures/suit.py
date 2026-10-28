"""Space suit textures: the armour layers worn on the player and the four item icons.

Current-day pressure suit: off-white fabric with soft seams, grey joint bands at elbows and
knees, a hard white helmet shell with a gold-tinted visor, a small control unit on the chest and
the life-support pack on the back. Run: python3 tools/textures/suit.py
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from texlib import hexcol, rng  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/assets/stevespace/textures')

FABRIC = hexcol('#e3e1db')
FABRIC_SHADE = hexcol('#c9c6bf')
SEAM = hexcol('#aeaba4')
JOINT = hexcol('#8a8d90')
JOINT_DARK = hexcol('#6c6f72')
SHELL = hexcol('#efeeea')
VISOR = hexcol('#4a3a1c')
VISOR_GLINT = hexcol('#c9a95a')
PACK = hexcol('#cfcdc6')
UNIT = hexcol('#5c6166')
LIGHT_GREEN = hexcol('#6fd18f')
LIGHT_AMBER = hexcol('#e6ad45')
BOOT = hexcol('#7d7f82')
BOOT_SOLE = hexcol('#4b4d50')
# The heat-shielded suit body: aluminised outer layer, a little warm in tone.
FOIL = hexcol('#c8c3b6')
FOIL_SHADE = hexcol('#a7a193')


def canvas(w, h):
    return np.zeros((h, w, 4))


def fill(img, x0, y0, x1, y1, color, seed=0, grain=3.0):
    """Fill [x0, x1) x [y0, y1) with a colour and a faint fabric grain."""
    r = rng(seed)
    for y in range(y0, y1):
        for x in range(x0, x1):
            img[y, x, :3] = color + r.uniform(-grain, grain)
            img[y, x, 3] = 255


def box(img, u, v, w, h, d, color, seed):
    """All six faces of a model box laid out the vanilla way (u, v = top-left of its net)."""
    fill(img, u + d, v, u + d + w, v + d, color, seed)                 # top
    fill(img, u + d + w, v, u + d + 2 * w, v + d, color * 0.92, seed + 1)  # bottom
    fill(img, u, v + d, u + 2 * d + 2 * w, v + d + h, color, seed + 2)     # sides, front, back


def row(img, x0, x1, y, color):
    for x in range(x0, x1):
        img[y, x, :3] = color
        img[y, x, 3] = 255


def layer_1():
    img = canvas(64, 32)
    # Helmet (head box 8x8x8 at 0,0): shell all round, visor on the front face.
    box(img, 0, 0, 8, 8, 8, SHELL, 11)
    for y in range(9, 15):
        for x in range(9, 15):
            img[y, x, :3] = VISOR + (6 if y < 11 else 0)
    img[10, 10, :3] = VISOR_GLINT
    img[10, 11, :3] = VISOR_GLINT * 0.85
    img[11, 10, :3] = VISOR_GLINT * 0.8
    row(img, 8, 16, 15, FABRIC_SHADE)            # neck ring, front
    row(img, 0, 32, 15, FABRIC_SHADE * 0.95)     # neck ring all round
    # Body (8x12x4 at 16,16): fabric, control unit on the chest, life-support pack on the back.
    box(img, 16, 16, 8, 12, 4, FABRIC, 21)
    for y in range(22, 26):
        for x in range(22, 26):
            img[y, x, :3] = UNIT
    img[23, 23, :3] = LIGHT_GREEN
    img[23, 24, :3] = LIGHT_AMBER
    row(img, 22, 26, 26, UNIT * 0.8)
    for y in range(21, 31):
        for x in range(33, 39):
            img[y, x, :3] = PACK - (8 if (x in (33, 38) or y in (21, 30)) else 0)
    row(img, 20, 28, 31, SEAM)                   # waist seam, front
    row(img, 16, 40, 31, SEAM)
    # Arms (4x12x4 at 40,16): fabric, grey elbow band and glove.
    box(img, 40, 16, 4, 12, 4, FABRIC, 31)
    row(img, 40, 56, 25, JOINT)
    for y in (29, 30, 31):
        row(img, 40, 56, y, JOINT_DARK if y == 31 else JOINT)
    # Legs in this layer are the boots (4x12x4 at 0,16): lower part only.
    for y in range(26, 32):
        for x in range(0, 16):
            img[y, x, :3] = BOOT if y < 31 else BOOT_SOLE
            img[y, x, 3] = 255
    fill(img, 8, 16, 12, 20, BOOT_SOLE, 41)      # sole underside
    return img


def layer_2():
    img = canvas(64, 32)
    # Trousers: legs (4x12x4 at 0,16) and the waist band (body at 16,16).
    box(img, 0, 16, 4, 12, 4, FABRIC, 51)
    row(img, 0, 16, 24, JOINT)                   # knee band
    row(img, 0, 16, 25, JOINT_DARK)
    for y in range(28, 32):                      # cuffs tucked into the boots
        row(img, 0, 16, y, FABRIC_SHADE)
    box(img, 16, 16, 8, 12, 4, FABRIC, 52)
    for y in range(20, 27):                      # only the waist shows under the suit
        for x in range(16, 40):
            img[y, x, 3] = 0
    row(img, 16, 40, 27, SEAM)
    return img


def body_layer(face, seam_col, seed, quilt=4, band=None):
    """A special suit body: the same cut as the plain suit, another outer layer. {@code quilt}
    is the spacing of the quilted seams; {@code band} an optional colour for the waist and
    shoulder bands."""
    img = layer_1()
    img[:16] = 0  # the helmet is the ordinary one
    r = rng(seed)
    face, seam_col = hexcol(face), hexcol(seam_col)
    for y in range(16, 32):
        for x in range(16, 56):
            if img[y, x, 3] == 0:
                continue
            c = img[y, x, :3]
            if np.allclose(c, UNIT, atol=12) or abs(c - LIGHT_GREEN).max() < 4 or abs(c - LIGHT_AMBER).max() < 4:
                continue
            q = (x % quilt == 0) or (y % quilt == 0)
            img[y, x, :3] = (seam_col if q else face) + r.uniform(-4, 4)
            if band is not None and (y in (29, 30) or (x >= 40 and y in (20, 21))):
                img[y, x, :3] = hexcol(band) + r.uniform(-4, 4)
    return img


def outlined(sprite):
    """Vanilla-style item icon: the sprite with a dark outline around it."""
    img = canvas(16, 16)
    for y, line in enumerate(sprite):
        for x, ch in enumerate(line):
            if ch != '.':
                img[y, x, :3] = PALETTE[ch]
                img[y, x, 3] = 255
    alpha = img[:, :, 3] > 0
    edge = np.zeros_like(alpha)
    for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        edge |= np.roll(np.roll(alpha, dy, 0), dx, 1) & ~alpha
    img[edge, :3] = hexcol('#2a2b2d')
    img[edge, 3] = 255
    return img


PALETTE = {
    'w': SHELL, 'f': FABRIC, 's': FABRIC_SHADE, 'v': VISOR, 'g': VISOR_GLINT, 'j': JOINT, 'J': JOINT_DARK,
    'u': UNIT, 'l': LIGHT_GREEN, 'a': LIGHT_AMBER, 'b': BOOT, 'B': BOOT_SOLE,
    'o': FOIL, 'O': FOIL_SHADE,
    'i': hexcol('#dde2e4'), 'I': hexcol('#b4bec4'),
    'r': hexcol('#5e605a'), 'R': hexcol('#4c4e48'), 'y': hexcol('#c4a03e'),
}

ICONS = {
    'space_suit_helmet': [
        "................",
        "................",
        "......wwww......",
        "....wwwwwwww....",
        "...wwwwwwwwww...",
        "...wwvvvvvvww...",
        "..wwvggvvvvvww..",
        "..wwvgvvvvvvww..",
        "..wwvvvvvvvvww..",
        "..wwvvvvvvvvww..",
        "...wwvvvvvvww...",
        "...wwwwwwwwww...",
        "....ssssssss....",
        "....jjjjjjjj....",
        "................",
        "................",
    ],
    'space_suit': [
        "................",
        "................",
        "...ssffjjffss...",
        "..ffffjjjjffff..",
        "..ffffffffffff..",
        "..ffffffffffff..",
        "..jjffuuuuffjj..",
        "..ffffulauffff..",
        "..ffffuuuuffff..",
        "..ffffffffffff..",
        "..jj.ffffff.jj..",
        "..jj.ffffff.jj..",
        ".....ssssss.....",
        "................",
        "................",
        "................",
    ],
    'heat_shielded_space_suit': [
        "................",
        "................",
        "...OOooOOooOO...",
        "..ooooOOOOoooo..",
        "..oOooooooooOo..",
        "..oooooooooooo..",
        "..OOoouuuuooOO..",
        "..oOooulauooOo..",
        "..oooouuuuoooo..",
        "..oOooooooooOo..",
        "..OO.oooooo.OO..",
        "..OO.oOooOo.OO..",
        ".....OOOOOO.....",
        "................",
        "................",
        "................",
    ],
    'insulated_space_suit': [
        "................",
        "................",
        "...IIiiIIiiII...",
        "..iiiiIIIIiiii..",
        "..iIiiiiiiiiIi..",
        "..iiiiiiiiiiii..",
        "..IIiiuuuuiiII..",
        "..iIiiulauiiIi..",
        "..iiiiuuuuiiii..",
        "..iIiiiiiiiiIi..",
        "..II.iiiiii.II..",
        "..II.iIiiIi.II..",
        ".....IIIIII.....",
        "................",
        "................",
        "................",
    ],
    'radiation_shielded_space_suit': [
        "................",
        "................",
        "...RRrrRRrrRR...",
        "..rrrrRRRRrrrr..",
        "..rRrrrrrrrrRr..",
        "..rrrrrrrrrrrr..",
        "..RRrruuuurrRR..",
        "..rRrrulaurrRr..",
        "..rrrruuuurrrr..",
        "..yyyyyyyyyyyy..",
        "..RR.rrrrrr.RR..",
        "..RR.rRrrRr.RR..",
        ".....RRRRRR.....",
        "................",
        "................",
        "................",
    ],
    'space_suit_trousers': [
        "................",
        "................",
        "....ssssssss....",
        "....ffffffff....",
        "....ffffffff....",
        "....fff..fff....",
        "....fff..fff....",
        "....jjj..jjj....",
        "....JJJ..JJJ....",
        "....fff..fff....",
        "....fff..fff....",
        "....sss..sss....",
        "................",
        "................",
        "................",
        "................",
    ],
    'space_suit_boots': [
        "................",
        "................",
        "................",
        "................",
        "................",
        "...sss....sss...",
        "...bbb....bbb...",
        "...bbb....bbb...",
        "...bbb....bbb...",
        "...bbbb...bbbb..",
        "..bbbbb..bbbbb..",
        "..BBBBB..BBBBB..",
        "................",
        "................",
        "................",
        "................",
    ],
}


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    Image.fromarray(np.clip(np.round(img), 0, 255).astype(np.uint8), 'RGBA').save(path)


if __name__ == '__main__':
    save(layer_1(), os.path.join(ROOT, 'models/armor/space_suit_layer_1.png'))
    save(layer_2(), os.path.join(ROOT, 'models/armor/space_suit_layer_2.png'))
    save(body_layer('#c8c3b6', '#a7a193', 71), os.path.join(ROOT, 'models/armor/heat_suit_layer_1.png'))
    save(body_layer('#dde2e4', '#b4bec4', 72, quilt=3), os.path.join(ROOT, 'models/armor/insulated_suit_layer_1.png'))
    save(body_layer('#5e605a', '#4c4e48', 73, quilt=5, band='#c4a03e'), os.path.join(ROOT, 'models/armor/radiation_suit_layer_1.png'))
    for name, sprite in ICONS.items():
        save(outlined(sprite), os.path.join(ROOT, 'item', name + '.png'))
    print('suit textures written')
