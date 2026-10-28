"""Real Earth's coastlines, much finer than the height map: Natural Earth's 1:10m land polygons
(public domain: https://www.naturalearthdata.com/downloads/10m-physical-vectors/10m-land/),
drawn as a land/sea mask so islands and coasts keep their real outline where the 5-arc-minute
heights smear them (Hawaii's Big Island came out half its width).

Writes src/main/resources/data/stevespace/earth/land.png: 1 bit per point, 16384 x 8192 (about
2.4 km), longitude -180..180 left to right, latitude 90..-90 top to bottom.

Run:  python3 tools/earth/make_land_mask.py path/to/ne_10m_land.shp
"""
import os
import struct
import sys

from PIL import Image, ImageChops, ImageDraw

OUT = os.path.join(os.path.dirname(__file__), '../../src/main/resources/data/stevespace/earth/land.png')
W, H = 16384, 8192


def rings(path):
    """Every ring of every polygon in a shapefile (type 5), as lists of (lon, lat)."""
    data = open(path, 'rb').read()
    pos = 100
    while pos + 8 <= len(data):
        _, words = struct.unpack('>ii', data[pos:pos + 8])
        content = data[pos + 8:pos + 8 + words * 2]
        pos += 8 + words * 2
        if struct.unpack('<i', content[:4])[0] != 5:
            continue
        parts, points = struct.unpack('<ii', content[36:44])
        starts = list(struct.unpack('<%di' % parts, content[44:44 + 4 * parts])) + [points]
        base = 44 + 4 * parts
        xy = struct.unpack('<%dd' % (2 * points), content[base:base + 16 * points])
        for a, b in zip(starts, starts[1:]):
            yield [(xy[2 * i], xy[2 * i + 1]) for i in range(a, b)]


def main():
    mask = Image.new('1', (W, H), 0)
    count = 0
    for ring in rings(sys.argv[1]):
        if len(ring) < 3:
            continue
        # Each ring flips what's inside it: outer rings make land, holes (lakes) take it away.
        # Worked on its own box only (the whole map per ring takes hours).
        pts = [((lon + 180) / 360 * W, (90 - lat) / 180 * H) for lon, lat in ring]
        x0 = max(0, int(min(p[0] for p in pts)) - 1)
        y0 = max(0, int(min(p[1] for p in pts)) - 1)
        x1 = min(W, int(max(p[0] for p in pts)) + 2)
        y1 = min(H, int(max(p[1] for p in pts)) + 2)
        layer = Image.new('1', (x1 - x0, y1 - y0), 0)
        ImageDraw.Draw(layer).polygon([(x - x0, y - y0) for x, y in pts], fill=1)
        box = (x0, y0, x1, y1)
        mask.paste(ImageChops.logical_xor(mask.crop(box), layer), box)
        count += 1
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    mask.save(OUT, optimize=True)
    print('wrote', OUT, count, 'rings', os.path.getsize(OUT), 'bytes')


if __name__ == '__main__':
    main()
