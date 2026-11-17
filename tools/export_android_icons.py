"""Export Android drawables and PNGs from the Stillune SVG master.

Install Pillow and resvg-py, then run python tools/export_android_icons.py.
"""
from io import BytesIO
import json
import math
from pathlib import Path
import xml.etree.ElementTree as ET

from PIL import Image, ImageCms, ImageDraw
from resvg_py import svg_to_bytes

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "assets/branding"
OUT = ROOT / "assets/android"
RES = OUT / "res"
SVG = "http://www.w3.org/2000/svg"
ANDROID = "http://schemas.android.com/apk/res/android"
VIEWBOX = 1024
LAYER_DP = 108
VISIBLE_DP = 72
INSET_DP = (LAYER_DP - VISIBLE_DP) / 2
SAFE_RADIUS_DP = 33
LAYER_PX = 1080


def render(markup, size):
    data = svg_to_bytes(svg_string=markup, width=size, height=size, skip_system_fonts=True)
    return Image.open(BytesIO(data)).convert("RGBA")


def vector(paths, size_dp, adaptive=False):
    body = "\n".join(
        f'    <path android:fillColor="{color}" android:fillType="{rule}" android:pathData="{path}" />'
        for color, rule, path in paths
    )
    viewport = LAYER_DP if adaptive else VIEWBOX
    if adaptive:
        scale = VISIBLE_DP / VIEWBOX
        body = (
            f'    <group android:translateX="{INSET_DP:g}" android:translateY="{INSET_DP:g}"'
            f' android:scaleX="{scale}" android:scaleY="{scale}">\n'
            + "\n".join("    " + line for line in body.splitlines())
            + "\n    </group>"
        )
    return (
        f'<vector xmlns:android="{ANDROID}" android:width="{size_dp}dp" android:height="{size_dp}dp"'
        f' android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n{body}\n</vector>\n'
    )


def masked(image, shape):
    size = image.width
    mask = Image.new("L", (size * 4, size * 4))
    edge = size * 4 - 1
    draw = ImageDraw.Draw(mask)
    if shape == "round":
        draw.ellipse((0, 0, edge, edge), fill=255)
    elif shape == "rounded-square":
        draw.rounded_rectangle((0, 0, edge, edge), radius=round(edge * .26), fill=255)
    else:
        center = edge / 2
        angles = (i * math.tau / 720 for i in range(720))
        points = [
            (center * (1 + math.copysign(abs(math.cos(t)) ** .5, math.cos(t))),
             center * (1 + math.copysign(abs(math.sin(t)) ** .5, math.sin(t))))
            for t in angles
        ]
        draw.polygon(points, fill=255)
    result = image.copy()
    result.putalpha(mask.resize(image.size, Image.Resampling.LANCZOS))
    return result


def main():
    source = BRAND / "stillune-icon.svg"
    design = ET.parse(source).getroot()
    elements = {item.get("id"): item for item in design if item.get("id")}
    background = elements["background"].attrib["fill"]
    dot = elements["center"].attrib
    x, y, radius = (float(dot[key]) for key in ("cx", "cy", "r"))
    dot_path = (f"M{x + radius:g},{y:g} A{radius:g},{radius:g} 0 1 0 {x - radius:g},{y:g}"
                f" A{radius:g},{radius:g} 0 1 0 {x + radius:g},{y:g} Z")
    paths = [(elements[name].attrib["fill"], "nonZero", elements[name].attrib["d"])
             for name in ("outer", "inner", "groove")]
    paths.append((dot["fill"], "nonZero", dot_path))
    outer, inner, groove, center = (item[2] for item in paths)
    monochrome = [("#FFFFFF", "evenOdd", f"{outer} {groove}"),
                  ("#FFFFFF", "nonZero", inner), ("#FFFFFF", "nonZero", center)]

    drawables = RES / "drawable"
    (drawables / "ic_launcher_foreground.xml").write_text(vector(paths, LAYER_DP, adaptive=True), encoding="utf-8")
    (drawables / "ic_launcher_monochrome.xml").write_text(vector(monochrome, LAYER_DP, adaptive=True), encoding="utf-8")
    (drawables / "ic_launcher_background.xml").write_text(
        f'<shape xmlns:android="{ANDROID}" android:shape="rectangle">\n'
        f'    <solid android:color="{background}" />\n</shape>\n', encoding="utf-8")
    (ROOT / "app/src/main/res/drawable/ic_notification.xml").write_text(
        vector(monochrome, 24), encoding="utf-8")

    square = render(source.read_text(encoding="utf-8"), VIEWBOX)
    square.save(BRAND / "stillune-icon-source-square.png", optimize=True)
    masked(square, "rounded-square").save(BRAND / "stillune-icon.png", optimize=True)

    # The media session decodes its fallback cover as a bitmap.
    design.remove(elements["background"])
    artwork = render(ET.tostring(design, encoding="unicode"), 720)
    artwork.save(RES / "drawable-nodpi/stillune_foreground.png", optimize=True)
    foreground = Image.new("RGBA", (LAYER_PX, LAYER_PX))
    inset = round(INSET_DP * LAYER_PX / LAYER_DP)
    foreground.paste(artwork, (inset, inset))
    alpha = foreground.getchannel("A")
    bounds = alpha.getbbox()
    assert bounds
    visible_radius = max(
        math.hypot(i % LAYER_PX + .5 - LAYER_PX / 2, i // LAYER_PX + .5 - LAYER_PX / 2)
        for i, value in enumerate(alpha.tobytes()) if value
    )
    assert visible_radius <= SAFE_RADIUS_DP * LAYER_PX / LAYER_DP

    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        target = RES / f"mipmap-{density}"
        scaled = square.resize((size, size), Image.Resampling.LANCZOS)
        masked(scaled, "rounded-square").save(target / "ic_launcher.png", optimize=True)
        masked(scaled, "round").save(target / "ic_launcher_round.png", optimize=True)

    play = square.resize((512, 512), Image.Resampling.LANCZOS)
    assert play.getchannel("A").getextrema() == (255, 255)
    profile = ImageCms.ImageCmsProfile(ImageCms.createProfile("sRGB")).tobytes()
    play_path = OUT / "play-store-512.png"
    play.save(play_path, icc_profile=profile, optimize=True)
    assert play_path.stat().st_size <= 1024 * 1024

    preview = square.resize((720, 720), Image.Resampling.LANCZOS)
    for shape in ("round", "rounded-square", "squircle"):
        masked(preview, shape).save(OUT / f"previews/{shape}.png", optimize=True)
    themed_paths = "".join(f'<path fill="#304650" fill-rule="{rule.lower()}" d="{path}"/>'
                           for _, rule, path in monochrome)
    themed = render(f'<svg xmlns="{SVG}" viewBox="0 0 {VIEWBOX} {VIEWBOX}">'
                    f'<rect width="{VIEWBOX}" height="{VIEWBOX}" fill="#D8E4E9"/>'
                    f'{themed_paths}</svg>', 720)
    masked(themed, "round").save(OUT / "previews/themed.png", optimize=True)

    xml_files = list(RES.rglob("*.xml"))
    for path in xml_files:
        ET.parse(path)
    report = {"source": "assets/branding/stillune-icon.svg", "layer_px": LAYER_PX,
              "layer_dp": LAYER_DP, "visible_viewport_dp": VISIBLE_DP, "safe_circle_dp": 2 * SAFE_RADIUS_DP,
              "alpha_bbox": bounds, "visible_radius_px": round(visible_radius, 2),
              "play_bytes": play_path.stat().st_size, "xml_files": len(xml_files)}
    (OUT / "export-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(report))


if __name__ == "__main__":
    main()
