#!/usr/bin/env python3
"""Fetch the unmodified official SDK pinned in docs/glyph/sdk.md."""
import hashlib
from pathlib import Path
from urllib.request import urlopen

COMMIT = "999b1143a6bff0dd79db11d21b92c15c56a346b1"
SHA256 = "be00ee9cd7115f6b11984c6e31fe98e298fb726940d1555063610685ef3bbf29"
URL = f"https://raw.githubusercontent.com/Nothing-Developer-Programme/GlyphMatrix-Developer-Kit/{COMMIT}/glyph-matrix-sdk-2.0.aar"
if __name__ == "__main__":
    target = Path(__file__).resolve().parents[1] / "app/libs/glyph-matrix-sdk-2.0.aar"
    with urlopen(URL, timeout=60) as response:
        data = response.read()
    if hashlib.sha256(data).hexdigest() != SHA256:
        raise SystemExit("SDK checksum mismatch; file not installed")
    target.parent.mkdir(parents=True, exist_ok=True)
    staging = target.with_suffix(".download")
    staging.write_bytes(data)
    staging.replace(target)
    print(f"Verified SDK 2.0: {target}")
