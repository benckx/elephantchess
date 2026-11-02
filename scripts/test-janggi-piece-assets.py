import io
import shutil
import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw


PIECES = Path(__file__).resolve().parents[1] / "webapp/src/main/resources/public/images/pieces"
STYLE = PIECES / "janggi_westernized"
NAMESPACE = {"svg": "http://www.w3.org/2000/svg"}
COLORS = {"red": (192, 57, 43), "blue": (26, 74, 134)}
NAMES = ("general", "advisor", "elephant", "horse", "chariot", "cannon", "soldier")


def render(svg, size):
    if shutil.which("rsvg-convert"):
        data = subprocess.run(
            ["rsvg-convert", "--width", str(size), "--height", str(size)],
            input=svg.read_bytes(), capture_output=True, check=True,
        ).stdout
    else:
        from cairosvg import svg2png
        data = svg2png(url=str(svg), output_width=size, output_height=size)
    return Image.open(io.BytesIO(data)).convert("RGBA")


def glyph_mask(image, color):
    mask = Image.new("L", image.size)
    mask.putdata([
        255 if alpha > 128 and all(abs(c - expected) < 4 for c, expected in zip(rgb, color)) else 0
        for *rgb, alpha in (
            image.getpixel((x, y))
            for y in range(image.height)
            for x in range(image.width)
        )
    ])
    return mask


class WesternizedPieceAssetsTest(unittest.TestCase):
    def test_vector_and_png_match_without_corner_artifact(self):
        for color in COLORS:
            for name in NAMES:
                with self.subTest(color=color, piece=name):
                    svg = STYLE / "svg" / f"{color}_{name}.svg"
                    root = ET.parse(svg).getroot()
                    self.assertEqual(root.get("viewBox"), "0 0 100 100")
                    self.assertIsNone(root.find(".//svg:image", NAMESPACE))
                    polygons = root.findall(".//svg:polygon", NAMESPACE)
                    self.assertEqual(len(polygons), 1)
                    self.assertEqual(polygons[0].get("fill"), "#fffaf0")
                    with Image.open(STYLE / f"{color}_{name}.png") as png:
                        png = png.convert("RGBA")
                        self.assertEqual(png.size, (512, 512))
                        self.assertIsNone(ImageChops.difference(png, render(svg, 512)).getbbox())
                        self.assertEqual(png.getpixel((10, 10))[3], 0)
                        self.assertEqual(png.getpixel((256, 41)), (255, 250, 240, 255))
                        self.assertEqual(png.getpixel((41, 256)), (255, 250, 240, 255))

    def test_pictograms_are_large_centered_and_inside_octagon(self):
        for color, rgb in COLORS.items():
            for name in NAMES:
                with self.subTest(color=color, piece=name):
                    with Image.open(STYLE / f"{color}_{name}.png") as png:
                        mask = glyph_mask(png.convert("RGBA"), rgb)
                    left, top, right, bottom = mask.getbbox()
                    self.assertLess(abs((left + right) / 2 - 256), 3)
                    self.assertLess(abs((top + bottom) / 2 - 256), 3)
                    self.assertGreater(max(right - left, bottom - top), 340)
                    self.assertGreater(right - left + bottom - top, 580)
                    for y in range(512):
                        for x in range(512):
                            if mask.getpixel((x, y)):
                                self.assertLess(abs(x - 256) + abs(y - 256), 308)

    def test_piece_names_keep_original_pictograms(self):
        for name in NAMES:
            with self.subTest(piece=name):
                with Image.open(PIECES / "romanized_rounded" / f"red_{name}.png") as source:
                    source = source.convert("RGBA")
                    original = glyph_mask(source, (173, 0, 0))
                # Exclude the original circular rim, not any pictogram details.
                interior = Image.new("L", original.size)
                width, height = interior.size
                ImageDraw.Draw(interior).ellipse(
                    (width * .07, height * .07, width * .93, height * .93), fill=255,
                )
                original = ImageChops.multiply(original, interior)
                with Image.open(STYLE / f"red_{name}.png") as png:
                    generated = glyph_mask(png.convert("RGBA"), COLORS["red"])
                original = original.crop(original.getbbox()).resize((128, 128))
                generated = generated.crop(generated.getbbox()).resize((128, 128))
                difference = ImageChops.difference(original, generated)
                mismatch = sum(value * count for value, count in enumerate(difference.histogram()))
                mismatch /= 255 * 128 * 128
                self.assertLess(mismatch, .06)


if __name__ == "__main__":
    unittest.main()
