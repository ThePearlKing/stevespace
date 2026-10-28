"""The docking port collar in power-sharing mode: the dark hull plating, dyed a muted signal red
(same light and shade, so it sits with the rest of the hull).

    python3 tools/textures/docking.py
"""
import os
from PIL import Image

HERE = os.path.dirname(__file__)
BLOCKS = os.path.join(HERE, '../../src/main/resources/assets/stevespace/textures/block')

src = Image.open(os.path.join(BLOCKS, 'hull_dark.png')).convert('RGBA')
out = Image.new('RGBA', src.size)
px = src.load()
lums = [0.299 * r + 0.587 * g + 0.114 * b for r, g, b, a in (px[x, y] for x in range(src.width) for y in range(src.height))]
mean = sum(lums) / len(lums)
for y in range(src.height):
    for x in range(src.width):
        r, g, b, a = px[x, y]
        k = (0.299 * r + 0.587 * g + 0.114 * b) / mean  # keep the plating's light and shade
        out.putpixel((x, y), (min(255, int(168 * k)), min(255, int(44 * k)), min(255, int(38 * k)), a))
out.save(os.path.join(BLOCKS, 'docking_ring_red.png'))
print('docking_ring_red.png', src.size, f'mean lum {mean:.0f}')
