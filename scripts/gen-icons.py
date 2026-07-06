"""Generates placeholder PWA icons for Life OS.

Deliberate palette: deep ink navy background, warm brass mark.
Run: python3 scripts/gen-icons.py
"""
from PIL import Image, ImageDraw
import os

INK = (17, 20, 28, 255)       # #11141C
BRASS = (196, 156, 74, 255)   # #C49C4A

OUT_DIR = os.path.join(os.path.dirname(__file__), "..", "icons")
os.makedirs(OUT_DIR, exist_ok=True)


def draw_mark(draw, size, scale=1.0, offset=(0, 0)):
    """Angular monogram: a compass-like L/O ligature built from strokes."""
    cx, cy = size / 2 + offset[0], size / 2 + offset[1]
    s = size * 0.30 * scale

    # Outer ring segment (three-quarter arc) suggesting "O" / orbit
    bbox = [cx - s, cy - s, cx + s, cy + s]
    draw.arc(bbox, start=40, end=320, fill=BRASS, width=max(2, int(size * 0.045 * scale)))

    # Vertical stem suggesting "L"
    stem_w = max(2, int(size * 0.045 * scale))
    draw.line(
        [(cx - s * 0.05, cy - s * 0.85), (cx - s * 0.05, cy + s * 0.55)],
        fill=BRASS, width=stem_w,
    )
    # Base of the "L"
    draw.line(
        [(cx - s * 0.05, cy + s * 0.55), (cx + s * 0.55, cy + s * 0.55)],
        fill=BRASS, width=stem_w,
    )


def make_icon(size, path, padding_ratio=0.0):
    img = Image.new("RGBA", (size, size), INK)
    d = ImageDraw.Draw(img)
    scale = 1.0 - padding_ratio * 2
    draw_mark(d, size, scale=scale)
    img.save(path)


sizes = [72, 96, 128, 144, 152, 192, 384, 512]
for s in sizes:
    make_icon(s, os.path.join(OUT_DIR, f"icon-{s}.png"))

# Maskable icons need extra safe-zone padding (~20%) since OS may crop to a circle/squircle.
for s in [192, 512]:
    make_icon(s, os.path.join(OUT_DIR, f"icon-maskable-{s}.png"), padding_ratio=0.14)

# Favicon
make_icon(32, os.path.join(OUT_DIR, "favicon-32.png"))
make_icon(16, os.path.join(OUT_DIR, "favicon-16.png"))

print("Icons generated in", os.path.abspath(OUT_DIR))
