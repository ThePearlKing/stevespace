"""Hand-drawn pixel art for instrument blocks, from palette-indexed maps.

Each map is 16 rows of 16 characters; each character is a palette key. A little noise is added
afterwards on the panel colours only, so flat areas don't look plastic.
"""
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from texlib import *  # noqa
import blocks  # noqa: registers into blocks.TEXTURES

texture = blocks.texture


def draw(rows, palette, noise_keys='', seed=0, noise=2.0):
    img = np.zeros((16, 16, 4))
    for y, row in enumerate(rows):
        assert len(row) == 16, (y, row, len(row))
        for x, ch in enumerate(row):
            if ch == ' ':
                continue
            img[y, x, :3] = hexcol(palette[ch])
            img[y, x, 3] = 255
    if noise_keys:
        mask = np.zeros((16, 16))
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in noise_keys:
                    mask[y, x] = 1
        shade(img, speckle(seed) * noise, mask=mask)
    return img


CASE = {
    'c': '#5b5f64',   # casing
    'C': '#6b7075',   # casing highlight
    'k': '#45484c',   # casing shadow
    'p': '#2e3135',   # panel face
    'P': '#383c40',   # panel face light
    'b': '#1b1d20',   # bezel / screen border
}


@texture('flight_computer_front')
def flight_computer_front():
    # Two displays (orbit plot, readouts), a row of keys and two guarded switches.
    pal = dict(CASE)
    pal.update({
        's': '#0f1a14',   # screen background (dark green-black)
        'g': '#58c47a',   # green trace
        'G': '#2f7a48',   # dim green
        'a': '#e0a43c',   # amber text
        'A': '#8a6324',   # dim amber
        'w': '#c9ccc8',   # key caps
        'W': '#9a9e9a',   # key cap shadow
        'r': '#b8443a',   # red switch guard
        'y': '#d9c25a',
    })
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpbbbbbbpbbbbbbK",
        "CpbsGGssPbsaasbK",
        "CpbGssgsPbsAAsbK",
        "CpbGsgsGPbsaaabK",
        "CpbsgGssPbsAsAbK",
        "CpbbbbbbpbbbbbbK",
        "CppppppppppppppK",
        "CpwWpwWpwWpwWppK",
        "CpWWpWWpWWpWWppK",
        "CppppppppppppppK",
        "CpprppprpppypppK",
        "CppwpppwpppPpppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    pal['K'] = '#4f5357'
    return draw(rows, pal, noise_keys='pcCkK', seed=101, noise=1.5)


@texture('flight_display_front')
def flight_display_front():
    # One large display (the block entity draws the live readout on it) and three soft keys.
    pal = dict(CASE)
    pal.update({'s': '#0d1612', 'S': '#111c17', 'w': '#c9ccc8', 'W': '#9a9e9a', 'g': '#3c8a56', 'K': '#4f5357'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CbbbbbbbbbbbbbbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbbbbbbbbbbbbbbK",
        "CppppppppppppppK",
        "CpwWppwWppwWpgpK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('oxygen_generator_front')
def oxygen_generator_front():
    # A fan grille behind a frame, a small status light and a gauge strip.
    pal = dict(CASE)
    pal.update({'g': '#2a2d31', 'G': '#565b60', 'l': '#58c47a', 'K': '#4f5357', 'm': '#7fa8c4', 'M': '#3d5566'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpbbbbbbbbbbbbpK",
        "CpbGgGgGgGgGgbpK",
        "CpbgGgGgGgGgGbpK",
        "CpbGgGgGgGgGgbpK",
        "CpbgGgGgGgGgGbpK",
        "CpbGgGgGgGgGgbpK",
        "CpbgGgGgGgGgGbpK",
        "CpbGgGgGgGgGgbpK",
        "CpbbbbbbbbbbbbpK",
        "CppppppppppppppK",
        "CpbmmmmmmMMMbplK",
        "CppppppppppppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('solar_cell')
def solar_cell():
    # Monocrystalline cells: deep blue squares with thin silver busbars, a faint sheen.
    img = fill(hexcol('#1f2c4a'))
    shade(img, value_noise(141, cell=8) * 3)
    for k in (0, 8):
        img[k, :, :3] = hexcol('#7c8794')
        img[:, k, :3] = hexcol('#7c8794')
    for k in (4, 12):
        img[k, :, :3] += 10
    y, x = np.mgrid[0:16, 0:16]
    sheen = (np.abs(x - y) < 1) & (img[:, :, 0] < 80)
    img[sheen, :3] += 14
    return img


@texture('solar_frame')
def solar_frame():
    img = fill(hexcol('#8c939a'))
    shade(img, speckle(142) * 3)
    return img


@texture('solar_controller_front')
def solar_controller_front():
    pal = dict(CASE)
    pal.update({'s': '#0f1a14', 'g': '#58c47a', 'a': '#e0a43c', 'K': '#4f5357', 'u': '#3d6aa0'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpbbbbbbbbbbbbpK",
        "CpbsssssssssbbpK",
        "CpbsggsssggsbbpK",
        "CpbsssgsgsssbbpK",
        "CpbssssgssssbbpK",
        "CpbbbbbbbbbbbbpK",
        "CppppppppppppppK",
        "CpuuupuuupuuuppK",
        "CpuuupuuupuuuppK",
        "CppppppppppppppK",
        "CppgppppappppppK",
        "CppppppppppppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('rtg_side')
def rtg_side():
    # Radioisotope generator: a grey core with deep cooling fins and a yellow warning band.
    img = fill(hexcol('#6f7377'))
    shade(img, speckle(143) * 3)
    for x in range(0, 16, 3):
        img[:, x, :3] += 16
        img[:, (x + 1) % 16, :3] -= 6
        img[:, (x + 2) % 16, :3] -= 14
    img[6:9, :, :3] = hexcol('#c9a83e')
    img[6, :, :3] += 12
    img[8, :, :3] -= 14
    return img


@texture('rtg_top')
def rtg_top():
    img = fill(hexcol('#62666a'))
    shade(img, speckle(144) * 3)
    y, x = np.mgrid[0:16, 0:16]
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    img[d < 4.5, :3] = hexcol('#4a4d50')
    img[(d >= 4.5) & (d < 5.5), :3] += 14
    return img


@texture('battery_front')
def battery_front():
    pal = dict(CASE)
    pal.update({'g': '#58c47a', 'G': '#2f7a48', 'K': '#4f5357', 'w': '#9aa0a6'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpbbbbpbbbbpbbbK",
        "CpbGGbpbGGbpbGbK",
        "CpbGGbpbGGbpbGbK",
        "CpbggbpbggbpbgbK",
        "CpbggbpbggbpbgbK",
        "CpbggbpbggbpbgbK",
        "CpbggbpbggbpbgbK",
        "CpbbbbpbbbbpbbbK",
        "CppppppppppppppK",
        "CpwwppppppppwwpK",
        "CppppppppppppppK",
        "CppppppppppppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('creative_power_front')
def creative_power_front():
    pal = dict(CASE)
    pal.update({'y': '#d6b03c', 'Y': '#8f7424', 'K': '#4f5357', 'v': '#6a4a8a'})
    rows = [
        "yyyyyyyyyyyyyyyY",
        "yppppppppppppppY",
        "ypbbbbbbbbbbbbpY",
        "ypbvvvvvvvvvvbpY",
        "ypbvyyvvvvyyvbpY",
        "ypbvvyyvvyyvvbpY",
        "ypbvvvyyyyvvvbpY",
        "ypbvvvyyyyvvvbpY",
        "ypbvvyyvvyyvvbpY",
        "ypbvyyvvvvyyvbpY",
        "ypbvvvvvvvvvvbpY",
        "ypbbbbbbbbbbbbpY",
        "yppppppppppppppY",
        "yppppppppppppppY",
        "yppppppppppppppY",
        "YYYYYYYYYYYYYYYY",
    ]
    return draw(rows, pal)


@texture('magnetometer_front')
def magnetometer_front():
    # A readout screen over a fluxgate sensor: a small copper coil behind a window.
    pal = dict(CASE)
    pal.update({'s': '#0d1612', 'S': '#111c17', 'K': '#4f5357', 'o': '#b87a4a', 'O': '#7a4e2e', 'w': '#9aa0a6'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CbbbbbbbbbbbbbbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbbbbbbbbbbbbbbK",
        "CppppppppppppppK",
        "CpppbbbbbbbbpppK",
        "CpppboOoOoOobppK",
        "CpppbOoOoOoObppK",
        "CpppbbbbbbbbpppK",
        "CppwppppppppwppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('plasma_receiver_front')
def plasma_receiver_front():
    # Receiver: a narrow readout, a tuning dial and a speaker grille.
    pal = dict(CASE)
    pal.update({'s': '#0d1612', 'S': '#111c17', 'K': '#4f5357', 'g': '#2a2d31', 'G': '#565b60', 'w': '#c9ccc8'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CbbbbbbbbbbbbbbK",
        "CbssssssssssssbK",
        "CbSSSSSSSSSSSSbK",
        "CbssssssssssssbK",
        "CbbbbbbbbbbbbbbK",
        "CppppppppppppppK",
        "CpgGgGgGgppwwppK",
        "CpGgGgGgGpwppwpK",
        "CpgGgGgGgpwppwpK",
        "CpGgGgGgGppwwppK",
        "CpgGgGgGgppppppK",
        "CpGgGgGgGppppppK",
        "CppppppppppppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal)


@texture('flag_cloth')
def flag_cloth():
    # Plain cloth in light grey (the dye colour is laid over it), with soft vertical folds.
    img = fill(hexcol('#e6e6e6'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.round(np.sin(x * 0.8 + y * 0.15) * 1.5) * 6)
    shade(img, speckle(151) * 2)
    return img


@texture('flag_pole')
def flag_pole():
    img = fill(hexcol('#b4b8bc'))
    img[:, 0:2, :3] += 14
    img[:, 4:, :3] -= 10
    shade(img, speckle(152) * 2)
    return img


@texture('mission_control_front')
def mission_control_front():
    # One wide tracking display (a map with an orbit's ground track) over status lamps and keys.
    pal = dict(CASE)
    pal.update({
        's': '#0d1714',   # screen background
        'l': '#1d3a2a',   # dim land outline
        'g': '#58c47a',   # ground track
        'a': '#e0a43c',   # vessel marker
        'w': '#c9ccc8',   # key caps
        'W': '#9a9e9a',
        'r': '#b8443a',   # red lamp
        'y': '#d9c25a',   # amber lamp
        'e': '#4fb06c',   # green lamp
        'K': '#4f5357',
    })
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpbbbbbbbbbbbbpK",
        "CpbsslllssssgsbK",
        "CpbsllllssgssgbK",
        "CpbgsllssgsllsbK",
        "CpbsgsssgsllllbK",
        "CpbsslgsasslllbK",
        "CpbsllsgssssslbK",
        "CpbbbbbbbbbbbbpK",
        "CppppppppppppppK",
        "CppepypprpppwWpK",
        "CppppppppppppWpK",
        "CpwWpwWpwWpppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal, noise_keys='pcCkK', seed=171, noise=1.5)


@texture('docking_hatch')
def docking_hatch():
    # The hatch inside a docking collar: a round grey hatch, alignment marks at the four sides.
    pal = dict(CASE)
    pal.update({'h': '#8d9296', 'H': '#a3a8ab', 'd': '#6e7377', 'y': '#c9a83c', 'x': '#2e3135'})
    rows = [
        "pppppppyyppppppp",
        "pppppddddddppppp",
        "pppddHHHHHHddppp",
        "ppdHHhhhhhhHHdpp",
        "ppdHhhhhhhhhhdpp",
        "pdHhhhhhhhhhhhdp",
        "pdHhhhhxxhhhhhdp",
        "ydHhhhxhhxhhhhdy",
        "ydHhhhxhhxhhhhdy",
        "pdHhhhhxxhhhhhdp",
        "pdHhhhhhhhhhhhdp",
        "ppdHhhhhhhhhhdpp",
        "ppddhhhhhhhhddpp",
        "pppddhhhhhhddppp",
        "pppppddddddppppp",
        "pppppppyyppppppp",
    ]
    return draw(rows, pal, noise_keys='phHd', seed=181, noise=1.5)


@texture('instrument_side')
def instrument_side():
    # Plain grey equipment casing with vent slots.
    pal = dict(CASE)
    pal['v'] = '#2a2c2f'
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CccccccccccccccK",
        "CccccccccccccccK",
        "CccvvvvvvvvvvccK",
        "CccccccccccccccK",
        "CccvvvvvvvvvvccK",
        "CccccccccccccccK",
        "CccvvvvvvvvvvccK",
        "CccccccccccccccK",
        "CccvvvvvvvvvvccK",
        "CccccccccccccccK",
        "CccccccccccccccK",
        "CccccccccccccccK",
        "CccccccccccccccK",
        "CccccccccccccccK",
        "ckkkkkkkkkkkkkkk",
    ]
    pal['K'] = '#4f5357'
    return draw(rows, pal, noise_keys='cCkK', seed=102, noise=1.8)


@texture('instrument_top')
def instrument_top():
    pal = dict(CASE)
    rows = ["C" * 15 + "c"] + ["C" + "c" * 14 + "K"] * 14 + ["c" + "k" * 15]
    pal['K'] = '#4f5357'
    return draw(rows, pal, noise_keys='cCkK', seed=103, noise=1.8)


@texture('radio_rack_front')
def radio_rack_front():
    # Three rack units: a transceiver with a frequency display and tuning knob, a unit with
    # status LEDs and switches, and a speaker grille. Rack ears with screws down both sides.
    pal = dict(CASE)
    pal.update({
        'e': '#7d8287',   # rack ear
        'o': '#a5aaae',   # screw
        'd': '#101712',   # display background
        'g': '#7fe0a0',   # display digits
        'n': '#c9ccc8',   # knob
        'N': '#8a8e8a',   # knob shadow
        'l': '#57d16c',   # green LED
        'L': '#e3a33a',   # amber LED
        'x': '#404347',   # dark separator
        'h': '#25272a',   # grille hole
    })
    rows = [
        "eepppppppppppppe",
        "eopddddddddpnNpo",
        "eepdgdggdgdpNNpe",
        "eepddddddddppppe",
        "eepppppppppppppe",
        "xxxxxxxxxxxxxxxx",
        "eepppppppppppppe",
        "eoplpLplpppnpnpo",
        "eepppppppppNpNpe",
        "eepPPPPpPPPPpppe",
        "xxxxxxxxxxxxxxxx",
        "eepphphphphphppe",
        "eophphphphphphpo",
        "eepphphphphphppe",
        "eepppppppppppppe",
        "eeeeeeeeeeeeeeee",
    ]
    return draw(rows, pal, noise_keys='pe', seed=104, noise=1.4)


@texture('fuel_loader_front')
def fuel_loader_front():
    # Ground equipment: a pressure gauge, a valve wheel and a hose coupling, yellow-striped edge.
    pal = dict(CASE)
    pal.update({
        'f': '#d9d6cc',   # gauge face
        'n': '#b8443a',   # gauge needle
        'r': '#9da2a6',   # coupling ring
        'R': '#6e7276',
        'h': '#1d1f22',   # coupling bore
        'y': '#c9a83a',   # safety yellow
        'Y': '#2b2b2b',
        'w': '#b8443a',   # valve wheel
        'W': '#7c2b25',
    })
    rows = [
        "yYyYyYyYyYyYyYyY",
        "CppppppppppppppK",
        "CppbbbbbppwwwppK",
        "CppbfffbpwWpWwpK",
        "CppbfnfbpwpWpwpK",
        "CppbffnbpwWpWwpK",
        "CppbbbbbppwwwppK",
        "CppppppppppppppK",
        "CppppprrrrrppppK",
        "CpppprRRRRRrpppK",
        "CpppprRhhhRrpppK",
        "CpppprRhhhRrpppK",
        "CpppprRRRRRrpppK",
        "CppppprrrrrppppK",
        "CppppppppppppppK",
        "yYyYyYyYyYyYyYyY",
    ]
    pal['K'] = '#4f5357'
    return draw(rows, pal, noise_keys='pcCkK', seed=105, noise=1.5)


@texture('porthole')
def porthole():
    # White hull panel with a round window. Glass is see-through (translucent render layer).
    img = blocks.painted_panel('#d8d5ce', 111, fasteners=False)
    y, x = np.mgrid[0:16, 0:16]
    d = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    rim = (d >= 5.0) & (d < 6.6)
    glass = d < 5.0
    tint_towards(img, hexcol('#7b8086'), 1.0, mask=rim)
    shade(img, 10, mask=rim & (y < 7))
    shade(img, -12, mask=rim & (y > 8))
    for a in range(6):
        ang = a * np.pi / 3 + 0.3
        img[int(round(7.5 + 5.8 * np.sin(ang))), int(round(7.5 + 5.8 * np.cos(ang))), :3] = hexcol('#b4b8bc')
    img[glass, 0] = 150
    img[glass, 1] = 175
    img[glass, 2] = 190
    img[glass, 3] = 90
    streak = glass & (np.abs((x - y) - (-2)) < 1.0) & (y < 8)
    img[streak, :3] = 235
    img[streak, 3] = 150
    return img


def window_glass(img, x0, y0, x1, y1):
    """See-through glass with a faint blue tint and one soft reflection streak."""
    y, x = np.mgrid[0:16, 0:16]
    glass = (x >= x0) & (x <= x1) & (y >= y0) & (y <= y1)
    img[glass, 0] = 150
    img[glass, 1] = 172
    img[glass, 2] = 188
    img[glass, 3] = 70
    streak = glass & (np.abs((x + y) - 13) < 1.0)
    img[streak, :3] = 200
    img[streak, 3] = 48
    return img


@texture('spacecraft_window')
def spacecraft_window():
    # A thick window: anodised frame with bolts and a sealing lip, a big pane of glass.
    img = fill(hexcol('#6a6f74'))
    shade(img, speckle(171) * 2)
    bevel(img, light=10, dark=-12)
    frame(img, 2, 2, 13, 13, hexcol('#4f5357'))          # sealing lip
    for bx, by in ((1, 1), (14, 1), (1, 14), (14, 14), (7, 1), (7, 14), (1, 7), (14, 7)):
        img[by, bx, :3] = hexcol('#9ca1a5')
    return window_glass(img, 3, 3, 12, 12)


@texture('window_sheet_glass')
def window_sheet_glass():
    # Glass only: windows side by side read as one sheet (the frame is a separate strip model).
    img = fill(hexcol('#96acbc'))
    img[:, :, 3] = 70
    y, x = np.mgrid[0:16, 0:16]
    streak = np.abs((x + y) - 15) < 1.0
    img[streak, :3] = 200
    img[streak, 3] = 46
    return img


@texture('window_frame')
def window_frame():
    # Anodised frame metal, as on the porthole and the old window.
    img = fill(hexcol('#6a6f74'))
    shade(img, speckle(173) * 2)
    shade(img, value_noise(174, cell=8) * 2)
    return img


@texture('window_pane')
def window_pane():
    # The same window as a thin panel: a slimmer frame round the glass.
    img = fill(hexcol('#6a6f74'))
    shade(img, speckle(172) * 2)
    bevel(img, light=10, dark=-12)
    frame(img, 1, 1, 14, 14, hexcol('#4f5357'))
    return window_glass(img, 2, 2, 13, 13)


@texture('window_pane_edge')
def window_pane_edge():
    img = fill(hexcol('#5e6267'))
    shade(img, speckle(173) * 2)
    return img


@texture('launch_pad')
def launch_pad():
    # The pad's deck: a steel blast grate (rows of vent slots the exhaust goes down through),
    # bolted at the corners, darkened by soot toward the middle of the slab.
    img = fill(hexcol('#7d8084'))
    shade(img, value_noise(121, cell=4, octaves=2) * 3 + speckle(122) * 3)
    for gy in (3, 7, 11):
        img[gy:gy + 2, 2:14, :3] = hexcol('#56595d') + speckle(128)[gy:gy + 2, 2:14, None] * 3   # vent slot
        img[gy - 1, 2:14, :3] += 8                                   # lit upper lip
        img[gy + 2, 2:14, :3] -= 6
    img[15, :, :3] -= 18                                             # plate seams
    img[:, 15, :3] -= 18
    img[0, :, :3] += 6
    img[:, 0, :3] += 6
    for bx, by in ((0, 0), (14, 0), (0, 14), (14, 14)):
        img[by + 1, bx + 1 if bx == 0 else bx, :3] = hexcol('#a4a8ac')
    soot = value_noise(123, cell=8) > 0.2
    shade(img, -9, mask=soot)
    return img


@texture('launch_pad_side')
def launch_pad_side():
    # Concrete under the deck, with a yellow and black hazard band along the top edge.
    img = fill(hexcol('#8e8f8b'))
    shade(img, value_noise(125, cell=4, octaves=2) * 4 + speckle(126) * 4)
    img[4, :, :3] -= 14                                              # deck plate shadow
    for y in range(0, 4):
        for x in range(16):
            stripe = ((x + y) // 3) % 2 == 0
            img[y, x, :3] = hexcol('#d6b03c') if stripe else hexcol('#2e2e2c')
    shade(img, speckle(127) * 3)
    return img


@texture('gravity_generator_front')
def gravity_generator_front():
    # A spin-gravity unit's service face: a round housing with a slowly turning rotor behind a
    # window, a pale blue status ring, and a small readout and switch below.
    pal = dict(CASE)
    pal.update({
        'r': '#1c2226',   # rotor well
        'w': '#2a3540',   # window glass
        'b': '#6fa8c8',   # status ring (lit)
        'B': '#4a7590',   # status ring (dim side)
        'm': '#8d9398',   # rotor blade
        'd': '#101712',   # readout
        'g': '#7fe0a0',   # readout digits
        's': '#c9ccc8',   # switch
    })
    rows = [
        "cCCCCCCCCCCCCCCk",
        "Cppppppppppppppk",
        "CpppppbbbbppPppk",
        "CppppbwwwwbpPppk",
        "CpppbwrmmrwbPppk",
        "CpppbwmrrmwBpppk",
        "CpppbwmrrmwBpppk",
        "CpppbwrmmrwBpppk",
        "CppppBwwwwBppppk",
        "CpppppBBBBpppppk",
        "Cppppppppppppppk",
        "CppddddddppPPppk",
        "CppdgdggdppspPpk",
        "CppddddddppPPppk",
        "Cppppppppppppppk",
        "kkkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal, noise_keys='pcCk', seed=151)


@texture('seat_fabric')
def seat_fabric():
    # Dark blue-grey seat cushion fabric with stitched panels.
    img = fill(hexcol('#3d4654'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.where((x + y) % 2 == 0, 2, -2) + speckle(131) * 2)
    for t in (5, 10):
        img[t, :, :3] += -10
        img[t + 1, :, :3] += 6
    return img


@texture('seat_fabric_grey')
def seat_fabric_grey():
    # Passenger seats: the same stitched fabric in a lighter, plainer grey.
    img = fill(hexcol('#5d636b'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.where((x + y) % 2 == 0, 2, -2) + speckle(133) * 2)
    for t in (5, 10):
        img[t, :, :3] += -10
        img[t + 1, :, :3] += 6
    return img


@texture('seat_frame')
def seat_frame():
    img = fill(hexcol('#a6aaad'))
    shade(img, speckle(132) * 3)
    bevel(img, light=8, dark=-12)
    return img



@texture('telescope_tube')
def telescope_tube():
    # Gloss white enamel tube: smooth, a soft highlight band along it, one faint joint ring.
    img = fill(hexcol('#dcd9d2'))
    y, x = np.mgrid[0:16, 0:16]
    shade(img, np.exp(-((x - 5.5) / 2.5) ** 2) * 9 - np.exp(-((x - 13) / 2.0) ** 2) * 6 + speckle(141) * 1.0)
    img[7, :, :3] += -9
    img[8, :, :3] += 4
    return img


@texture('telescope_metal')
def telescope_metal():
    # Dark anodised aluminium: fine brushed grain, edges catching a little light.
    img = fill(hexcol('#3c3f43'))
    grain = np.repeat(speckle(142)[:, :1], 16, axis=1)
    shade(img, grain * 2.5 + speckle(143) * 1.2)
    bevel(img, light=7, dark=-7)
    return img


@texture('telescope_lens')
def telescope_lens():
    # Objective glass seen from the front: deep blue-black with a faint coated sheen.
    img = fill(hexcol('#141a22'))
    y, x = np.mgrid[0:16, 0:16]
    r = np.sqrt((x - 7.5) ** 2 + (y - 7.5) ** 2)
    shade(img, np.exp(-((x - 5) ** 2 + (y - 5) ** 2) / 6.0) * 22)
    # Dark anodised retaining ring, then the white cell around it.
    ring = (r > 5.6) & (r <= 6.8)
    img[ring, :3] = hexcol('#3c3f43')
    img[r > 6.8, :3] = hexcol('#d6d3cc')
    tint_towards(img, hexcol('#3a5a6a'), 0.25 * np.exp(-((x - 5) ** 2 + (y - 5) ** 2) / 10.0))
    return img


@texture('remote_receiver_front')
def remote_receiver_front():
    # A probe's flight computer: a small dish antenna, a status panel and link lights.
    pal = dict(CASE)
    pal.update({'d': '#9aa0a6', 'D': '#6d7378', 'f': '#c9ccc8', 's': '#0f1a14', 'g': '#58c47a', 'G': '#2f7a48',
                'a': '#e0a43c', 'K': '#4f5357'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CppppDdddDpppppK",
        "CpppDdffddDppppK",
        "CppDdfDDfddDpppK",
        "CppDdfDDfddDpppK",
        "CpppDdffddDppppK",
        "CppppDdddDpppppK",
        "CpppppDDppppgppK",
        "CppppppDppppGppK",
        "CppppppppppppppK",
        "CpbbbbbbbbbbbbpK",
        "CpbsgGsaasGgsbpK",
        "CpbbbbbbbbbbbbpK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal, noise_keys='pcCkK', seed=211, noise=1.5)


@texture('remote_console_front')
def remote_console_front():
    # A screen showing the craft's view, a stick, and a row of five function keys.
    pal = dict(CASE)
    pal.update({'s': '#0d1612', 'S': '#16241d', 'h': '#3c8a56', 'w': '#c9ccc8', 'W': '#9a9e9a', 'j': '#1e2023',
                'J': '#8a8f94', 'r': '#a8382f', 'K': '#4f5357'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CbbbbbbbbbbbbbbK",
        "CbssssssssssssbK",
        "CbsSSSSSSSSSSsbK",
        "CbsSSShhSSSSSsbK",
        "CbshhhhhhhhhhsbK",
        "CbsSSSSSSSSSSsbK",
        "CbssssssssssssbK",
        "CbbbbbbbbbbbbbbK",
        "CppppppppppppppK",
        "CpppJppppppppppK",
        "CppjJjpwpwpwpwpK",
        "CpppjppWpWpWpWpK",
        "CprppppppppppppK",
        "CppppppppppppppK",
        "ckkkkkkkkkkkkkkk",
    ]
    return draw(rows, pal, noise_keys='pcCkK', seed=223, noise=1.5)


DIGITS = {
    1: [".#.", "##.", ".#.", ".#.", "###"],
    2: ["##.", "..#", ".#.", "#..", "###"],
    3: ["##.", "..#", ".#.", "..#", "##."],
    4: ["#.#", "#.#", "###", "..#", "..#"],
    5: ["###", "#..", "##.", "..#", "##."],
}


def rc_function_face(n, lit):
    # A relay: redstone-red traces from a contact to the edges, its function number on an amber
    # plate, and a lamp that glows red while it gives a pulse.
    pal = dict(CASE)
    pal.update({
        'r': '#c4362b' if lit else '#7a2a24',   # redstone trace
        'R': '#ff5a3c' if lit else '#9e3328',   # contact / lamp
        'o': '#ffd0a0' if lit else '#b0574a',   # lamp core
        'm': '#2a2014', 'a': '#e0a43c', 'K': '#4f5357',
    })
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CppppppppppppppK",
        "CpmmmmmpppppppppK"[:16],
        "CpmmmmmpppRRRppK",
        "CpmmmmmppRooRrrr",
        "CpmmmmmppRooRppK",
        "CpmmmmmpppRRRppK",
        "CpmmmmmppppprppK",
        "CppppppppppprppK",
        "CppppppppppprppK",
        "rrrrrrrrrrrrrppK",
        "CppppprppppppppK",
        "CppppprppppppppK",
        "CppppprppppppppK",
        "CppppprppppppppK",
        "ckkkkkrkkkkkkkkk",
    ]
    rows[2] = "CpmmmmmppppppppK"
    grid = [list(r) for r in rows]
    for y, line in enumerate(DIGITS[n]):
        for x, ch in enumerate(line):
            if ch == '#':
                grid[2 + y][3 + x] = 'a'
    return draw([''.join(r) for r in grid], pal, noise_keys='pcCkK', seed=230 + n, noise=1.5)


for _n in range(1, 6):
    texture(f'rc_function_{_n}')(lambda n=_n: rc_function_face(n, False))
    texture(f'rc_function_{_n}_on')(lambda n=_n: rc_function_face(n, True))



DEVICE = {
    'c': '#4d5235',   # olive drab casing
    'C': '#5d6341',   # casing highlight
    'k': '#3a3e28',   # casing shadow
    'p': '#2c2f22',   # panel
    'P': '#363a2a',   # panel light
    'b': '#18190f',   # bezel
    'w': '#c8c4a8',   # stencil
    'v': '#8f8a6e',   # rivet
    'K': '#45492f',   # casing edge
}


def nuclear_device_face(yield_index, armed):
    # The arming panel: a three-position yield dial, a key switch, the arming lamp (dark green
    # safe, glowing red armed) and a stencilled plate.
    pal = dict(DEVICE)
    pal.update({
        'd': '#a9a58e', 'D': '#6f6c58',               # dial ring
        'n': '#e8e2c8',                                # dial needle
        'm': '#d9b44a',                                # dial marks
        'l': '#e03a2e' if armed else '#2f4a2a',        # lamp
        'L': '#ff8a60' if armed else '#3c5c34',        # lamp core
        'y': '#cfa83a', 'Y': '#1b1b14',                # key switch
    })
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CvppppppppppppvK",
        "CpbbbbbbbpppppppK"[:16],
        "CpbDdddDbpplllpK",
        "CpbdpppdbpplLlpK",
        "CpbdpppdbpplllpK",
        "CpbDdddDbpppppppK"[:16],
        "CpbbbbbbbpppppppK"[:16],
        "CppppppppppYYYpK",
        "CppppppppppYyYpK",
        "CppppppppppYYYpK",
        "CppppppppppppppK",
        "CpbbbbbbbbbbbbpK",
        "CpbwwpwwpwwwbbpK",
        "CvbbbbbbbbbbbbvK",
        "ckkkkkkkkkkkkkkk",
    ]
    rows[2] = "CpbbbbbbbppppppK"
    rows[6] = "CpbDdddDbppppppK"
    rows[7] = "CpbbbbbbbppppppK"
    grid = [list(r) for r in rows]
    # Dial marks at three positions and the needle pointing at the chosen one.
    for (x, y) in ((3, 6), (5, 2), (7, 6)):
        grid[y][x] = 'm'
    needle = {0: [(4, 5), (3, 6)], 1: [(5, 4), (5, 3)], 2: [(6, 5), (7, 6)]}[yield_index]
    grid[4][5] = 'n'
    for (x, y) in needle:
        grid[y][x] = 'n'
    return draw([''.join(r) for r in grid], pal, noise_keys='cCkpP', seed=300 + yield_index, noise=1.6)


for _y in range(3):
    texture(f'nuclear_device_front_{_y}')(lambda y=_y: nuclear_device_face(y, False))
    texture(f'nuclear_device_front_{_y}_armed')(lambda y=_y: nuclear_device_face(y, True))


@texture('nuclear_device_side')
def nuclear_device_side():
    # The casing with a yellow plate bearing the radiation trefoil (a disc and three 60-degree
    # blades, two above and one below), and rivets.
    import math
    pal = dict(DEVICE)
    pal.update({'t': '#d8b13a', 'T': '#1a1a12'})
    grid = [list("C" + "c" * 14 + "K") for _ in range(16)]
    grid[0] = list("CCCCCCCCCCCCCCCc")
    grid[15] = list("ckkkkkkkkkkkkkkk")
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        grid[y][x] = 'v'
    cx, cy = 8.0, 8.0
    for y in range(2, 14):
        for x in range(2, 14):
            grid[y][x] = 't'
            dx, dy = x + 0.5 - cx, cy - (y + 0.5)
            r = math.hypot(dx, dy)
            ang = math.degrees(math.atan2(dy, dx)) % 360
            blade = any(abs((ang - c + 180) % 360 - 180) <= 30 for c in (30, 150, 270))
            if r <= 1.25 or (2.3 <= r <= 5.7 and blade):
                grid[y][x] = 'T'
    return draw([''.join(r) for r in grid], pal, noise_keys='cCk', seed=311, noise=1.8)


@texture('nuclear_device_top')
def nuclear_device_top():
    # An access hatch with two handles.
    pal = dict(DEVICE)
    pal.update({'h': '#7b7d63', 'H': '#2a2c1d'})
    rows = [
        "CCCCCCCCCCCCCCCc",
        "CvccccccccccccvK",
        "CcHHHHHHHHHHHHcK",
        "CcHcccccccccHHcK"[:16],
        "CcHchhhhhhhhcHcK",
        "CcHcccccccccHHcK"[:16],
        "CcHccccccccccHcK",
        "CcHccccccccccHcK",
        "CcHccccccccccHcK",
        "CcHccccccccccHcK",
        "CcHcccccccccHHcK"[:16],
        "CcHchhhhhhhhcHcK",
        "CcHcccccccccHHcK"[:16],
        "CcHHHHHHHHHHHHcK",
        "CvccccccccccccvK",
        "ckkkkkkkkkkkkkkk",
    ]
    rows[3] = "CcHccccccccccHcK"
    rows[5] = "CcHccccccccccHcK"
    rows[10] = "CcHccccccccccHcK"
    rows[12] = "CcHccccccccccHcK"
    return draw(rows, pal, noise_keys='cCk', seed=313, noise=1.8)


if __name__ == '__main__':
    out = blocks.OUT
    os.makedirs(out, exist_ok=True)
    for name, fn in blocks.TEXTURES.items():
        im = to_image(fn())
        im.save(os.path.join(out, name + '.png'))
        mean, std = luminance_stats(im)
        print(f'{name:24s} mean {mean:6.1f}  std {std:5.1f}')
    print(blocks.preview(list(blocks.TEXTURES)))
