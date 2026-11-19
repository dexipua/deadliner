"""Draws the Deadliner bot avatars (640x640 PNG). Run: python docs/brand/make_avatars.py"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = Path(__file__).parent
S = 640          # final size
K = 4            # supersampling for smooth edges
W = S * K
BLUE_TOP = (47, 111, 224)
BLUE_BOTTOM = (25, 61, 156)
WHITE = (255, 255, 255)
GREEN = (34, 197, 120)
AMBER = (255, 184, 64)
INK = (24, 52, 128)


def gradient():
    img = Image.new("RGB", (W, W))
    px = img.load()
    for y in range(W):
        for x in range(0, W, 4):
            t = (x + y) / (2 * W)  # diagonal
            c = tuple(round(a + (b - a) * t) for a, b in zip(BLUE_TOP, BLUE_BOTTOM))
            for dx in range(4):
                px[x + dx, y] = c
    return img


def shadow(base, shape_mask, offset=(0, 18), blur=28, alpha=90):
    sh = Image.new("L", base.size, 0)
    sh.paste(shape_mask, offset)
    sh = sh.filter(ImageFilter.GaussianBlur(blur * K / 2))
    sh = sh.point(lambda v: v * alpha // 255)
    dark = Image.new("RGB", base.size, (10, 25, 70))
    base.paste(dark, (0, 0), sh)


def check(draw, cx, cy, size, color, width):
    pts = [(cx - size * 0.5, cy), (cx - size * 0.12, cy + size * 0.38), (cx + size * 0.55, cy - size * 0.38)]
    draw.line(pts, fill=color, width=width, joint="curve")
    r = width // 2
    for x, y in (pts[0], pts[-1]):
        draw.ellipse((x - r, y - r, x + r, y + r), fill=color)


def u(v):
    return round(v * K)


def calendar():
    img = gradient()
    mask = Image.new("L", img.size, 0)
    m = ImageDraw.Draw(mask)
    box = (u(170), u(185), u(470), u(470))
    m.rounded_rectangle(box, radius=u(48), fill=255)
    shadow(img, mask)
    d = ImageDraw.Draw(img)
    d.rounded_rectangle(box, radius=u(48), fill=WHITE)
    # header band
    d.rounded_rectangle((box[0], box[1], box[2], u(265)), radius=u(48), fill=AMBER)
    d.rectangle((box[0], u(235), box[2], u(265)), fill=AMBER)
    # rings
    for x in (u(245), u(395)):
        d.rounded_rectangle((x - u(14), u(160), x + u(14), u(220)), radius=u(14), fill=INK)
    check(d, u(322), u(370), u(150), GREEN, u(38))
    return img


def clock():
    img = gradient()
    mask = Image.new("L", img.size, 0)
    c, r = u(320), u(160)
    ImageDraw.Draw(mask).ellipse((c - r, c - r, c + r, c + r), fill=255)
    shadow(img, mask)
    d = ImageDraw.Draw(img)
    d.ellipse((c - r, c - r, c + r, c + r), fill=WHITE)
    for i in range(12):
        a = math.radians(i * 30)
        r1, r2 = (r * 0.78, r * 0.9) if i % 3 else (r * 0.72, r * 0.9)
        d.line((c + r1 * math.sin(a), c - r1 * math.cos(a), c + r2 * math.sin(a), c - r2 * math.cos(a)),
               fill=(190, 200, 222) if i % 3 else INK, width=u(10 if i % 3 == 0 else 6))
    # 23:55 — five minutes to a Moodle deadline
    for angle, length, width in ((-2.5, 0.5, 22), (-30, 0.72, 14)):
        a = math.radians(angle)
        end = (c + r * length * math.sin(a), c - r * length * math.cos(a))
        d.line((c, c, *end), fill=INK, width=u(width))
        hw = u(width) / 2
        d.ellipse((end[0] - hw, end[1] - hw, end[0] + hw, end[1] + hw), fill=INK)
    d.ellipse((c - u(18), c - u(18), c + u(18), c + u(18)), fill=AMBER)
    badge(d, u(440), u(440))
    return img


def badge(d, bx, by, br=None):
    br = br or u(78)
    d.ellipse((bx - br - u(10), by - br - u(10), bx + br + u(10), by + br + u(10)), fill=BLUE_BOTTOM)
    d.ellipse((bx - br, by - br, bx + br, by + br), fill=GREEN)
    check(d, bx + u(2), by + u(4), br, WHITE, u(20))


def letter():
    img = gradient()
    d = ImageDraw.Draw(img)
    font_path = next((p for p in (r"C:\Windows\Fonts\segoeuib.ttf", r"C:\Windows\Fonts\arialbd.ttf") if Path(p).exists()), None)
    font = ImageFont.truetype(font_path, u(330)) if font_path else ImageFont.load_default()
    text = "D"
    l, t, rr, b = d.textbbox((0, 0), text, font=font)
    x = (W - (rr - l)) / 2 - l - u(10)
    y = (W - (b - t)) / 2 - t - u(10)
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).text((x, y), text, font=font, fill=255)
    shadow(img, mask)
    d.text((x, y), text, font=font, fill=WHITE)
    badge(d, u(430), u(430), u(70))
    return img


def save(img, name):
    img.resize((S, S), Image.Resampling.LANCZOS).save(OUT / name, optimize=True)


if __name__ == "__main__":
    variants = {"avatar-calendar.png": calendar(), "avatar-clock.png": clock(), "avatar-letter.png": letter()}
    for name, img in variants.items():
        save(img, name)
    # preview: the three as Telegram shows them (circles), side by side
    gap = 40
    preview = Image.new("RGB", (S * 3 + gap * 4, S + gap * 2), (238, 241, 246))
    circle = Image.new("L", (S, S), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, S - 1, S - 1), fill=255)
    for i, name in enumerate(variants):
        preview.paste(Image.open(OUT / name), (gap + i * (S + gap), gap), circle)
    preview.save(OUT / "avatars-preview.png", optimize=True)
    print("ok")
