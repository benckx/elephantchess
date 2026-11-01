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
SIZE="${1:-512}"

if ! command -v rsvg-convert >/dev/null 2>&1; then
    echo "error: rsvg-convert not found (install the librsvg2-bin package)" >&2
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
    [janggi_cursive]="AR PL UKai CN, STKaiti, KaiTi, serif"
    [janggi_modern]="Noto Sans CJK KR, Noto Sans CJK SC, sans-serif"
)

declare -A STYLE_WEIGHT=(
    [janggi_cursive]="700"
    [janggi_modern]="900"
)

declare -A STYLE_TEXT_SIZE=(
    [janggi_cursive]="58"
    [janggi_modern]="62"
)

declare -A STYLE_TEXT_Y=(
    [janggi_cursive]="71"
    [janggi_modern]="73"
)

declare -A COLOR_TEXT=(
    [red]="#c0392b"
    [blue]="#1f5fbf"
)

declare -A COLOR_RING=(
    [red]="#b03328"
    [blue]="#1a4ea0"
)

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

    cat > "${svg_path}" <<EOF
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" width="256" height="256">
  <polygon points="50,4 89,27 89,73 50,96 11,73 11,27" fill="#fffaf0" stroke="${ring_color}" stroke-width="3.5"/>
  <polygon points="50,12 82,31 82,69 50,88 18,69 18,31" fill="none" stroke="${ring_color}" stroke-width="1.8"/>
  <text x="50" y="${text_y}" font-family="${font_family}" font-weight="${font_weight}" font-size="${text_size}" fill="${text_color}" text-anchor="middle">${glyph}</text>
</svg>
EOF
}

create_style() {
    local style="$1"
    local style_dir="${PIECES_DIR}/${style}"
    local svg_dir="${style_dir}/svg"

    mkdir -p "${svg_dir}"

    local piece
    for piece in "${!RED_CHARS[@]}"; do
        write_piece_svg "${style}" "red" "${piece}" "${RED_CHARS[${piece}]}" "${svg_dir}/red_${piece}.svg"
        write_piece_svg "${style}" "blue" "${piece}" "${BLUE_CHARS[${piece}]}" "${svg_dir}/blue_${piece}.svg"
    done

    local converted=0
    local svg
    for svg in "${svg_dir}"/*.svg; do
        local png="${style_dir}/$(basename "${svg}" .svg).png"
        rsvg-convert --width "${SIZE}" --height "${SIZE}" "${svg}" -o "${png}"
        converted=$((converted + 1))
    done

    echo "${style}: generated ${converted} PNG file(s)"
}

create_style "janggi_cursive"
create_style "janggi_modern"

echo "done: generated Janggi SVG + PNG assets under ${PIECES_DIR}"
