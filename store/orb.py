"""The full Photon artwork: a geodesic wireframe orb over rippled water drawn as dots.

Usage: python3 store/orb.py [subdivisions] [output.svg]. Used for the feature graphic.
"""
import math, random, sys

W = H = 1024
CX, CY, R = 512, 410, 165          # orb
WY = 545                            # water centre (ellipse centre y)
TILT = 0.40                         # ellipse ry / rx (camera angle)
random.seed(7)

# --- geodesic sphere (icosahedron, subdivided) ---------------------------------
t = (1 + 5 ** 0.5) / 2
verts = [(-1, t, 0), (1, t, 0), (-1, -t, 0), (1, -t, 0), (0, -1, t), (0, 1, t), (0, -1, -t), (0, 1, -t),
         (t, 0, -1), (t, 0, 1), (-t, 0, -1), (-t, 0, 1)]
faces = [(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6),
         (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10),
         (8, 6, 7), (9, 8, 1)]
def norm(v):
    l = math.sqrt(sum(c * c for c in v)); return tuple(c / l for c in v)
verts = [norm(v) for v in verts]
SUB = int(sys.argv[1]) if len(sys.argv) > 1 else 2
for _ in range(SUB - 1):
    cache = {}
    def mid(a, b):
        k = (min(a, b), max(a, b))
        if k not in cache:
            verts.append(norm(tuple((verts[a][i] + verts[b][i]) / 2 for i in range(3))))
            cache[k] = len(verts) - 1
        return cache[k]
    nf = []
    for a, b, c in faces:
        ab, bc, ca = mid(a, b), mid(b, c), mid(c, a)
        nf += [(a, ab, ca), (b, bc, ab), (c, ca, bc), (ab, bc, ca)]
    faces = nf
edges = sorted({(min(a, b), max(a, b)) for f in faces for a, b in ((f[0], f[1]), (f[1], f[2]), (f[2], f[0]))})

def rot(v, ax, ay):
    x, y, z = v
    x, z = x * math.cos(ay) + z * math.sin(ay), -x * math.sin(ay) + z * math.cos(ay)
    y, z = y * math.cos(ax) - z * math.sin(ax), y * math.sin(ax) + z * math.cos(ax)
    return x, y, z
P = [rot(v, 0.35, 0.5) for v in verts]

out = []
o = out.append
o(f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {W} {H}" width="{W}" height="{H}">')
o('''<defs>
<radialGradient id="bg" cx="50%" cy="48%" r="60%"><stop offset="0" stop-color="#101622"/><stop offset="0.55" stop-color="#05070b"/><stop offset="1" stop-color="#000"/></radialGradient>
<radialGradient id="halo" cx="50%" cy="50%" r="50%"><stop offset="0" stop-color="#cfe0ff" stop-opacity="0.30"/><stop offset="0.45" stop-color="#9fb8ff" stop-opacity="0.10"/><stop offset="1" stop-color="#9fb8ff" stop-opacity="0"/></radialGradient>
<radialGradient id="core" cx="45%" cy="40%" r="60%"><stop offset="0" stop-color="#eef3ff" stop-opacity="0.22"/><stop offset="1" stop-color="#9fb8ff" stop-opacity="0.04"/></radialGradient>
<filter id="glow" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="6"/></filter>
<filter id="soft" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="1.2"/></filter>
</defs>''')
o(f'<rect width="{W}" height="{H}" fill="url(#bg)"/>')

# --- water: rings of dots ---------------------------------------------------------
def water_y_offset(r, a):
    # ripple: small radial wobble so rings look like moving water
    return 3.0 * math.sin(a * 5 + r * 0.05) + 2.0 * math.sin(a * 9 - r * 0.03)

dots_back, dots_front = [], []
r = R * 1.08
ring = 0
while r < 640:
    spacing = 10 + ring * 1.1
    n = int(2 * math.pi * r / spacing)
    for i in range(n):
        a = 2 * math.pi * i / n + ring * 0.13
        rr = r + water_y_offset(r, a) * (1 + ring * 0.1)
        x = CX + rr * math.cos(a)
        y = WY + rr * math.sin(a) * TILT
        if not (-10 < x < W + 10 and -10 < y < H + 10):
            continue
        front = math.sin(a) > 0
        # light from the orb: brighter near it and toward the viewer
        d = (r - R * 1.08) / 480
        light = max(0.0, 1 - d) ** 1.6
        crest = 0.55 + 0.45 * math.sin(r * 0.11 + 4 * math.cos(a))
        op = (0.12 + 0.88 * light) * crest * (0.65 + 0.35 * max(0, math.sin(a)))
        size = 0.9 + 2.3 * light * crest + (0.6 if front else 0)
        if not front and math.hypot(x - CX, y - CY) < R * 1.02:
            continue  # hidden behind the orb
        (dots_front if front else dots_back).append((x, y, size, op))
    r += 15 + ring * 1.6
    ring += 1

def dots(lst):
    for x, y, s, op in lst:
        if op > 0.03:
            o(f'<circle cx="{x:.1f}" cy="{y:.1f}" r="{s:.2f}" fill="#dfe8ff" fill-opacity="{min(op,1):.2f}"/>')

o('<g>'); dots(dots_back); o('</g>')

# --- reflection: the wireframe's points, mirrored and broken up by the ripples -------
o('<g filter="url(#soft)">')
for a, b in edges:
    pa, pb = P[a], P[b]
    steps = 7
    for k in range(steps + 1):
        u = k / steps
        x3 = pa[0] + (pb[0] - pa[0]) * u; y3 = pa[1] + (pb[1] - pa[1]) * u; z3 = pa[2] + (pb[2] - pa[2]) * u
        if z3 < -0.1:
            continue
        sx = CX + x3 * R
        sy = WY + 18 + (y3 + 1) * R * 0.55        # flipped (y up = further down), squashed
        sx += 7 * math.sin(sy * 0.09)              # ripple distortion
        fade = max(0.0, 1 - (sy - WY) / (R * 1.25))
        if fade <= 0.02 or random.random() < 0.15:
            continue
        o(f'<circle cx="{sx:.1f}" cy="{sy:.1f}" r="1.5" fill="#cfdcff" fill-opacity="{0.75*fade:.2f}"/>')
o('</g>')

# --- orb --------------------------------------------------------------------------
o(f'<circle cx="{CX}" cy="{CY}" r="{R*2.1:.0f}" fill="url(#halo)"/>')
o(f'<circle cx="{CX}" cy="{CY}" r="{R:.0f}" fill="url(#core)"/>')
def lines(sel, stroke, width, op_front, op_back, extra=''):
    o(f'<g stroke="{stroke}" stroke-linecap="round" fill="none" {extra}>')
    for a, b in edges:
        za = (P[a][2] + P[b][2]) / 2
        if not sel(za):
            continue
        op = op_back + (op_front - op_back) * (za + 1) / 2
        x1, y1 = CX + P[a][0] * R, CY + P[a][1] * R
        x2, y2 = CX + P[b][0] * R, CY + P[b][1] * R
        o(f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" stroke-width="{width}" stroke-opacity="{op:.2f}"/>')
    o('</g>')
lines(lambda z: z < 0, '#9fb6ff', 1.2, 0.45, 0.18)                    # back edges, faint
lines(lambda z: z >= 0, '#bcd0ff', 6, 0.55, 0.15, 'filter="url(#glow)"')  # glow
lines(lambda z: z >= 0, '#ffffff', 2.0, 1.0, 0.55)                    # front edges
for i, p in enumerate(P):                                             # vertices
    if p[2] >= 0:
        o(f'<circle cx="{CX+p[0]*R:.1f}" cy="{CY+p[1]*R:.1f}" r="{2.2+1.6*p[2]:.1f}" fill="#fff" fill-opacity="{0.5+0.5*p[2]:.2f}"/>')

o('<g>'); dots(dots_front); o('</g>')
o('</svg>')
open(sys.argv[2] if len(sys.argv) > 2 else 'orb.svg', 'w').write('\n'.join(out))
