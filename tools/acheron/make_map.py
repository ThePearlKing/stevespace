"""Acheron's surface: colour, height and kind of ground for the whole globe, made to look like a
real world while keeping the shapes and colours of the drawing (~/Pictures/acheron2.svg; the face
toward Earth is acheron1.svg).

The drawing is an old sketch of part of the globe, not a texture: one of its units is one degree
both ways (the two drawings agree on that), so it covers about 283 x 138 degrees and the rest is
made up. From it come only the shapes: where the bloodstone is, the darker green round it, the
pale spot, the craters and the basin's rings. Everything else is grown here: edges that wander
like real coastlines, mottled ground, cracked bloodstone, craters of every size with their ejecta,
and the basin as what it is, a great impact: a sunken floor, rings of cliffs facing in, a raised
middle with the pale spot on it, and a blanket of streaky ejecta round the outside.

Writes, under src/main/resources/data/stevespace/acheron/ (equirectangular, 2048 x 1024,
longitude -180..180, the face toward Earth at longitude 0):
  surface.png  kind of ground: 0 crust, 1 darker green (stained ground round the bloodstone, the
               basin's cliffs), 2 bloodstone, 3 pale (the basin's middle), 4 bare rock (crater walls)
  color.png    colour seen from above (no shading: the game shades it from the heights)
  height.png   16-bit: (value - 32768) / 100 = blocks above the mean ground

Run:  python3 tools/acheron/make_map.py [path/to/acheron2.svg]
"""
import math
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.dirname(__file__))
import svgraster  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '../..')
OUT = os.path.join(ROOT, 'src/main/resources/data/stevespace/acheron')
W, H = 2048, 1024
RADIUS = 1737.0  # blocks
BLOCKS_PER_DEG = RADIUS * math.pi / 180
# The drawing's map rectangle (SVG units), green background.
RECT = (0.0, 102.36, 282.68, 240.78)
# Where on the drawing (units from its top left) the middle of acheron1's face is.
FACE_X, FACE_Y = 72.0, 70.7
RIM = '#307c48'
SHAPES = {'#35874f': 0, '#337f4b': 0, '#317747': 1, '#317646': 1, '#307746': 1,
          '#873636': 2, '#863637': 2, '#4a9863': 3}

rng = np.random.default_rng(0x41636872)

# ---------------------------------------------------------------- the globe's pixels
LAT = np.radians(90 - (np.arange(H) + 0.5) / H * 180)[:, None] * np.ones((1, W))
LON = np.radians((np.arange(W) + 0.5) / W * 360 - 180)[None, :] * np.ones((H, 1))
PX = np.cos(LAT) * np.cos(LON)
PY = np.sin(LAT)
PZ = np.cos(LAT) * np.sin(LON)


def _hash(ix, iy, iz, seed):
    h = (ix * 73856093) ^ (iy * 19349663) ^ (iz * 83492791) ^ seed
    h = (h ^ (h >> 13)) * 1274126177
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float64) / 0x7FFFFF - 1.0


def noise(x, y, z, seed):
    """Smooth value noise in 3D, about -1..1."""
    ix, iy, iz = np.floor(x).astype(np.int64), np.floor(y).astype(np.int64), np.floor(z).astype(np.int64)
    fx, fy, fz = x - ix, y - iy, z - iz
    ux, uy, uz = (f * f * f * (f * (f * 6 - 15) + 10) for f in (fx, fy, fz))
    out = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (ux if dx else 1 - ux) * (uy if dy else 1 - uy) * (uz if dz else 1 - uz)
                out = out + w * _hash(ix + dx, iy + dy, iz + dz, seed)
    return out


def fbm(freq, octaves, seed, gain=0.5, ridged=False):
    """Fractal noise on the sphere; freq = cells per planet radius of the first octave."""
    total, amp, norm = 0, 1.0, 0
    for o in range(octaves):
        n = noise(PX * freq + 17.3 * o, PY * freq - 9.1 * o, PZ * freq + 3.7 * o, seed + 1013 * o)
        if ridged:
            n = 1 - 2 * np.abs(n)
        total = total + n * amp
        norm += amp
        amp *= gain
        freq *= 2
    return total / norm


def hexrgb(c):
    return np.array([int(c[i:i + 2], 16) for i in (1, 3, 5)], np.float64)


def paint(img, colour, amount):
    a = np.clip(amount, 0, 1)[..., None]
    img[:] = img * (1 - a) + hexrgb(colour) * a


def angle_to(lat, lon):
    """Angle (radians) from every pixel to a point."""
    a, b = math.radians(lat), math.radians(lon)
    dot = PX * math.cos(a) * math.cos(b) + PY * math.sin(a) + PZ * math.cos(a) * math.sin(b)
    return np.arccos(np.clip(dot, -1, 1))


def azimuth_round(lat, lon):
    """Direction (radians) round a point of every pixel, for things laid out radially."""
    a, b = math.radians(lat), math.radians(lon)
    c = np.array([math.cos(a) * math.cos(b), math.sin(a), math.cos(a) * math.sin(b)])
    north = np.array([0.0, 1.0, 0.0]) - c * c[1]
    north /= np.linalg.norm(north)
    east = np.cross(north, c)
    return np.arctan2(PX * east[0] + PY * east[1] + PZ * east[2], PX * north[0] + PY * north[1] + PZ * north[2])


def main():
    svg = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser('~/Pictures/acheron2.svg')
    scale = 8
    img, shapes, strokes = svgraster.render(svg, scale, outlines=False)
    src = np.asarray(img.convert('RGB')).astype(np.int32)

    # ------------------------------------------------------------ the shapes, with wandering edges
    # Look the drawing up a little off each point, by fractal amounts: the outlines become ragged
    # like real coastlines at every scale, but the shapes stay where and how big they are.
    wlat = fbm(5, 7, 11, 0.55) * 1.9 + fbm(40, 4, 12, 0.5) * 0.35
    wlon = fbm(5, 7, 13, 0.55) * 1.9 + fbm(40, 4, 14, 0.5) * 0.35
    lat_d = np.degrees(LAT) + wlat
    lon_d = np.degrees(LON) + wlon / np.maximum(0.2, np.cos(LAT))
    dx = np.mod(lon_d + FACE_X, 360)
    dy = FACE_Y - lat_d
    inside = (dx < RECT[2] - RECT[0]) & (dy >= 0) & (dy < RECT[3] - RECT[1])
    sx = np.clip(((RECT[0] + dx) * scale).astype(int), 0, src.shape[1] - 1)
    sy = np.clip(((RECT[1] + dy) * scale).astype(int), 0, src.shape[0] - 1)
    rgb = src[sy, sx]
    best = np.full((H, W), 1e18)
    drawn = np.zeros((H, W), np.uint8)
    for hexc, k in SHAPES.items():
        d = ((rgb - hexrgb(hexc)) ** 2).sum(axis=2)
        drawn = np.where(d < best, k, drawn)
        best = np.minimum(d, best)
    drawn = np.where(inside, drawn, 0)
    blood = (drawn == 2).astype(np.float64)
    stain = (drawn >= 1) & (drawn <= 2)
    # Soft versions for colour: the stain fades out from the bloodstone rather than ending in a line.
    soft_blood = ndimage.gaussian_filter(blood, 1.2, mode=('nearest', 'wrap'))
    near_blood = ndimage.gaussian_filter(stain.astype(np.float64), 4.0, mode=('nearest', 'wrap'))

    # ------------------------------------------------------------ height: the plain ground
    height = fbm(2.5, 6, 21, 0.5) * 28 + fbm(12, 5, 22, 0.5) * 7
    # The bloodstone stands a little proud of the crust, in knobbly slabs.
    knob = fbm(60, 3, 23, 0.6, ridged=True)
    height += soft_blood * (4 + knob * 2.5)

    # ------------------------------------------------------------ colour: the plain ground
    colour = np.zeros((H, W, 3))
    colour[:] = hexrgb('#35874f')
    large = fbm(3, 5, 31, 0.55)
    medium = fbm(18, 4, 32, 0.5)
    fine = fbm(90, 3, 33, 0.5)
    paint(colour, '#409a5c', np.clip(large * 1.4, 0, 1) * 0.9 + np.clip(medium, 0, 1) * 0.5)
    paint(colour, '#2b7342', np.clip(-large * 1.4, 0, 1) * 0.9 + np.clip(-medium, 0, 1) * 0.5)
    paint(colour, '#4a8a52', np.clip(fbm(6, 4, 34, 0.5) - 0.15, 0, 1) * 1.2)  # olive patches
    paint(colour, '#2f7a46', np.clip(-height / 40, 0, 1) * 0.5)  # lowlands a little darker
    colour *= (1 + fine * 0.07)[..., None]
    # The darker green round the bloodstone: stained ground, darkest at the bloodstone's edge.
    patchy = np.clip(0.75 + fbm(9, 5, 35, 0.6) * 0.9, 0.2, 1.1)
    paint(colour, '#2e7244', np.clip(near_blood * 2.0 * patchy, 0, 1) * (0.85 + medium * 0.15))
    paint(colour, '#28633b', np.clip((near_blood - 0.4) * 2.2 * patchy, 0, 1) * 0.7)
    # Bloodstone: dark red, mottled, with darker cracks between its slabs.
    red = np.zeros((H, W, 3))
    red[:] = hexrgb('#873636')
    tone = fbm(10, 5, 41, 0.55)
    paint(red, '#994541', np.clip(tone, 0, 1) * 1.3)
    paint(red, '#6c2a2d', np.clip(-tone, 0, 1) * 1.3)
    cracks = fbm(35, 3, 42, 0.5, ridged=True)
    paint(red, '#4f1d21', np.clip((cracks - 0.72) * 4, 0, 1) * 0.8)
    red *= (1 + fine * 0.08)[..., None]
    a = soft_blood[..., None]
    colour = colour * (1 - a) + red * a

    # ------------------------------------------------------------ the great impact basin
    rings = []
    craters = []
    for n, col, width, polys in strokes:
        if col != RIM:
            continue
        pts = [p for poly in polys for p in poly]
        cx = sum(p[0] for p in pts) / len(pts)
        cy = sum(p[1] for p in pts) / len(pts)
        r = sum(math.hypot(p[0] - cx, p[1] - cy) for p in pts) / len(pts)
        la = FACE_Y - (cy - RECT[1])
        lo = ((cx - RECT[0]) - FACE_X + 180) % 360 - 180
        filled = n > 0 and shapes[n - 1][1] is polys
        (craters if filled else rings).append((la, lo, r))
    rings.sort(key=lambda t: t[2])
    blat = sum(t[0] for t in rings) / len(rings)
    blon = sum(t[1] for t in rings) / len(rings)
    radii = [t[2] for t in rings]  # degrees: the small one round the middle, then three rings of cliffs
    rho = np.degrees(angle_to(blat, blon))
    az = azimuth_round(blat, blon)
    wob = fbm(8, 5, 51, 0.55) * 0.9  # the rings aren't perfect circles
    rr = rho + wob
    outer = radii[-1]
    # The impact itself is the crater in the middle, round the pale spot. The rings are the
    # ground's ripples spreading out from it, frozen: each a low ridge with a shallow trough inside.
    crater = radii[0]
    d = rr / crater
    depth, lip = 24, 8
    height += np.where(d < 1, depth * (d * d - 1) + lip, lip * np.exp(-((d - 1) / 0.6) ** 2 * 2.5))
    height += np.where(d < 0.3, depth * 0.35 * (1 - d / 0.3) ** 2, 0)  # a little central peak
    ridge = np.zeros((H, W))
    for r in radii[1:]:
        ridge = np.maximum(ridge, np.exp(-((rr - r) / 1.2) ** 2))
        height += 5 * np.exp(-((rr - r) / 1.2) ** 2) - 3 * np.exp(-((rr - r + 2.2) / 1.5) ** 2)
    # The pale spot: what the impact melted, pooled in the crater.
    pale = np.clip((crater * 0.85 - rr) / 0.5, 0, 1)
    paint(colour, '#4a9863', pale)
    paint(colour, '#55a46e', pale * np.clip(fbm(40, 3, 52, 0.5) + 0.2, 0, 1) * 0.5)
    # The ripples and the crater's lip: darker green, as drawn.
    cliff = np.maximum(ridge, np.exp(-((rr - crater) / 0.5) ** 2))
    paint(colour, '#2b6e40', cliff * 0.85)
    # Rays: bright ejecta thrown out from the crater in the middle, across the rings, fading out.
    # Each its own direction, width and length, a little wavering, and broken up along its way.
    rays = np.zeros((H, W))
    near = rho < outer * 1.5
    ray_rng = np.random.default_rng(55)
    bend = fbm(14, 3, 56, 0.5) * 0.04
    for _ in range(52):
        direction = ray_rng.uniform(-math.pi, math.pi)
        width = ray_rng.uniform(0.006, 0.03)  # radians of direction
        length = crater + ray_rng.uniform(0.25, 1.2) * outer
        off = np.angle(np.exp(1j * (az[near] + bend[near] - direction)))
        fade = np.clip(1 - (rho[near] - crater) / (length - crater), 0, 1) ** 1.5
        rays[near] = np.maximum(rays[near], np.exp(-(off / width) ** 2) * fade * ray_rng.uniform(0.5, 1))
    rays *= np.clip(0.55 + fbm(30, 4, 57, 0.5) * 1.2, 0, 1)
    reach = np.clip((rr - crater * 0.9) / 1.0, 0, 1)
    paint(colour, '#4aa366', reach * rays * 0.85 * (1 - 0.85 * soft_blood))

    kind = np.where(drawn == 2, 2, np.where(drawn == 1, 1, 0)).astype(np.uint8)
    kind = np.where(cliff > 0.45, 1, kind)
    kind = np.where(pale > 0.5, 3, kind).astype(np.uint8)

    # ------------------------------------------------------------ craters
    # The drawing's own, then a natural spread of more (many small, few big), kept off the
    # drawn shapes where a big one would spoil them.
    pop = [(la, lo, r, 0.6) for la, lo, r in craters]
    count = 0
    while count < 420:
        u = rng.random()
        r = min(7.0, 0.35 * u ** -0.55)
        la = math.degrees(math.asin(rng.uniform(-1, 1)))
        lo = rng.uniform(-180, 180)
        if r > 0.9:
            i = int((90 - la) / 180 * H) % H
            j = int((lo + 180) / 360 * W) % W
            if near_blood[i, j] > 0.05 or rho[i, j] < outer + r * 2:
                continue
        pop.append((la, lo, r, rng.random()))
        count += 1
    pop.sort(key=lambda t: -t[2])  # big first, small ones on top of them
    for la, lo, r, age in pop:
        add_crater(height, colour, kind, la, lo, r, age)

    # ------------------------------------------------------------ write
    os.makedirs(OUT, exist_ok=True)
    Image.fromarray(kind, 'L').save(os.path.join(OUT, 'surface.png'))
    Image.fromarray(np.clip(colour, 0, 255).astype(np.uint8), 'RGB').save(os.path.join(OUT, 'color.png'), optimize=True)
    h16 = np.clip(np.round(height * 100) + 32768, 0, 65535).astype(np.uint16)
    Image.fromarray(h16).save(os.path.join(OUT, 'height.png'))
    old = os.path.join(OUT, 'features.txt')
    if os.path.exists(old):
        os.remove(old)
    print('wrote', OUT, 'kinds:', np.bincount(kind.ravel(), minlength=5).tolist(),
          'height %.0f..%.0f' % (height.min(), height.max()), 'craters', len(pop))


def add_crater(height, colour, kind, la, lo, r, age):
    """A crater of radius r degrees: bowl, raised rim, ejecta; fresh ones bright with rays."""
    reach = r * (4 if age < 0.15 else 2.4)
    i0 = max(0, int((90 - la - reach) / 180 * H) - 1)
    i1 = min(H, int((90 - la + reach) / 180 * H) + 2)
    cosl = max(0.05, math.cos(math.radians(la)))
    span = reach / cosl
    if span > 170 or i0 == 0 or i1 == H:
        cols = np.arange(W)
    else:
        j0 = int((lo - span + 180) / 360 * W) - 1
        j1 = int((lo + span + 180) / 360 * W) + 2
        cols = np.arange(j0, j1) % W
    sel = np.ix_(np.arange(i0, i1), cols)
    a, b = math.radians(la), math.radians(lo)
    px, py, pz = PX[sel], PY[sel], PZ[sel]
    dot = px * math.cos(a) * math.cos(b) + py * math.sin(a) + pz * math.cos(a) * math.sin(b)
    d = np.degrees(np.arccos(np.clip(dot, -1, 1))) / r
    rb = r * BLOCKS_PER_DEG
    depth = min(70, rb * (0.2 - 0.08 * age))
    rim = min(22, rb * (0.055 - 0.025 * age))
    h = np.where(d < 1, depth * (d * d - 1) + rim, rim * np.exp(-((d - 1) / 0.7) ** 2 * 2.5))
    if rb > 120:
        h = np.where(d < 0.55, -depth * 0.82 + rim + depth * 0.4 * np.exp(-(d / 0.15) ** 2), h)  # flat floor, central peak
    patch = height[sel]
    height[sel] = np.where(d < 1, patch * (0.3 + 0.7 * d * d), patch) + h  # the old ground half buried under the bowl
    c = colour[sel]
    fresh = max(0.0, 1 - age / 0.3)
    wall = np.clip(1 - np.abs(d - 0.85) / 0.25, 0, 1)

    def mix(c, target, amount):
        m = np.clip(amount, 0, 1)[..., None]
        return c * (1 - m) + hexrgb(target) * m
    c = mix(c, '#2f7745', (d < 0.9) * 0.35)  # floors: a bit darker
    c = mix(c, '#4e6c57', wall * (0.35 + 0.5 * fresh))  # walls: bare grey-green rock
    c = mix(c, '#4fa268', np.clip(1.6 - d, 0, 1) * (d > 0.9) * (0.25 + 0.45 * fresh))  # bright ejecta
    if fresh > 0:
        # Rays: streaks of bright ejecta thrown far out, in some directions more than others.
        e = np.array([-math.sin(b), 0, math.cos(b)])
        n = np.array([-math.sin(a) * math.cos(b), math.cos(a), -math.sin(a) * math.sin(b)])
        ray_az = np.arctan2(px * e[0] + pz * e[2], px * n[0] + py * n[1] + pz * n[2])
        rays = np.clip(noise(np.cos(ray_az) * 9 + la, np.sin(ray_az) * 9 + lo, d * 0.15, int(abs(la) * 100) & 0xFFFF) * 2.2 - 0.6, 0, 1)
        c = mix(c, '#58a872', rays * np.clip((4 - d) / 3, 0, 1) * (d > 1) * 0.55 * fresh)
    colour[sel] = c
    k = kind[sel]
    kind[sel] = np.where((wall > 0.5) & (rb > 25) & (k != 2), 4, k)


if __name__ == '__main__':
    main()
