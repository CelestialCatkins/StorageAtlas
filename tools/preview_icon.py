#!/usr/bin/env python3
"""按 ic_launcher_*.xml 的坐标画预览图（纯 PIL，用于人工核对）。"""
from PIL import Image, ImageDraw, ImageFilter
import math, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'docs', 'images')
os.makedirs(OUT, exist_ok=True)

C = (54.0, 54.0)
S = 4  # supersample


def qbez(p0, p1, p2, n=64):
    pts = []
    for i in range(n + 1):
        t = i / n
        x = (1 - t) ** 2 * p0[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * p2[0]
        y = (1 - t) ** 2 * p0[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * p2[1]
        pts.append((x, y))
    return pts


def disc(d, cx, cy, r, fill):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S], fill=fill)


def stroke_path(d, pts, w, color):
    wpx = w * S
    d.line(pts, fill=color, width=int(round(wpx)), joint="curve")
    for p in (pts[0], pts[-1]):
        d.ellipse([p[0] - wpx / 2, p[1] - wpx / 2, p[0] + wpx / 2, p[1] + wpx / 2], fill=color)


def halo(d, cx, cy, r, w, color):
    d.ellipse([(cx - r) * S, (cy - r) * S, (cx + r) * S, (cy + r) * S],
              outline=color, width=int(round(w * S)))


def bg(size):
    """莫兰迪蓝纯色底"""
    return Image.new("RGBA", (size, size), (103, 125, 145, 255))


def fg(size, mono=False, tint=(255, 255, 255)):
    """size: 输出像素；坐标基于 108 画布"""
    big = size * S
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = big / 108.0  # 108 -> big

    def sc(p):
        return (p[0] * k, p[1] * k)

    def disc2(cx, cy, r, fill):
        d.ellipse([(cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k], fill=fill)

    def stroke(pts, w, color):
        wp = w * k
        # 圆头笔刷：沿路径密集打点，避免 PIL 分段线出现断口
        dense = []
        for a, b in zip(pts, pts[1:]):
            seg = max(2, int(math.hypot(b[0] - a[0], b[1] - a[1]) * 3))
            for i in range(seg):
                t = i / seg
                dense.append((x if False else a[0] + (b[0] - a[0]) * t,
                              a[1] + (b[1] - a[1]) * t))
        dense.append(pts[-1])
        rr = wp / 2
        for (x, y) in dense:
            cx, cy = x * k, y * k
            d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=color)

    W = tint if mono else None
    INK = (246, 242, 234, 255)
    trunk = W or INK
    topbr = W or INK
    branch = W or INK

    stroke([(54, 79.4), (54, 49.8)], 5.9, trunk)
    stroke([(54, 49.8), (54, 29.6)], 4.7, topbr)
    stroke(qbez((54, 56.4), (41.8, 52.7), (32.1, 38.2)), 4.7, branch)
    stroke(qbez((54, 56.4), (66.2, 52.7), (75.9, 38.2)), 4.7, branch)

    disc2(54, 56.4, 4.9, W or INK)
    disc2(32.1, 38.2, 3.96, W or INK)
    disc2(75.9, 38.2, 3.96, W or INK)
    disc2(54, 29.6, 3.35, W or INK)

    if mono:
        # 根节点：白环 + 镂空
        disc2(54, 79.4, 6.4, W)
        disc2(54, 79.4, 2.25, (0, 0, 0, 0))
    else:
        disc2(54, 79.4, 6.4, INK)
        disc2(54, 79.4, 2.25, (103, 125, 145, 255))

    return img.resize((size, size), Image.LANCZOS)


def squircle_mask(size, curve=0.3):
    """超椭圆近似 Android 的 squircle 遮罩"""
    img = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(img)
    n = 4.0
    pts = []
    R = size / 2.0
    for i in range(720):
        th = 2 * math.pi * i / 720
        ct, st = math.cos(th), math.sin(th)
        x = R * math.copysign(abs(ct) ** (2 / n), ct)
        y = R * math.copysign(abs(st) ** (2 / n), st)
        pts.append((R + x, R + y))
    d.polygon(pts, fill=255)
    return img


def icon(size, mono=False, tint=(255, 255, 255, 255), bgcol=None):
    if bgcol is None:
        base = bg(size)
    else:
        base = Image.new("RGBA", (size, size), bgcol)
    base.alpha_composite(fg(size, mono=mono, tint=tint))
    from PIL import ImageChops
    base.putalpha(ImageChops.multiply(base.getchannel("A"), squircle_mask(size)))
    return base


def on_sheet(sheet, ic, x, y, size, bgcol):
    bgp = Image.new("RGB", (size, size), bgcol)
    bgp.paste(ic, (0, 0), ic)
    sheet.paste(bgp, (x, y))


def label_sheet():
    PAD = 40
    cell = 512
    W = cell * 3 + PAD * 4
    H = cell + PAD * 2
    sheet = Image.new("RGB", (W, H), (238, 242, 246))
    # 1 彩色版（白底）
    on_sheet(sheet, icon(cell), PAD, PAD, cell, (255, 255, 255))
    # 2 单色层：浅色壁纸 + 深色染色
    on_sheet(sheet, icon(cell, mono=True, tint=(31, 42, 54, 255), bgcol=(0, 0, 0, 0)),
             PAD * 2 + cell, PAD, cell, (232, 237, 243))
    # 3 单色层：深色壁纸 + 白色染色
    on_sheet(sheet, icon(cell, mono=True, tint=(255, 255, 255, 255), bgcol=(0, 0, 0, 0)),
             PAD * 3 + cell * 2, PAD, cell, (20, 27, 36))
    sheet.save(os.path.join(OUT, 'icon-preview.png'))
    return sheet


def small_sheet():
    """桌面尺寸对照：96 / 72 / 48 / 32"""
    sizes = [192, 144, 96, 72, 48]
    W = sum(sizes) + 40 * (len(sizes) + 1)
    H = 292
    sheet = Image.new("RGB", (W, H), (250, 251, 252))
    x = 40
    for s in sizes:
        on_sheet(sheet, icon(s), x, 50, s, (255, 255, 255))
        x += s + 40
    sheet.save(os.path.join(OUT, 'icon-sizes.png'))
    return sheet


if __name__ == "__main__":
    label_sheet()
    small_sheet()
    print("ok")
