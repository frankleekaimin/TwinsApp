#!/usr/bin/env python3
"""
Generate TwinsApp Android launcher icons.
Design: Two mirrored chat bubbles (WhatsApp palette) on dark teal background.
"""

from PIL import Image, ImageDraw
import os
import math

# Colors
BG_COLOR = "#075E54"       # Dark Teal Green
BUBBLE_COLOR = "#25D366"   # Light Green
ACCENT_COLOR = "#FFFFFF"   # White (unused for now, kept for future)

# Android mipmap sizes: (directory_suffix, px_size)
SIZES = [
    ("mdpi",    48),
    ("hdpi",    72),
    ("xhdpi",   96),
    ("xxhdpi",  144),
    ("xxxhdpi", 192),
]

RES_DIR = "app/src/main/res"


def hex_to_rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i+2], 16) for i in (0, 2, 4))


def draw_chat_bubble(draw, x1, y1, x2, y2, radius, fill, tail_side="right"):
    """Draw a rounded-rect chat bubble with a small tail."""
    # Main bubble body
    draw.rounded_rectangle([x1, y1, x2, y2], radius=radius, fill=fill)

    # Tail — small triangle emerging from the bottom edge
    tail_w = (x2 - x1) * 0.20   # tail base width ~20% of bubble width
    tail_h = (y2 - y1) * 0.25   # tail height ~25% of bubble height

    if tail_side == "right":
        # Tail at bottom-right, pointing outward-right
        bx = x2 - radius * 0.6
        pts = [
            (bx - tail_w, y2),         # base-left
            (bx,          y2),         # base-right (overlaps corner)
            (x2 + tail_w * 0.7, y2 + tail_h),  # tip
        ]
    else:
        # Tail at bottom-left, pointing outward-left (mirror)
        bx = x1 + radius * 0.6
        pts = [
            (bx,          y2),         # base-left
            (bx + tail_w, y2),         # base-right
            (x1 - tail_w * 0.7, y2 + tail_h),  # tip
        ]

    draw.polygon(pts, fill=fill)


def render_icon(size, shape="square"):
    """Render icon at given pixel size. shape='square' or 'round'."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    s = size / 512.0  # scale factor relative to 512 master

    # ── Background ──────────────────────────────────────────────────────────
    bg_r = int(115 * s)
    if shape == "round":
        # Full circle clipped to canvas
        draw.ellipse([0, 0, size - 1, size - 1], fill=BG_COLOR)
    else:
        draw.rounded_rectangle([0, 0, size - 1, size - 1], radius=bg_r, fill=BG_COLOR)

    # ── Bubble layout (master coords at 512px) ───────────────────────────────
    # Both bubbles vertically centered; left half / right half
    # Gap between them: 28px  |  outer padding: 65px
    lx1, ly1, lx2, ly2 = 65,  165, 242, 312   # left bubble  (177 × 147)
    rx1, ry1, rx2, ry2 = 270, 165, 447, 312   # right bubble (177 × 147) — mirror
    bub_r = int(28 * s)

    def sc(v):
        return v * s

    draw_chat_bubble(
        draw,
        sc(lx1), sc(ly1), sc(lx2), sc(ly2),
        bub_r,
        BUBBLE_COLOR,
        tail_side="right",   # left bubble tail points right (inward)
    )
    draw_chat_bubble(
        draw,
        sc(rx1), sc(ry1), sc(rx2), sc(ry2),
        bub_r,
        BUBBLE_COLOR,
        tail_side="left",    # right bubble tail points left (inward, mirror)
    )

    return img


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    res_path = os.path.join(script_dir, RES_DIR)

    # Generate a 512px preview PNG first
    preview = render_icon(512, "square")
    preview_path = os.path.join(script_dir, "icon_preview.png")
    preview.save(preview_path)
    print(f"Preview saved → {preview_path}")

    # Generate all Android mipmap sizes
    for dpi, px in SIZES:
        mipmap_dir = os.path.join(res_path, f"mipmap-{dpi}")

        for shape, filename in [("square", "ic_launcher.webp"),
                                 ("round",  "ic_launcher_round.webp")]:
            icon = render_icon(px, shape)
            out_path = os.path.join(mipmap_dir, filename)
            icon.save(out_path, "WEBP", quality=100)
            print(f"  {dpi:12s} {px:3d}×{px:3d}  {shape:6s}  → {out_path}")

    print("\nDone. All launcher icons updated.")


if __name__ == "__main__":
    main()
