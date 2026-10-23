# NanoFont 3x4 clock

Font: [Michaelangel007/nanofont3x4](https://github.com/Michaelangel007/nanofont3x4), pinned to `dbd373a3bd0f2f8386273de931c05cba2b290daa`. Digit source: `nanofont3x4.cpp`, ASCII 0x30–0x39. [Original license](nanofont/LICENSE) retained verbatim.

The digit patterns already present in the sibling D-Arc app and this app match these upstream glyphs exactly. Digits have 3×3 ink in a 3×4 cell; they are not 3×2. The earlier font identification was incomplete.

Preview 6 renders each lit font cell as exactly one white bitmap pixel, removing 1.2-pixel-radius antialiased circles. Four digits plus one-column gaps occupy 15 columns, x=5..19. Ink is placed at y=20..22, below the native LCD at y=4..19. The spare lower margin accommodates the clock inside the circular Matrix; a centred 15-wide font at y=0 would encounter the physical top-edge mask.

Tests compare all ten digits with the canonical upstream hex patterns and check every HHMM combination stays below the LCD and inside the nominal 25×25 circular footprint. Physical LED readability remains subject to phone testing.
