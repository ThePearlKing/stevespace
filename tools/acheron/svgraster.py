import re, sys
from PIL import Image, ImageDraw
import xml.etree.ElementTree as ET

def tokens(d):
    return re.findall(r'[MmLlHhVvCcZz]|-?\d*\.?\d+(?:e-?\d+)?', d)

def path_polys(d, tx, ty, sx=1, sy=1):
    t = tokens(d); i = 0; polys = []; cur = []; x = y = 0; cmd = None; start = (0, 0)
    def num():
        nonlocal i
        v = float(t[i]); i += 1; return v
    while i < len(t):
        if re.match(r'[A-Za-z]', t[i]):
            cmd = t[i]; i += 1
            if cmd in 'Zz':
                if cur: polys.append(cur); cur = []
                x, y = start
                continue
        if cmd == 'M':
            if cur: polys.append(cur)
            x, y = num(), num(); cur = [(x, y)]; start = (x, y); cmd = 'L'
        elif cmd == 'm':
            if cur: polys.append(cur)
            x += num(); y += num(); cur = [(x, y)]; start = (x, y); cmd = 'l'
        elif cmd == 'L': x, y = num(), num(); cur.append((x, y))
        elif cmd == 'l': x += num(); y += num(); cur.append((x, y))
        elif cmd == 'H': x = num(); cur.append((x, y))
        elif cmd == 'h': x += num(); cur.append((x, y))
        elif cmd == 'V': y = num(); cur.append((x, y))
        elif cmd == 'v': y += num(); cur.append((x, y))
        elif cmd in 'Cc':
            p = [num() for _ in range(6)]
            if cmd == 'c': p = [p[0]+x, p[1]+y, p[2]+x, p[3]+y, p[4]+x, p[5]+y]
            x0, y0 = x, y
            for k in range(1, 9):
                u = k / 8; a = (1-u)**3; b = 3*(1-u)**2*u; c = 3*(1-u)*u*u; e = u**3
                cur.append((a*x0+b*p[0]+c*p[2]+e*p[4], a*y0+b*p[1]+c*p[3]+e*p[5]))
            x, y = p[4], p[5]
        else:
            i += 1
    if cur: polys.append(cur)
    return [[((px)*sx+tx, (py)*sy+ty) for px, py in poly] for poly in polys]

def render(svg, scale=4, outlines=True):
    root = ET.parse(svg).getroot()
    ns = '{http://www.w3.org/2000/svg}'
    W = float(root.get('width')); H = float(root.get('height'))
    img = Image.new('RGBA', (int(W*scale), int(H*scale)), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    shapes = []
    def walk(el, tx, ty):
        tr = el.get('transform')
        if tr and 'translate' in tr and 'scale' not in tr:
            a, b = map(float, re.findall(r'-?\d*\.?\d+', tr)[:2]); tx += a; ty += b
        for ch in el:
            tag = ch.tag.replace(ns, '')
            if tag == 'g':
                fill = ch.get('fill')
                walk(ch, tx, ty) if fill is None else walk_fill(ch, tx, ty, fill)
            elif tag == 'path':
                add(ch, tx, ty, ch.get('fill'))
    def walk_fill(el, tx, ty, fill):
        for ch in el.iter(ns + 'path'):
            add(ch, tx, ty, ch.get('fill') or fill)
    def add(ch, tx, ty, f):
        polys = path_polys(ch.get('d'), tx, ty)
        if f and f.startswith('#'):
            shapes.append((f, polys))
        # Outlines (the drawing's crater and basin rings): drawn over the fill, at their width.
        st = ch.get('stroke')
        width = float(ch.get('stroke-width') or 0)
        if st and st.startswith('#') and width > 0:
            strokes.append((len(shapes), st, width, polys))
    strokes = []
    walk(root, 0, 0)
    pending = list(strokes)
    for n, (f, polys) in enumerate(shapes):
        for poly in polys:
            if len(poly) > 2:
                dr.polygon([(px*scale, py*scale) for px, py in poly], fill=f)
        while outlines and pending and pending[0][0] <= n + 1:
            _, st, width, spolys = pending.pop(0)
            for poly in spolys:
                dr.line([(px*scale, py*scale) for px, py in poly + poly[:1]], fill=st, width=max(1, round(width*scale)), joint='curve')
    for _, st, width, spolys in (pending if outlines else []):
        for poly in spolys:
            dr.line([(px*scale, py*scale) for px, py in poly + poly[:1]], fill=st, width=max(1, round(width*scale)), joint='curve')
    return img, shapes, strokes

if __name__ == '__main__':
    img, shapes, _ = render(sys.argv[1], int(sys.argv[3]) if len(sys.argv) > 3 else 4)
    img.save(sys.argv[2])
    print(img.size, len(shapes))
