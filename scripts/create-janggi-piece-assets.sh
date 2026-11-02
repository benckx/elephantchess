#!/usr/bin/env bash
#
# Generate Janggi piece assets as SVG and PNG.
#
# Creates three styles under images/pieces:
# - janggi_cursive    (calligraphic/cursive font)
# - janggi_modern     (modern sans font)
# - janggi_westernized (re-used xiangqi westernized glyphs in octagons)
#
# Output naming uses red_* and blue_* files.
# Usage: scripts/create-janggi-piece-assets.sh [size] [style]
# Omit style to generate all three styles.
#
# Requirements:
# - rsvg-convert (Debian/Ubuntu package: librsvg2-bin)
# - CJK fonts (e.g. fonts-noto-cjk, fonts-arphic-ukai)
# - Pillow (Python package, for westernized pictogram bounds)
#
set -euo pipefail

cd "$(dirname "$0")/.."

PIECES_DIR="webapp/src/main/resources/public/images/pieces"
SOURCE_SVG="webapp/src/main/resources/public/images/Janggi.svg"
SIZE="${1:-512}"

if ! command -v python3 >/dev/null 2>&1; then
    echo "error: python3 not found (required to extract cursive paths from ${SOURCE_SVG})" >&2
    exit 1
fi

declare -A RED_CHARS=(
    [general]="漢"
    [advisor]="士"
    [elephant]="象"
    [horse]="馬"
    [chariot]="車"
    [cannon]="包"
    [soldier]="兵"
)

declare -A BLUE_CHARS=(
    [general]="楚"
    [advisor]="士"
    [elephant]="象"
    [horse]="馬"
    [chariot]="車"
    [cannon]="包"
    [soldier]="卒"
)

declare -A STYLE_FONT=(
    [janggi_cursive]="KaiTi, STKaiti, Kaiti SC, AR PL UKai CN, Noto Serif CJK KR, serif"
    [janggi_modern]="Noto Sans CJK KR, Noto Sans CJK SC, sans-serif"
    [janggi_westernized]="Noto Sans CJK KR, Noto Sans CJK SC, sans-serif"
)

declare -A STYLE_WEIGHT=(
    [janggi_cursive]="600"
    [janggi_modern]="800"
    [janggi_westernized]="800"
)

declare -A STYLE_TEXT_SIZE=(
    [janggi_cursive]="52"
    [janggi_modern]="54"
    [janggi_westernized]="54"
)

declare -A STYLE_TEXT_Y=(
    [janggi_cursive]="69"
    [janggi_modern]="69"
    [janggi_westernized]="69"
)

declare -A STYLE_FONT_STYLE=(
    [janggi_cursive]="italic"
    [janggi_modern]="normal"
    [janggi_westernized]="normal"
)

declare -A COLOR_TEXT=(
    [red]="#c0392b"
    [blue]="#1a4a86"
)

declare -A COLOR_RING=(
    [red]="#b03328"
    [blue]="#153e71"
)

create_cursive_svg_from_source() {
    local svg_dir="$1"
    local source_svg="$2"

    python3 - "${svg_dir}" "${source_svg}" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

svg_dir = Path(sys.argv[1])
source_svg = Path(sys.argv[2])

if not source_svg.exists():
    raise SystemExit(f"error: source svg not found: {source_svg}")

ns = {"svg": "http://www.w3.org/2000/svg"}
number_re = re.compile(r"-?\d+(?:\.\d+)?")
token_re = re.compile(r"[MmCcLlHhVvZz]|-?\d*\.?\d+(?:[eE][-+]?\d+)?")
command_re = re.compile(r"[A-Za-z]")

def path_bbox(d: str):
    """Tight bounding box of an SVG path using only M/L/C (abs+rel) and Z."""
    tokens = token_re.findall(d)
    i = 0
    cx = cy = sx = sy = 0.0
    minx = miny = float("inf")
    maxx = maxy = float("-inf")
    cmd = None

    def include(x, y):
        nonlocal minx, miny, maxx, maxy
        minx = min(minx, x)
        miny = min(miny, y)
        maxx = max(maxx, x)
        maxy = max(maxy, y)

    def cubic(x0, y0, x1, y1, x2, y2, x3, y3):
        for k in range(21):
            t = k / 20.0
            mt = 1.0 - t
            x = mt**3 * x0 + 3 * mt * mt * t * x1 + 3 * mt * t * t * x2 + t**3 * x3
            y = mt**3 * y0 + 3 * mt * mt * t * y1 + 3 * mt * t * t * y2 + t**3 * y3
            include(x, y)

    while i < len(tokens):
        if command_re.match(tokens[i]):
            cmd = tokens[i]
            i += 1
            if cmd in ("z", "Z"):
                cx, cy = sx, sy
                cmd = None
                continue
        if cmd in ("M", "m"):
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            if cmd == "m":
                x += cx
                y += cy
            cx, cy = sx, sy = x, y
            include(cx, cy)
            cmd = "l" if cmd == "m" else "L"
        elif cmd in ("L", "l"):
            x, y = float(tokens[i]), float(tokens[i + 1])
            i += 2
            if cmd == "l":
                x += cx
                y += cy
            cx, cy = x, y
            include(cx, cy)
        elif cmd in ("C", "c"):
            vals = [float(v) for v in tokens[i:i + 6]]
            i += 6
            x1, y1, x2, y2, x3, y3 = vals
            if cmd == "c":
                x1 += cx; y1 += cy
                x2 += cx; y2 += cy
                x3 += cx; y3 += cy
            cubic(cx, cy, x1, y1, x2, y2, x3, y3)
            cx, cy = x3, y3
        else:
            i += 1

    return minx, miny, maxx, maxy

def parse_center(points: str):
    vals = [float(v) for v in number_re.findall(points)]
    xs = vals[0::2]
    ys = vals[1::2]
    return sum(xs) / len(xs), sum(ys) / len(ys)

tree = ET.parse(source_svg)
root = tree.getroot()

candidates = []
for group in root.findall(".//svg:g", ns):
    polygons = [
        n for n in group.findall(".//svg:polygon", ns)
        if "fill:#FFFFFF" in n.attrib.get("style", "") and n.attrib.get("points")
    ]
    paths = [
        n for n in group.findall(".//svg:path", ns)
        if "#DD1F26" in n.attrib.get("style", "") or "#006838" in n.attrib.get("style", "")
    ]
    if len(polygons) != 1 or len(paths) != 1:
        continue
    cx, cy = parse_center(polygons[0].attrib["points"])
    path = paths[0]
    style = path.attrib.get("style", "")
    color = "red" if "#DD1F26" in style else "blue"
    candidates.append({
        "color": color,
        "cx": cx,
        "cy": cy,
        "d": path.attrib["d"],
    })

expected = {
    ("red", "chariot"): (37, 37),
    ("red", "horse"): (109, 37),
    ("red", "elephant"): (181, 37),
    ("red", "advisor"): (253, 37),
    ("red", "general"): (326, 110),
    ("red", "cannon"): (109, 181),
    ("red", "soldier"): (37, 253),
    ("blue", "chariot"): (37, 685),
    ("blue", "horse"): (109, 685),
    ("blue", "elephant"): (181, 686),
    ("blue", "advisor"): (253, 685),
    ("blue", "general"): (326, 614),
    ("blue", "cannon"): (109, 541),
    ("blue", "soldier"): (37, 469),
}

piece_fill = {"red": "#c0392b", "blue": "#1a4a86"}
ring_fill = {"red": "#b03328", "blue": "#153e71"}
# Scale each glyph so its bounding box fills the octagon as much as possible
# while keeping its corners inside the octagon's slanted edges. The corner cuts
# lie on the line x+y=37.8 (and symmetric), so a bbox centered at (50, 50) stays
# inside when halfWidth + halfHeight <= ~60. Fitting to this limit gives every
# piece a uniform, near-maximal fill regardless of its native glyph size.
fill_half_extent = 60.0

def nearest(color: str, ex: float, ey: float):
    options = [c for c in candidates if c["color"] == color]
    return min(options, key=lambda c: (c["cx"] - ex) ** 2 + (c["cy"] - ey) ** 2)

svg_template = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="256" height="256">
  <polygon points="31.8,6 68.2,6 94,31.8 94,68.2 68.2,94 31.8,94 6,68.2 6,31.8" fill="#fffaf0" stroke="{ring_color}" stroke-width="2"/>
  <g transform="translate(50 50) translate(0 {y_shift:.3f}) scale({scale}) translate(-{cx:.3f} -{cy:.3f})">
    <path d="{path_data}" fill="{piece_color}"/>
  </g>
</svg>
"""

for (color, piece), (ex, ey) in expected.items():
    item = nearest(color, ex, ey)
    minx, miny, maxx, maxy = path_bbox(item["d"])
    glyph_w, glyph_h = maxx - minx, maxy - miny
    glyph_cx, glyph_cy = (minx + maxx) / 2.0, (miny + maxy) / 2.0
    scale = 2.0 * fill_half_extent / (glyph_w + glyph_h)
    output = svg_dir / f"{color}_{piece}.svg"
    output.write_text(
        svg_template.format(
            ring_color=ring_fill[color],
            piece_color=piece_fill[color],
            scale=scale,
            y_shift=0.0,
            cx=glyph_cx,
            cy=glyph_cy,
            path_data=item["d"],
        ),
        encoding="utf-8",
    )
PY
}

write_piece_svg() {
    local style="$1"
    local color="$2"
    local piece="$3"
    local glyph="$4"
    local svg_path="$5"

    local text_color="${COLOR_TEXT[${color}]}"
    local ring_color="${COLOR_RING[${color}]}"
    local font_family="${STYLE_FONT[${style}]}"
    local font_weight="${STYLE_WEIGHT[${style}]}"
    local text_size="${STYLE_TEXT_SIZE[${style}]}"
    local text_y="${STYLE_TEXT_Y[${style}]}"
    local font_style="${STYLE_FONT_STYLE[${style}]}"

    cat > "${svg_path}" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="256" height="256">
  <polygon points="31.8,6 68.2,6 94,31.8 94,68.2 68.2,94 31.8,94 6,68.2 6,31.8" fill="#fffaf0" stroke="${ring_color}" stroke-width="2"/>
  <text x="50" y="${text_y}" font-family="${font_family}" font-style="${font_style}" font-weight="${font_weight}" font-size="${text_size}" fill="${text_color}" text-anchor="middle">${glyph}</text>
</svg>
EOF
}

create_westernized_piece_svgs() {
    local svg_dir="$1"
    local source_svg="${PIECES_DIR}/wip/xiangqi_board_romanized.svg"

    python3 - "${svg_dir}" "${source_svg}" <<'PY'
import copy
import io
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

svg_dir = Path(sys.argv[1])
source_svg = Path(sys.argv[2])
namespace = "http://www.w3.org/2000/svg"
ET.register_namespace("", namespace)
source = ET.parse(source_svg).getroot()

# The final red row contains the rounded westernized pictograms, separately
# from their circular frames and backgrounds.
glyph_ids = {
    "chariot": "path1868-1-2-6-7",
    "cannon": "g1970-1-8-9-6",
    "horse": "g1814-6-2-8-2",
    "elephant": "g1312-3-4-3-7",
    "advisor": "path2466-7",
    "general": "path2116-7-9-0-3",
    "soldier": "path1982-5-7-0-0-0",
}
palette = {
    "red": {"main": "#c0392b", "ring": "#b03328"},
    "blue": {"main": "#1a4a86", "ring": "#153e71"},
}

for piece, glyph_id in glyph_ids.items():
    glyph = source.find(f".//*[@id='{glyph_id}']")
    if glyph is None:
        raise ValueError(f"missing source pictogram: {glyph_id}")

    # Measure the rendered vector, including its inherited transforms/strokes.
    probe = ET.Element(f"{{{namespace}}}svg", {
        "viewBox": "0 0 600 660", "width": "2400", "height": "2640",
    })
    probe.append(copy.deepcopy(glyph))
    data = ET.tostring(probe)
    if shutil.which("rsvg-convert"):
        rendered = subprocess.run(
            ["rsvg-convert"], input=data, capture_output=True, check=True,
        ).stdout
    else:
        from cairosvg import svg2png
        rendered = svg2png(bytestring=data)
    with Image.open(io.BytesIO(rendered)) as image:
        alpha = image.getchannel("A").point(lambda value: 255 if value > 128 else 0)
        bounds = alpha.getbbox()
    if bounds is None:
        raise ValueError(f"empty source pictogram: {glyph_id}")
    minx, miny, maxx, maxy = (value / 4 for value in bounds)
    width, height = maxx - minx, maxy - miny
    cx, cy = (minx + maxx) / 2, (miny + maxy) / 2
    # Give every pictogram the same longest edge, preserving its proportions.
    scale = 0.85 * 78 / max(width, height)

    for color, colors in palette.items():
        pictogram = copy.deepcopy(glyph)
        for node in pictogram.iter():
            for attr in ("style", "fill", "stroke"):
                if attr in node.attrib:
                    node.attrib[attr] = re.sub(
                        r"#(?:ad0000|000000)\b", colors["main"],
                        node.attrib[attr], flags=re.IGNORECASE,
                    )
        svg = ET.Element(f"{{{namespace}}}svg", {
            "viewBox": "5 5 90 90", "width": "512", "height": "512",
        })
        ET.SubElement(svg, f"{{{namespace}}}polygon", {
            "points": "31.8,6 68.2,6 94,31.8 94,68.2 68.2,94 31.8,94 6,68.2 6,31.8",
            "fill": "#fffaf0", "stroke": colors["ring"], "stroke-width": "2",
        })
        group = ET.SubElement(svg, f"{{{namespace}}}g", {
            "transform": f"translate(50 50) scale({scale:.8f}) translate(-{cx:.3f} -{cy:.3f})",
            "fill": colors["main"],
        })
        group.append(pictogram)
        ET.indent(svg)
        (svg_dir / f"{color}_{piece}.svg").write_bytes(ET.tostring(svg))
PY
}

create_style() {
    local style="$1"
    local style_dir="${PIECES_DIR}/${style}"
    local svg_dir="${style_dir}/svg"

    mkdir -p "${svg_dir}"

    if [ "${style}" = "janggi_cursive" ]; then
        create_cursive_svg_from_source "${svg_dir}" "${SOURCE_SVG}"
    elif [ "${style}" = "janggi_westernized" ]; then
        create_westernized_piece_svgs "${svg_dir}"
    else
        local piece
        for piece in "${!RED_CHARS[@]}"; do
            write_piece_svg "${style}" "red" "${piece}" "${RED_CHARS[${piece}]}" "${svg_dir}/red_${piece}.svg"
            write_piece_svg "${style}" "blue" "${piece}" "${BLUE_CHARS[${piece}]}" "${svg_dir}/blue_${piece}.svg"
        done
    fi

    local converted=0
    local svg
    for svg in "${svg_dir}"/*.svg; do
        local png="${style_dir}/$(basename "${svg}" .svg).png"
        if command -v rsvg-convert >/dev/null 2>&1; then
            rsvg-convert --background-color "transparent" --width "${SIZE}" --height "${SIZE}" "${svg}" -o "${png}"
        else
            python3 - "${svg}" "${png}" "${SIZE}" <<'PY'
import sys
from cairosvg import svg2png
from pathlib import Path
svg_path = Path(sys.argv[1])
png_path = Path(sys.argv[2])
size = int(sys.argv[3])
svg2png(url=str(svg_path), write_to=str(png_path), output_width=size, output_height=size)
PY
        fi
        converted=$((converted + 1))
    done

    echo "${style}: generated ${converted} PNG file(s)"
}

if [ "$#" -gt 1 ]; then
    case "$2" in
        janggi_cursive|janggi_modern|janggi_westernized)
            create_style "$2"
            ;;
        *)
            echo "error: unknown Janggi piece style: $2" >&2
            exit 1
            ;;
    esac
else
    create_style "janggi_cursive"
    create_style "janggi_modern"
    create_style "janggi_westernized"
fi

echo "done: generated Janggi SVG + PNG assets under ${PIECES_DIR}"
