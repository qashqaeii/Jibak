#!/usr/bin/env python3
"""Validate the twelve commissioned 3D WebP illustrations for Jibik.

These are generated raster illustrations, NOT CairoSVG/Python-drawn SVG icons.
Run: python scripts/verify_jibo_assets.py [asset_directory]
"""
import sys
from pathlib import Path
from PIL import Image

DEFAULT_DIR = Path("app/src/main/res/drawable-nodpi")
ARTWORKS = (
    "home", "transactions", "budget", "reports", "accounts", "goals",
    "obligations", "purchase", "categories", "jibo_ai", "empty", "reminders",
)
EXPECTED = {f"jibo_art_{name}.webp" for name in ARTWORKS}
SIZE = (384, 384)
MAX_FILE_BYTES = 70_000


def verify(directory: Path) -> None:
    found = {p.name for p in directory.glob("jibo_art_*")}
    missing, extra = EXPECTED - found, found - EXPECTED
    if missing or extra:
        raise AssertionError(
            f"Jibo assets mismatch: missing={sorted(missing)}, extra={sorted(extra)}"
        )
    total = 0
    for name in sorted(EXPECTED):
        path = directory / name
        with Image.open(path) as image:
            image.load()
            assert image.format == "WEBP", f"Not WebP: {name}"
            assert image.mode == "RGBA", f"No RGBA alpha: {name}"
            assert image.size == SIZE, f"Wrong dimensions: {name} {image.size}"
            alpha = image.getchannel("A")
            low, high = alpha.getextrema()
            assert low == 0 and high == 255, f"Missing transparency/solid art: {name}"
            w, h = image.size
            assert all(alpha.getpixel(p) == 0 for p in (
                (0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)
            )), f"Opaque corner/background: {name}"
            assert alpha.getbbox(), f"Empty artwork: {name}"
            assert path.stat().st_size < MAX_FILE_BYTES, f"Image too large: {name}"
        size = path.stat().st_size
        total += size
        print(f"OK {name}: {size:,} bytes")
    print(f"Verified {len(EXPECTED)} transparent 3D WebP illustrations: {total:,} bytes")


if __name__ == "__main__":
    verify(Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_DIR)
