#!/usr/bin/env bash
#
# Convert the SVG piece sources into PNG files.
#
# Some piece styles (MODERN, HAND_BRUSH) are authored as SVG using Chinese
# fonts. Because those fonts are not available on every device, we render the
# SVGs to PNG ahead of time so the board always looks the same.
#
# For every piece style folder under images/pieces/<style>/svg/*.svg this script
# writes a matching <style>/<piece>.png next to the style folder.
#
# Requirements: rsvg-convert (Debian/Ubuntu package: librsvg2-bin) and the CJK
# fonts referenced by the SVGs (e.g. fonts-noto-cjk and fonts-arphic-ukai).
#
set -euo pipefail

cd "$(dirname "$0")/.."

PIECES_DIR="webapp/src/main/resources/public/images/pieces"
SIZE="${1:-512}"

if ! command -v rsvg-convert >/dev/null 2>&1; then
    echo "error: rsvg-convert not found (install the librsvg2-bin package)" >&2
    exit 1
fi

shopt -s nullglob
converted=0
for svg in "${PIECES_DIR}"/*/svg/*.svg; do
    style_dir="$(dirname "$(dirname "${svg}")")"
    png="${style_dir}/$(basename "${svg}" .svg).png"
    rsvg-convert --width "${SIZE}" --height "${SIZE}" "${svg}" -o "${png}"
    echo "converted ${svg} -> ${png}"
    converted=$((converted + 1))
done

echo "done: ${converted} file(s) converted"
