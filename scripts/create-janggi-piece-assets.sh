#!/usr/bin/env bash
#
# Generate Janggi piece assets as SVG and PNG.
#
# Creates two styles under images/pieces:
# - janggi_cursive (calligraphic/cursive font)
# - janggi_modern  (modern sans font)
#
# Output naming uses red_* and blue_* files.
#
# Requirements:
# - rsvg-convert (Debian/Ubuntu package: librsvg2-bin)
# - CJK fonts (e.g. fonts-noto-cjk, fonts-arphic-ukai)
#
set -euo pipefail

cd "$(dirname "$0")/.."

PIECES_DIR="webapp/src/main/resources/public/images/pieces"
SOURCE_SVG="webapp/src/main/resources/public/images/Janggi.svg"
SIZE="${1:-512}"

if ! command -v rsvg-convert >/dev/null 2>&1; then
    echo "error: rsvg-convert not found (install the librsvg2-bin package)" >&2
    exit 1
fi

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
)

declare -A STYLE_WEIGHT=(
    [janggi_cursive]="600"
    [janggi_modern]="800"
)

declare -A STYLE_TEXT_SIZE=(
    [janggi_cursive]="52"
    [janggi_modern]="54"
)

declare -A STYLE_TEXT_Y=(
    [janggi_cursive]="69"
    [janggi_modern]="69"
)

declare -A STYLE_FONT_STYLE=(
    [janggi_cursive]="italic"
    [janggi_modern]="normal"
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
piece_scale = {
    "general": 1.02,
    "advisor": 1.52,
    "cannon": 1.30,
    "soldier": 1.34,
    "default": 1.18,
}
y_shift = 2.4

def nearest(color: str, ex: float, ey: float):
    options = [c for c in candidates if c["color"] == color]
    return min(options, key=lambda c: (c["cx"] - ex) ** 2 + (c["cy"] - ey) ** 2)

svg_template = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="256" height="256">
  <polygon points="50,6 88,28 88,72 50,94 12,72 12,28" fill="#fffaf0" stroke="{ring_color}" stroke-width="2"/>
  <g transform="translate(50 50) translate(0 {y_shift:.3f}) scale({scale}) translate(-{cx:.3f} -{cy:.3f})">
    <path d="{path_data}" fill="{piece_color}"/>
  </g>
</svg>
"""

for (color, piece), (ex, ey) in expected.items():
    item = nearest(color, ex, ey)
    scale = piece_scale.get(piece, piece_scale["default"])
    output = svg_dir / f"{color}_{piece}.svg"
    output.write_text(
        svg_template.format(
            ring_color=ring_fill[color],
            piece_color=piece_fill[color],
            scale=scale,
            y_shift=y_shift,
            cx=item["cx"],
            cy=item["cy"],
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
  <polygon points="50,6 88,28 88,72 50,94 12,72 12,28" fill="#fffaf0" stroke="${ring_color}" stroke-width="2"/>
  <text x="50" y="${text_y}" font-family="${font_family}" font-style="${font_style}" font-weight="${font_weight}" font-size="${text_size}" fill="${text_color}" text-anchor="middle">${glyph}</text>
</svg>
EOF
}

create_style() {
    local style="$1"
    local style_dir="${PIECES_DIR}/${style}"
    local svg_dir="${style_dir}/svg"

    mkdir -p "${svg_dir}"

    if [ "${style}" = "janggi_cursive" ]; then
        create_cursive_svg_from_source "${svg_dir}" "${SOURCE_SVG}"
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
        rsvg-convert --background-color "transparent" --width "${SIZE}" --height "${SIZE}" "${svg}" -o "${png}"
        converted=$((converted + 1))
    done

    echo "${style}: generated ${converted} PNG file(s)"
}

create_style "janggi_cursive"
create_style "janggi_modern"

echo "done: generated Janggi SVG + PNG assets under ${PIECES_DIR}"
