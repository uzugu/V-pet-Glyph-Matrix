# Japanese localization

## Plan

1. Move visible Android layout text and interactive UI labels into string resources.
2. Supply `values-ja` translations for setup, runtime controls, D-Power/NFC,
   quiet hours, saves, battle controls, widgets, and the field/evolution guides.
3. Select the bundled Japanese evolution database by the active resource locale;
   preserve English identifiers used for ROM detection, sprite lookup and saves.
4. Localize the Even companion using the browser language, including its native
   glasses menu. Preserve request fields and protocol values.
5. Verify resource parity and format arguments, Android build/unit tests/lint,
   and companion tests/build. Record device-only checks separately.

## Conventions

- Follow the system/app language; English is the fallback for other languages.
- Use 成長期, 成熟期 and 完全体 for Rookie, Champion and Ultimate respectively.
- Keep product names, ROM revisions, button letters and technical identifiers.
- Translate controls and explanations; raw RAM/register dumps and diagnostic
  protocol details retain their technical notation.
- Localization changes the app interface, not the language inside a ROM.

## Device review

Check Japanese on a phone at normal and enlarged font sizes: setup, confirmations,
quiet hours, D-Power/NFC, widgets, and expanded evolution cards. Check Even host
language detection and Japanese glyph support in the glasses' native input menu.

## Implemented

- 479 translated Android string/array values, including layout accessibility labels.
- Japanese Digimon names and stages in the live monitor and evolution cards.
  Requirements retain their numeric thresholds and alternative conditions.
  All 42 existing roster names were checked against official Bandai Japanese
  headings; see the [species-name audit](digimon-names.md) and its linked sources.
- Locale-specific dex selection with a cache per asset, so changing language
  cannot reuse the other language's data. Sprite lookup uses the original names.
- Android 13+ per-app language settings through `locales_config.xml`.
- System Japanese typography, natural CJK line spacing, locale-aware save dates,
  and content-sized action buttons that can grow when labels wrap.
- Even setup, connection states, gestures, errors, accessibility labels and native
  Step/Card menu labels follow the host's primary browser language. Unsupported
  languages fall back to English. Frame/command protocol values remain unchanged.
- The runtime card reads emulator state directly instead of comparing its label.

Low-level exception details and raw transport/RAM diagnostics can still contain
English. These are diagnostic output rather than translated game content.

## Verification (2026-10-29)

- `python3 tools/check_localization.py`: all 479 values pass resource parity,
  format-argument, array-length, evolution-number, layout-label and official-name
  glossary checks (42 species, including runtime/guide spelling aliases).
- Android: `assembleDebug testDebugUnitTest lintDebug` passed. 129 JVM tests:
  90 passed, 39 opt-in ROM/hardware cases skipped in this environment. Lint has
  warnings and no errors. Final resource/layout packaging was rechecked with
  `assembleDebug lintDebug`.
- All runtime Digimon names have presentation mappings; English and Japanese dex
  assets have matching version/species identities.
- Even: 10 tests passed, including Japanese host detection and translation without
  modifying the pairing key; TypeScript/Vite build and EHPK packaging passed.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- Companion: `even-client/vpet-stream.ehpk`.

ADB reported no connected devices. Phone layouts at large font sizes, actual
Japanese glyph rendering, language switching while running, widget refresh after
language changes, and the native glasses menu remain device-review checks.
