"""Draws the nature-sound pictures as vector drawables (own artwork, CC-BY-SA-4.0).

    python tools/draw_scenes.py            # writes app/src/main/res/drawable/scene_*.xml
    python tools/draw_scenes.py preview    # also writes tools/scenes.png (needs Pillow)

Each scene is a list of shapes on a 160×100 canvas: ("rect", x0, y0, x1, y1, colour),
("circle", cx, cy, r, colour), ("poly", [(x, y), …], colour) and ("line", [(x, y), …], colour, width).
"""
import math
import pathlib
import sys

W, H = 160, 100
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable"


def wave(y, amp, n, x0=0, x1=W, steps=40):
    return [(x0 + (x1 - x0) * i / steps, y + amp * math.sin(i / steps * n * 2 * math.pi)) for i in range(steps + 1)]


def tri(cx, base, w, h):
    return [(cx - w / 2, base), (cx, base - h), (cx + w / 2, base)]


def drop(cx, cy, r):
    pts = [(cx, cy - r * 2.2)]
    for i in range(0, 181, 15):
        a = math.radians(i)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a) * 1.0))
    pts[1:] = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(0, 181, 15)]
    return [(cx, cy - r * 2.2)] + [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(0, 181, 15)]


def flame(cx, base, w, h):
    pts = []
    for i in range(0, 181, 10):
        a = math.radians(i)
        pts.append((cx + (w / 2) * math.cos(a), base - (h * 0.35) * math.sin(a)))
    pts = [(cx + w / 2, base)] + [(cx + (w / 2) * math.cos(math.radians(a)), base - h * 0.3 * math.sin(math.radians(a))) for a in range(0, 181, 10)]
    tip = (cx + w * 0.1, base - h)
    return [(cx - w / 2, base - h * 0.25), tip, (cx + w / 2, base - h * 0.25)] + [(cx + (w / 2) * math.cos(math.radians(a)), base - h * 0.25 + (h * 0.25) * math.sin(math.radians(a))) for a in range(0, 181, 10)]


S = {
    "birds": [
        ("rect", 0, 0, W, H, "#BFE6FF"),
        ("circle", 130, 22, 12, "#FFD54F"),
        ("line", [(0, 70), (60, 62), (110, 66), (160, 58)], "#6D4C41", 5),
        ("line", [(60, 62), (75, 50)], "#6D4C41", 3),
        ("circle", 82, 46, 6, "#81C784"), ("circle", 70, 46, 5, "#66BB6A"),
        ("circle", 100, 55, 9, "#FF7043"),            # bird body
        ("circle", 108, 49, 5.5, "#FF7043"),           # head
        ("poly", [(113, 48), (120, 50), (113, 51)], "#FFB300"),
        ("circle", 109.5, 47.5, 1.3, "#212121"),
        ("poly", [(92, 52), (100, 47), (104, 56)], "#E64A19"),
        ("poly", [(91, 56), (82, 54), (90, 60)], "#E64A19"),
        ("line", [(30, 30), (35, 26), (40, 30)], "#455A64", 1.6), ("line", [(40, 30), (45, 26), (50, 30)], "#455A64", 1.6),
    ],
    "wind": [
        ("rect", 0, 0, W, H, "#DDE7F0"),
        ("line", [(15, 35), (90, 35), (100, 30), (98, 22), (90, 22), (88, 28)], "#78909C", 4),
        ("line", [(25, 55), (120, 55), (132, 48), (128, 38), (118, 40)], "#90A4AE", 4),
        ("line", [(10, 75), (75, 75), (85, 81), (82, 89), (74, 87)], "#78909C", 4),
        ("poly", [(130, 72), (145, 64), (150, 78), (138, 82)], "#8BC34A"),
        ("line", [(130, 72), (150, 78)], "#558B2F", 1.5),
    ],
    "water": [
        ("rect", 0, 0, W, H, "#B2EBF2"),
        ("poly", wave(45, 4, 3) + [(W, H), (0, H)], "#4DD0E1"),
        ("poly", wave(62, 4, 2.5) + [(W, H), (0, H)], "#26C6DA"),
        ("poly", wave(80, 3, 3.5) + [(W, H), (0, H)], "#00ACC1"),
        ("poly", drop(40, 28, 5), "#0097A7"), ("poly", drop(120, 20, 4), "#0097A7"),
    ],
    "forest": [
        ("rect", 0, 0, W, H, "#C8E6C9"),
        ("circle", 30, 24, 10, "#FFF59D"),
        ("rect", 0, 80, W, H, "#7CB342"),
        ("poly", tri(40, 82, 40, 60), "#2E7D32"), ("poly", tri(40, 62, 30, 40), "#388E3C"),
        ("poly", tri(85, 84, 46, 72), "#1B5E20"), ("poly", tri(85, 60, 34, 46), "#2E7D32"),
        ("poly", tri(128, 82, 38, 54), "#388E3C"), ("poly", tri(128, 64, 28, 36), "#43A047"),
        ("rect", 38, 82, 42, 90, "#5D4037"), ("rect", 83, 84, 87, 92, "#5D4037"), ("rect", 126, 82, 130, 90, "#5D4037"),
    ],
    "restaurant": [
        ("rect", 0, 0, W, H, "#FFE0B2"),
        ("rect", 0, 70, W, H, "#A1887F"),
        ("rect", 20, 60, 140, 66, "#6D4C41"), ("rect", 30, 66, 35, 92, "#5D4037"), ("rect", 125, 66, 130, 92, "#5D4037"),
        ("circle", 62, 54, 13, "#FFFFFF"), ("circle", 62, 54, 8, "#FFCC80"),
        ("line", [(42, 42), (42, 60)], "#9E9E9E", 2), ("line", [(82, 42), (82, 60)], "#9E9E9E", 2),
        ("rect", 100, 40, 112, 58, "#FFFFFF"), ("line", [(112, 45), (118, 45), (118, 52), (112, 52)], "#FFFFFF", 2),
        ("line", [(104, 36), (102, 30), (106, 24)], "#BDBDBD", 1.5), ("line", [(109, 36), (107, 30), (111, 24)], "#BDBDBD", 1.5),
        ("circle", 140, 18, 6, "#FFB74D"), ("circle", 20, 18, 6, "#FFB74D"),
    ],
    "city": [
        ("rect", 0, 0, W, H, "#7E57C2"), ("rect", 0, 55, W, H, "#9575CD"),
        ("circle", 130, 20, 8, "#FFF59D"),
        ("rect", 10, 40, 35, H, "#37474F"), ("rect", 38, 25, 62, H, "#455A64"), ("rect", 65, 50, 88, H, "#37474F"),
        ("rect", 92, 15, 118, H, "#263238"), ("rect", 122, 45, 150, H, "#455A64"),
    ] + [("rect", x, y, x + 4, y + 5, "#FFE082") for x in (15, 25, 43, 52, 97, 107, 128, 140) for y in (50, 62, 74, 86) if not (x in (15, 25) and y == 50)],
    "rain": [
        ("rect", 0, 0, W, H, "#90A4AE"),
        ("circle", 60, 32, 16, "#ECEFF1"), ("circle", 82, 26, 20, "#ECEFF1"), ("circle", 105, 34, 14, "#ECEFF1"),
        ("rect", 52, 34, 112, 48, "#ECEFF1"),
    ] + [("line", [(x, y), (x - 4, y + 10)], "#1E88E5", 2.2) for x, y in
         [(55, 56), (70, 62), (85, 56), (100, 62), (115, 56), (62, 76), (78, 82), (94, 76), (110, 82), (48, 84), (125, 74)]],
    "fire": [
        ("rect", 0, 0, W, H, "#263238"),
        ("circle", 30, 20, 1.2, "#FFF59D"), ("circle", 120, 14, 1.2, "#FFF59D"), ("circle", 140, 30, 1, "#FFF59D"), ("circle", 50, 10, 1, "#FFF59D"),
        ("poly", flame(80, 84, 46, 62), "#FF7043"),
        ("poly", flame(80, 84, 30, 44), "#FFB300"),
        ("poly", flame(80, 84, 14, 24), "#FFF176"),
        ("line", [(52, 92), (108, 80)], "#6D4C41", 8), ("line", [(52, 80), (108, 92)], "#5D4037", 8),
    ],
    "night": [
        ("rect", 0, 0, W, H, "#1A237E"),
        ("circle", 128, 22, 11, "#FFF9C4"), ("circle", 133, 18, 10, "#1A237E"),
        ("circle", 30, 15, 1.3, "#FFFFFF"), ("circle", 60, 25, 1, "#FFFFFF"), ("circle", 95, 12, 1.3, "#FFFFFF"), ("circle", 15, 35, 1, "#FFFFFF"),
        ("poly", wave(70, 2, 2) + [(W, H), (0, H)], "#283593"),
        ("circle", 80, 86, 30, "#3949AB"),
        ("circle", 80, 66, 9, "#66BB6A"), ("circle", 74, 59, 3.5, "#66BB6A"), ("circle", 86, 59, 3.5, "#66BB6A"),
        ("circle", 74, 59, 1.6, "#FFFFFF"), ("circle", 86, 59, 1.6, "#FFFFFF"),
        ("circle", 74, 59, 0.8, "#212121"), ("circle", 86, 59, 0.8, "#212121"),
        ("line", [(10, 100), (14, 72)], "#2E7D32", 2), ("line", [(18, 100), (20, 78)], "#2E7D32", 2), ("line", [(146, 100), (150, 74)], "#2E7D32", 2),
        ("line", [(40, 40), (46, 36), (52, 40)], "#C5E1A5", 1.2),
    ],
    "aquarium": [
        ("rect", 0, 0, W, H, "#4FC3F7"), ("rect", 0, 88, W, H, "#FFE0B2"),
        ("line", [(20, 90), (16, 70), (22, 55), (18, 40)], "#43A047", 3), ("line", [(140, 90), (144, 72), (138, 58)], "#43A047", 3),
        ("circle", 70, 45, 10, "#FF8A65"), ("poly", [(78, 45), (90, 37), (90, 53)], "#FF7043"), ("circle", 65, 43, 1.6, "#212121"),
        ("circle", 112, 66, 7, "#FFD54F"), ("poly", [(118, 66), (127, 60), (127, 72)], "#FFB300"), ("circle", 108, 64, 1.2, "#212121"),
        ("circle", 50, 30, 2.5, "#E1F5FE"), ("circle", 46, 20, 1.8, "#E1F5FE"), ("circle", 52, 12, 1.2, "#E1F5FE"),
    ],
}


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def vector(shapes):
    out = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- Generated by tools/draw_scenes.py. Own artwork, CC-BY-SA-4.0. -->",
           '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
           f'    android:width="{W}dp" android:height="{H}dp" android:viewportWidth="{W}" android:viewportHeight="{H}">']
    for s in shapes:
        k = s[0]
        if k == "rect":
            _, x0, y0, x1, y1, c = s
            out.append(f'    <path android:fillColor="{c}" android:pathData="M{f(x0)},{f(y0)}H{f(x1)}V{f(y1)}H{f(x0)}z" />')
        elif k == "circle":
            _, cx, cy, r, c = s
            out.append(f'    <path android:fillColor="{c}" android:pathData="M{f(cx - r)},{f(cy)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z" />')
        elif k == "poly":
            _, pts, c = s
            out.append(f'    <path android:fillColor="{c}" android:pathData="M' + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + 'z" />')
        elif k == "line":
            _, pts, c, w = s
            out.append(f'    <path android:strokeColor="{c}" android:strokeWidth="{w}" android:strokeLineCap="round" android:strokeLineJoin="round" '
                       f'android:fillColor="#00000000" android:pathData="M' + " L".join(f"{f(x)},{f(y)}" for x, y in pts) + '" />')
    out.append("</vector>")
    return "\n".join(out) + "\n"


def preview():
    from PIL import Image, ImageDraw
    sc = 3
    img = Image.new("RGB", (W * sc * 2, H * sc * ((len(S) + 1) // 2)), "white")
    d = ImageDraw.Draw(img)
    for i, (name, shapes) in enumerate(S.items()):
        ox, oy = (i % 2) * W * sc, (i // 2) * H * sc
        P = lambda p: (ox + p[0] * sc, oy + p[1] * sc)
        for s in shapes:
            k = s[0]
            if k == "rect":
                d.rectangle((*P(s[1:3]), *P(s[3:5])), fill=s[5])
            elif k == "circle":
                cx, cy = P(s[1:3]); r = s[3] * sc
                d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=s[4])
            elif k == "poly":
                d.polygon([P(p) for p in s[1]], fill=s[2])
            elif k == "line":
                d.line([P(p) for p in s[1]], fill=s[2], width=int(s[3] * sc), joint="curve")
        d.text((ox + 4, oy + 4), name, fill="black")
    img.save(ROOT / "tools/scenes.png")


if __name__ == "__main__":
    for name, shapes in S.items():
        (OUT / f"scene_{name}.xml").write_text(vector(shapes), encoding="utf-8")
    if "preview" in sys.argv:
        preview()
    print(len(S), "scenes drawn")
