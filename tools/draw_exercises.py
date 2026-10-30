"""Draws the exercise illustrations as Android vector drawables (own artwork, CC-BY-SA-4.0).

Each exercise has a start pose (A) and an end pose (B); the app alternates them to show the
movement. Figures are simple pictograms built from joint angles, so every image shares one style.

    python tools/draw_exercises.py            # writes app/src/main/res/drawable/ex_*.xml
    python tools/draw_exercises.py preview    # also writes tools/preview.png (needs Pillow)

Angles are in degrees, 0 = right, 90 = down (screen coordinates). Figures face right.
"""
import math
import pathlib
import sys

W, H = 200, 150
GROUND = 130  # lowest point of the figure
TORSO, NECK, HEAD = 40, 5, 9
UA, FA, TH, SH, FOOT = 21, 20, 26, 26, 8
LIMB, BODY = 8, 10

ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "app/src/main/res/drawable"


def at(p, ang, length):
    r = math.radians(ang)
    return (p[0] + math.cos(r) * length, p[1] + math.sin(r) * length)


def pose(torso=-90, arm_n=(90, 90), arm_f=None, leg_n=(90, 90), leg_f=None, foot_n=None, foot_f=None, front=False):
    """Returns named segments; far limbs default to the near ones."""
    arm_f = arm_f or arm_n
    leg_f = leg_f or leg_n
    hip = (0.0, 0.0)
    neck = at(hip, torso, TORSO)
    head = at(neck, torso, NECK + HEAD)
    segs = {"torso": (hip, neck), "head": head}
    for side, arm, leg, foot in (("n", arm_n, leg_n, foot_n), ("f", arm_f, leg_f, foot_f)):
        sx = 0.0
        if front:
            # Front view: mirror the far side horizontally around the spine.
            sign = 1 if side == "n" else -1
            arm = (arm[0] if sign == 1 else 180 - arm[0], arm[1] if sign == 1 else 180 - arm[1])
            leg = (leg[0] if sign == 1 else 180 - leg[0], leg[1] if sign == 1 else 180 - leg[1])
            sx = 7 * sign
        sh = (neck[0] + sx, neck[1] + 3)
        el = at(sh, arm[0], UA)
        ha = at(el, arm[1], FA)
        hp = (hip[0] + sx * 0.7, hip[1])
        kn = at(hp, leg[0], TH)
        an = at(kn, leg[1], SH)
        segs[f"arm_{side}"] = (sh, el, ha)
        segs[f"leg_{side}"] = (hp, kn, an)
        if not front:
            fa = foot if foot is not None else leg[1] - 90
            segs[f"foot_{side}"] = (an, at(an, fa, FOOT))
    if front:
        segs["shoulders"] = (segs["arm_f"][0], segs["arm_n"][0])
        segs["hips"] = (segs["leg_f"][0], segs["leg_n"][0])
    return segs


def points(s):
    for k, v in s.items():
        if k == "head":
            yield v, HEAD
        else:
            for p in v:
                yield p, LIMB / 2


def bbox(s):
    xs = [p[0] - r for p, r in points(s)] + [p[0] + r for p, r in points(s)]
    ys = [p[1] - r for p, r in points(s)] + [p[1] + r for p, r in points(s)]
    return min(xs), min(ys), max(xs), max(ys)


def moved(s, dx, dy):
    out = {}
    for k, v in s.items():
        out[k] = (v[0] + dx, v[1] + dy) if k == "head" else tuple((p[0] + dx, p[1] + dy) for p in v)
    return out


# name: (pose A, pose B or None, props)
E = {
    "push_up": (pose(-22, (90, 90), (92, 92), (158, 158), (159, 159)),
                pose(-14, (150, 70), (152, 72), (166, 166), (167, 167)), []),
    "knee_push_up": (pose(-32, (90, 90), (92, 92), (125, 180), (127, 180)),
                     pose(-16, (150, 70), (152, 72), (140, 180), (142, 180)), ["mat"]),
    "squat": (pose(-90, (90, 90), (92, 92), (90, 90), (91, 91)),
              pose(-62, (0, 0), (4, 4), (10, 102), (12, 104)), []),
    "lunge": (pose(-90, (90, 90), (92, 92), (90, 90), (91, 91)),
              pose(-90, (90, 90), (92, 92), (8, 92), (112, 172), foot_f=200), []),
    "glute_bridge": (pose(180, (6, 0), (8, 0), (-48, 72), (-46, 74)),
                     pose(164, (12, 4), (14, 4), (-14, 88), (-12, 90)), ["mat"]),
    "plank": (pose(-12, (90, 0), (92, 2), (168, 168), (169, 169)), None, ["mat"]),
    "side_plank": (pose(-22, (90, 0), (-90, -90), (160, 160), (162, 162), front=False), None, ["mat"]),
    "crunch": (pose(180, (2, 2), (4, 4), (-52, 64), (-50, 66)),
               pose(212, (-14, -14), (-12, -12), (-52, 64), (-50, 66)), ["mat"]),
    "superman": (pose(0, (0, 0), (2, 2), (180, 180), (182, 182), foot_n=180, foot_f=180),
                 pose(-10, (-14, -14), (-12, -12), (188, 190), (190, 192), foot_n=190, foot_f=192), ["mat"]),
    "jumping_jack": (pose(-90, (74, 82), (74, 82), (93, 93), (93, 93), front=True),
                     pose(-90, (-55, -75), (-55, -75), (66, 70), (66, 70), front=True), []),
    "mountain_climber": (pose(-26, (90, 90), (92, 92), (40, 125), (156, 156)),
                         pose(-26, (90, 90), (92, 92), (154, 154), (42, 127)), []),
    "wall_sit": (pose(-90, (60, 20), (62, 22), (0, 90), (2, 91)), None, ["wall"]),
    "calf_raise": (pose(-90, (90, 90), (92, 92), (90, 90), (91, 91)),
                   pose(-90, (90, 90), (92, 92), (90, 90), (91, 91), foot_n=58, foot_f=60), []),
    "bird_dog": (pose(0, (90, 90), (92, 92), (90, 180), (92, 180)),
                 pose(0, (-6, -6), (92, 92), (90, 180), (178, 180), foot_f=180), ["mat"]),
    "biceps_curl": (pose(-90, (92, 92), (94, 94), (90, 90), (91, 91)),
                    pose(-90, (95, -75), (97, -73), (90, 90), (91, 91)), ["dumbbell"]),
    "shoulder_press": (pose(-90, (110, -88), (112, -86), (90, 90), (91, 91)),
                       pose(-90, (-88, -90), (-86, -88), (90, 90), (91, 91)), ["dumbbell"]),
    "bent_over_row": (pose(-22, (90, 90), (92, 92), (100, 80), (102, 82)),
                      pose(-22, (150, 62), (152, 64), (100, 80), (102, 82)), ["dumbbell"]),
    "goblet_squat": (pose(-90, (62, -100), (64, -98), (90, 90), (91, 91)),
                     pose(-66, (70, -60), (72, -58), (10, 102), (12, 104)), ["dumbbell"]),
    "chair_dip": (pose(-90, (96, 92), (98, 94), (14, 72), (16, 74)),
                  pose(-90, (148, 62), (150, 64), (-6, 78), (-4, 80)), ["chair"]),
    "high_knees": (pose(-86, (122, 45), (60, -35), (-12, 92), (94, 95)),
                   pose(-86, (60, -35), (122, 45), (94, 95), (-12, 92)), []),
    "leg_raise": (pose(180, (6, 0), (8, 0), (-6, -6), (-4, -4), foot_n=-90, foot_f=-88),
                  pose(180, (6, 0), (8, 0), (-84, -84), (-82, -82), foot_n=0, foot_f=2), ["mat"]),
    "lateral_raise": (pose(-90, (84, 88), (84, 88), (92, 92), (92, 92), front=True),
                      pose(-90, (2, -4), (2, -4), (92, 92), (92, 92), front=True), ["dumbbell"]),
    # Yoga: A = entering the pose, B = holding it.
    "yoga_mountain": (pose(-90, (88, 90), (88, 90), (90, 90), (90, 90), front=True),
                      pose(-90, (-78, -92), (-78, -92), (90, 90), (90, 90), front=True), ["mat"]),
    "yoga_tree": (pose(-90, (88, 90), (88, 90), (90, 90), (90, 90), front=True),
                  pose(-90, (-62, -118), (-62, -118), (91, 91), (32, 192), front=True), ["mat"]),
    "yoga_warrior": (pose(-90, (88, 90), (88, 90), (80, 95), (80, 95), front=True),
                     pose(-90, (0, 0), (0, 0), (25, 92), (58, 58), front=True), ["mat"]),
    "yoga_triangle": (pose(-90, (88, 90), (88, 90), (62, 62), (62, 62), front=True),
                      pose(-28, (62, 62), (-62, -62), (62, 62), (62, 62), front=True), ["mat"]),
    "yoga_downward_dog": (pose(0, (90, 90), (92, 92), (90, 180), (92, 180)),
                          pose(52, (52, 52), (54, 54), (126, 126), (128, 128), foot_n=60, foot_f=62), ["mat"]),
    "yoga_cobra": (pose(0, (150, 30), (152, 32), (180, 180), (182, 182), foot_n=180, foot_f=182),
                   pose(-52, (128, 98), (130, 100), (180, 180), (182, 182), foot_n=180, foot_f=182), ["mat"]),
    "yoga_child": (pose(-90, (90, 90), (92, 92), (90, 180), (92, 180), foot_n=180, foot_f=180),
                   pose(14, (6, 4), (8, 6), (32, 182), (34, 182), foot_n=180, foot_f=180), ["mat"]),
    "yoga_forward_bend": (pose(-90, (-82, -84), (-80, -82), (0, 0), (2, 2), foot_n=-90, foot_f=-88),
                          pose(-22, (8, 4), (10, 6), (0, 0), (2, 2), foot_n=-90, foot_f=-88), ["mat"]),
    "yoga_savasana": (pose(180, (18, 4), (20, 6), (2, 0), (4, 2), foot_n=-80, foot_f=-78), None, ["mat"]),
    # Pilates (on the mat, lying with the head to the left unless prone).
    "pilates_hundred": (pose(198, (6, 6), (8, 8), (-38, -38), (-36, -36), foot_n=-128, foot_f=-126),
                        pose(198, (14, 14), (16, 16), (-38, -38), (-36, -36), foot_n=-128, foot_f=-126), ["mat"]),
    "pilates_roll_up": (pose(180, (180, 180), (182, 182), (0, 0), (2, 2), foot_n=-90, foot_f=-88),
                        pose(-28, (8, 6), (10, 8), (0, 0), (2, 2), foot_n=-90, foot_f=-88), ["mat"]),
    "pilates_single_leg": (pose(200, (-40, 10), (-38, 12), (-115, -10), (-14, -14), foot_n=-100, foot_f=-104),
                           pose(200, (-40, 10), (-38, 12), (-14, -14), (-115, -10), foot_n=-104, foot_f=-100), ["mat"]),
    "pilates_leg_circle": (pose(180, (8, 2), (10, 4), (-90, -90), (2, 2), foot_n=-180, foot_f=-88),
                           pose(180, (8, 2), (10, 4), (-58, -58), (2, 2), foot_n=-148, foot_f=-88), ["mat"]),
    "pilates_swimming": (pose(-6, (-14, -14), (4, 4), (182, 182), (192, 192), foot_n=182, foot_f=192),
                         pose(-6, (4, 4), (-14, -14), (192, 192), (182, 182), foot_n=192, foot_f=182), ["mat"]),
    "pilates_teaser": (pose(180, (180, 180), (182, 182), (0, 0), (2, 2), foot_n=-90, foot_f=-88),
                       pose(-128, (-40, -40), (-38, -38), (-38, -38), (-36, -36), foot_n=-128, foot_f=-126), ["mat"]),
}


def walk_frame(i, n=8):
    """One frame of a walk cycle: legs and arms swing in opposite phase, knees bend on the swing."""
    t = 2 * math.pi * i / n
    sw = math.sin(t)
    th_n, th_f = 90 - 24 * sw, 90 + 24 * sw
    bend_n = 8 + 34 * max(0.0, math.cos(t))
    bend_f = 8 + 34 * max(0.0, -math.cos(t))
    ua_n, ua_f = 90 + 26 * sw, 90 - 26 * sw
    return pose(-84, (ua_n, ua_n - 38), (ua_f, ua_f - 38), (th_n, th_n + bend_n), (th_f, th_f + bend_f))


def scaled(s, k):
    out = {}
    for key, v in s.items():
        out[key] = (v[0] * k, v[1] * k) if key == "head" else tuple((p[0] * k, p[1] * k) for p in v)
    return out


def placed(name):
    a, b, props = E[name]
    poses = [a, b or a]
    # Tall poses (arms overhead) are scaled down so head and hands stay inside the picture.
    tallest = max(bbox(p)[3] - bbox(p)[1] for p in poses)
    room = GROUND - 8
    if tallest > room:
        k = room / tallest
        poses = [scaled(p, k) for p in poses]
    # Shared horizontal offset so the figure does not jump between poses.
    boxes = [bbox(p) for p in poses]
    minx = min(x[0] for x in boxes)
    maxx = max(x[2] for x in boxes)
    dx = W / 2 - (minx + maxx) / 2
    return [moved(p, dx, GROUND - bbox(p)[3]) for p in poses], props


def shapes(s, props, chair_from=None, female=False):
    """(kind, data, colour) in drawing order: props behind, far limbs, body, near limbs."""
    out = [("line", ((8, GROUND + 1.5), (W - 8, GROUND + 1.5)), "fig_ground", 2)]
    x0, y0, x1, y1 = bbox(s)
    if "mat" in props:
        out.append(("rect", (x0 - 6, GROUND - 2, x1 + 6, GROUND + 2), "fig_prop_light", 0))
    if "wall" in props:
        wx = s["torso"][0][0] - 10
        out.append(("rect", (wx - 6, 20, wx, GROUND + 1.5), "fig_prop_light", 0))
    if "chair" in props:
        hand = (chair_from or s)["arm_n"][2]
        top = hand[1] + LIMB / 2
        cx0, cx1 = hand[0] - 34, hand[0] + 2
        out.append(("rect", (cx0, top, cx1, top + 5), "fig_prop", 0))
        out.append(("line", ((cx0 + 3, top + 5), (cx0 + 3, GROUND)), "fig_prop", 4))
        out.append(("line", ((cx1 - 3, top + 5), (cx1 - 3, GROUND)), "fig_prop", 4))
    front = "shoulders" in s
    g = "_f" if female else ""
    sfx = "" if front else "_far"
    tx, ty = s["torso"][1][0] - s["torso"][0][0], s["torso"][1][1] - s["torso"][0][1]
    tl = math.hypot(tx, ty) or 1
    ux, uy = tx / tl, ty / tl

    def arm(seg, shade):
        out.append(("poly", seg, "fig_skin" + shade, LIMB))
        sh, el = seg[0], seg[1]
        sleeve = (sh, (sh[0] + (el[0] - sh[0]) * 0.5, sh[1] + (el[1] - sh[1]) * 0.5))
        out.append(("line", sleeve, "fig_shirt" + g + shade, LIMB + 1))

    if "arm_f" in s:
        arm(s["arm_f"], sfx)
    if "leg_f" in s:
        out.append(("poly", s["leg_f"], "fig_pants" + g + sfx, LIMB + 1))
    if "foot_f" in s:
        out.append(("poly", s["foot_f"], "fig_shoe", LIMB - 1))
    if front:
        out.append(("line", s["hips"], "fig_pants" + g, BODY + 2))
    hx, hy = s["head"]
    if female:
        # Long hair: a ponytail behind the head (side view) or hair down both sides (front view).
        bx, by = uy, -ux
        if front:
            for side in (-1, 1):
                out.append(("line", ((hx + side * HEAD * 0.8, hy - 2), (hx + side * HEAD * 0.9, hy + HEAD * 1.3)), "fig_hair" + g, 8))
        else:
            tail = (hx + bx * HEAD * 1.05 - ux * HEAD * 0.2, hy + by * HEAD * 1.05 - uy * HEAD * 0.2)
            end = (tail[0] - ux * HEAD * 0.9, tail[1] - uy * HEAD * 0.9)
            out.append(("line", (tail, end), "fig_hair" + g, 8))
    out.append(("line", s["torso"], "fig_shirt" + g, BODY + 4))
    if front:
        out.append(("line", s["shoulders"], "fig_shirt" + g, BODY + 2))
    out.append(("circle", ((hx + ux * 2.2, hy + uy * 2.2), HEAD + 0.5), "fig_hair" + g, 0))
    out.append(("circle", ((hx - ux * 0.8, hy - uy * 0.8), HEAD - 1.2), "fig_skin", 0))
    if "leg_n" in s:
        out.append(("poly", s["leg_n"], "fig_pants" + g, LIMB + 1))
    if "foot_n" in s:
        out.append(("poly", s["foot_n"], "fig_shoe", LIMB - 1))
    if "arm_n" in s:
        arm(s["arm_n"], "")
    if "dumbbell" in props:
        for k in ("arm_f", "arm_n"):
            out.append(("circle", (s[k][2], 6.5), "fig_prop", 0))
    return out


def f(v):
    return f"{v:.1f}".rstrip("0").rstrip(".")


def vector(sh, w=W):
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             "<!-- Generated by tools/draw_exercises.py. Own artwork, CC-BY-SA-4.0. -->",
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{w}dp" android:height="{H}dp"',
             f'    android:viewportWidth="{w}" android:viewportHeight="{H}">']
    for kind, d, col, sw in sh:
        if kind in ("line", "poly"):
            path = "M" + " L".join(f"{f(x)},{f(y)}" for x, y in d)
            lines.append(f'    <path android:strokeColor="@color/{col}" android:strokeWidth="{sw}" android:strokeLineCap="round" '
                         f'android:strokeLineJoin="round" android:fillColor="#00000000" android:pathData="{path}" />')
        elif kind == "circle":
            (x, y), r = d
            path = f"M{f(x - r)},{f(y)}a{f(r)},{f(r)} 0 1,0 {f(2 * r)},0a{f(r)},{f(r)} 0 1,0 {f(-2 * r)},0z"
            lines.append(f'    <path android:fillColor="@color/{col}" android:pathData="{path}" />')
        elif kind == "rect":
            x0, y0, x1, y1 = d
            path = f"M{f(x0)},{f(y0)}H{f(x1)}V{f(y1)}H{f(x0)}z"
            lines.append(f'    <path android:fillColor="@color/{col}" android:pathData="{path}" />')
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


COLORS = {"fig_shirt": (30, 136, 229), "fig_shirt_far": (21, 101, 192), "fig_pants": (55, 71, 79), "fig_pants_far": (38, 50, 56),
          "fig_skin": (242, 194, 155), "fig_skin_far": (214, 160, 120), "fig_hair": (93, 64, 55), "fig_shoe": (55, 71, 79),
          "fig_prop": (0, 137, 123), "fig_prop_light": (178, 223, 219), "fig_ground": (207, 216, 220), "fig_shadow": (220, 226, 230),
          "fig_shirt_f": (236, 64, 122), "fig_shirt_f_far": (194, 24, 91), "fig_pants_f": (94, 53, 177), "fig_pants_f_far": (69, 39, 160),
          "fig_hair_f": (78, 52, 46)}


def preview(all_shapes):
    from PIL import Image, ImageDraw
    sc = 2
    names = list(all_shapes)
    img = Image.new("RGB", (W * sc * 2, H * sc * len(names)), "white")
    d = ImageDraw.Draw(img)
    for row, name in enumerate(names):
        for col, sh in enumerate(all_shapes[name]):
            ox, oy = col * W * sc, row * H * sc
            def P(p):
                return (ox + p[0] * sc, oy + p[1] * sc)
            for kind, data, c, sw in sh:
                c = COLORS[c]
                if kind in ("line", "poly"):
                    pts = [P(p) for p in data]
                    d.line(pts, fill=c, width=int(sw * sc), joint="curve")
                    for p in pts:
                        r = sw * sc / 2
                        d.ellipse((p[0] - r, p[1] - r, p[0] + r, p[1] + r), fill=c)
                elif kind == "circle":
                    (x, y), r = data
                    x, y = P((x, y))
                    d.ellipse((x - r * sc, y - r * sc, x + r * sc, y + r * sc), fill=c)
                elif kind == "rect":
                    a, b = P(data[:2]), P(data[2:])
                    d.rectangle((a[0], a[1], b[0], b[1]), fill=c)
            d.text((ox + 6, oy + 4), f"{name} {'AB'[col]}", fill=(200, 0, 0))
    img.save(ROOT / "tools/preview.png")


def walker(female=False):
    """Walk cycle frames on a transparent background with a soft shadow (ui: WalkingBuddy)."""
    frames = [walk_frame(i) for i in range(8)]
    hip_x = 60
    out = []
    for p in frames:
        q = moved(p, hip_x, GROUND - bbox(p)[3])
        sh = shapes(q, [], female=female)
        sh[0] = ("rect", (hip_x - 26, GROUND - 1, hip_x + 26, GROUND + 3), "fig_shadow", 0)
        out.append(sh)
    return out


def main():
    all_shapes = {}
    names = []
    for name in E:
        (a, b), props = placed(name)
        for female, pre in ((False, "ex"), (True, "exf")):
            sa, sb = shapes(a, props, female=female), shapes(b, props, chair_from=a, female=female)
            (OUT / f"{pre}_{name}_a.xml").write_text(vector(sa), encoding="utf-8")
            (OUT / f"{pre}_{name}_b.xml").write_text(vector(sb), encoding="utf-8")
            if female:
                all_shapes[name + " (f)"] = [sa, sb]
            else:
                all_shapes[name] = [sa, sb]
        names.append(name)
    for female, pre in ((False, "walker"), (True, "walkerf")):
        frames = walker(female)
        for i, sh in enumerate(frames):
            (OUT / f"{pre}_{i}.xml").write_text(vector(sh, w=120), encoding="utf-8")
        all_shapes[pre + " 0/1"] = [frames[0], frames[1]]
    write_kotlin(names)
    if "preview" in sys.argv:
        preview(all_shapes)
    print(len(E), "exercises (male + female) and walker frames drawn")


def write_kotlin(names):
    """Maps every male picture to its female twin, so themes can swap the character."""
    lines = ["package io.github.smyrna_pergamum.exercisetracker.ui", "",
             "import io.github.smyrna_pergamum.exercisetracker.R", "",
             "// Generated by tools/draw_exercises.py; do not edit.",
             "object Art {",
             "    val female: Map<Int, Int> = mapOf("]
    for n in names:
        for ab in "ab":
            lines.append(f"        R.drawable.ex_{n}_{ab} to R.drawable.exf_{n}_{ab},")
    lines += ["    )", "",
              "    val walker = listOf(" + ", ".join(f"R.drawable.walker_{i}" for i in range(8)) + ")",
              "    val walkerFemale = listOf(" + ", ".join(f"R.drawable.walkerf_{i}" for i in range(8)) + ")",
              "}", ""]
    (ROOT / "app/src/main/java/io/github/smyrna_pergamum/exercisetracker/ui/Art.kt").write_text(chr(10).join(lines), encoding="utf-8")


if __name__ == "__main__":
    main()
