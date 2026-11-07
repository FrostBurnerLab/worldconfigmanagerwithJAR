import math
from PIL import Image, ImageDraw, ImageFilter
S = 2048  # supersampled, downscaled to 512
img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

# Background: rounded square with a vertical graphite gradient.
bg = Image.new("RGBA", (S, S))
d = ImageDraw.Draw(bg)
for y in range(S):
    t = y / S
    c = (int(38 - 14 * t), int(43 - 15 * t), int(52 - 17 * t), 255)
    d.line([(0, y), (S, y)], fill=c)
mask = Image.new("L", (S, S), 0)
ImageDraw.Draw(mask).rounded_rectangle([0, 0, S - 1, S - 1], radius=380, fill=255)
img.paste(bg, (0, 0), mask)
d = ImageDraw.Draw(img)
# Subtle blue glow behind the block.
glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
ImageDraw.Draw(glow).ellipse([380, 330, 1670, 1620], fill=(74, 158, 240, 70))
glow = glow.filter(ImageFilter.GaussianBlur(160))
img = Image.alpha_composite(img, Image.composite(glow, Image.new("RGBA", (S, S), (0, 0, 0, 0)), mask))
d = ImageDraw.Draw(img)

# Isometric "world" block.
cx, cy, r = 980, 1000, 560
top = [(cx, cy - r), (cx + r * 0.866, cy - r / 2), (cx, cy), (cx - r * 0.866, cy - r / 2)]
left = [(cx - r * 0.866, cy - r / 2), (cx, cy), (cx, cy + r), (cx - r * 0.866, cy + r / 2)]
right = [(cx, cy), (cx + r * 0.866, cy - r / 2), (cx + r * 0.866, cy + r / 2), (cx, cy + r)]
d.polygon(left, fill=(121, 85, 58))
d.polygon(right, fill=(92, 64, 44))
d.polygon(top, fill=(98, 176, 74))
# Grass overhang on the sides.
gl = [(cx - r * 0.866, cy - r / 2), (cx, cy), (cx, cy + 120), (cx - r * 0.866, cy - r / 2 + 120)]
gr = [(cx, cy), (cx + r * 0.866, cy - r / 2), (cx + r * 0.866, cy - r / 2 + 120), (cx, cy + 120)]
d.polygon(gl, fill=(80, 150, 60)); d.polygon(gr, fill=(64, 124, 48))
# Pixel texture flecks (deterministic).
import random
rng = random.Random(7)
def fleck(poly_fn, n, color):
    for _ in range(n):
        u, v = rng.random(), rng.random()
        x, y = poly_fn(u, v)
        d.rectangle([x - 22, y - 22, x + 22, y + 22], fill=color)
fleck(lambda u, v: (cx - r * 0.866 * (1 - u) + 0 * u, cy - r / 2 * (1 - u) + 0 + 200 + v * (r - 260) * 1.0 + u * (r / 2) - u * r / 2 + (u * r / 2)), 0, None) if False else None
for face, base, col in ((left, (cx - r * 0.866, cy - r / 2), (100, 70, 47)), (right, (cx, cy), (76, 52, 36))):
    for _ in range(26):
        u, v = rng.uniform(0.08, 0.92), rng.uniform(0.32, 0.92)
        if face is left:
            x = cx - r * 0.866 * (1 - u); y = (cy - r / 2) + (r / 2) * u + r * v
        else:
            x = cx + r * 0.866 * u; y = cy - (r / 2) * u + r * v
        d.rectangle([x - 20, y - 20, x + 20, y + 20], fill=col)
for _ in range(30):
    u, v = rng.uniform(0.1, 0.9), rng.uniform(0.1, 0.9)
    x = cx + (u - v) * r * 0.866; y = cy - r + (u + v) * r / 2
    d.rectangle([x - 20, y - 14, x + 20, y + 14], fill=(118, 196, 88))
# Edges.
for poly in (top, left, right):
    d.line(poly + [poly[0]], fill=(20, 22, 27), width=14, joint="curve")

# Blue gear badge (configuration).
gx, gy = 1500, 1500
R, teeth = 360, 10
pts = []
for i in range(teeth * 2 * 6):
    a = 2 * math.pi * i / (teeth * 2 * 6)
    phase = (i // 6) % 2
    rr = R if phase == 0 else R * 0.80
    pts.append((gx + rr * math.cos(a), gy + rr * math.sin(a)))
d.ellipse([gx - R - 40, gy - R - 40, gx + R + 40, gy + R + 40], fill=(17, 19, 23))  # outline ring
d.polygon(pts, fill=(74, 158, 240))
d.ellipse([gx - R * 0.62, gy - R * 0.62, gx + R * 0.62, gy + R * 0.62], fill=(58, 136, 214))
d.ellipse([gx - R * 0.30, gy - R * 0.30, gx + R * 0.30, gy + R * 0.30], fill=(17, 19, 23))
# Highlight on gear.
d.arc([gx - R * 0.55, gy - R * 0.55, gx + R * 0.55, gy + R * 0.55], 200, 320, fill=(140, 200, 255), width=26)

out = img.resize((512, 512), Image.LANCZOS)
out.save("/tmp/claude-0/icon/wcm-icon-512.png", optimize=True)
out.resize((128, 128), Image.LANCZOS).save("/tmp/claude-0/icon/wcm-icon-128.png", optimize=True)
print("ok")
