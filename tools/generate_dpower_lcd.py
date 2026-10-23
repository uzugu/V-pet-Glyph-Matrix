#!/usr/bin/env python3
"""Regenerate the LCD map from upstream DPowerDigiviceEU101.svg, not artwork."""
import argparse
import json
from pathlib import Path
import re
import xml.etree.ElementTree as ET
parser = argparse.ArgumentParser()
parser.add_argument('svg', type=Path)
args = parser.parse_args()
cells = []
for element in ET.parse(args.svg).getroot().iter():
    identity = element.get('id', '')
    if not re.fullmatch(r'\d+_\d+', identity):
        continue
    path = element.get('d', '')
    assert re.sub(r'[0-9. -]', '', path) == 'MHVHVZ'
    n = [float(v) for v in re.findall(r'-?\d+(?:\.\d+)?', path)]
    address, bit = map(int, identity.split('_'))
    assert 0 <= bit < 8
    mask = 1 << bit
    cells.append(((n[0]+n[2])/2, (n[1]+n[3])/2, address, mask))
assert len(cells) == 512
x0, y0 = min(c[0] for c in cells), min(c[1] for c in cells)
grid = [[None]*32 for _ in range(16)]
for x, y, address, mask in cells:
    col, row = round((x-x0)/31.76), round((y-y0)/31.375)
    assert 0 <= col < 32 and 0 <= row < 16 and grid[row][col] is None
    assert 0 <= address < 128 and mask in (1,2,4,8,16,32,64,128)
    assert abs(x-x0-col*31.76) < 0.3 and abs(y-y0-row*31.375) < 0.3
    grid[row][col] = [address, mask]
assert all(all(cell is not None for cell in row) for row in grid)
(Path(__file__).resolve().parents[1]/'app/src/main/python/dpower_lcd.json').write_text(json.dumps(grid))
