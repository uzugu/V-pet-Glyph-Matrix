# Japanese species-name audit

Verified on 2026-10-29 against the **Japanese Bandai Digimon Encyclopedia**.
Each link below was fetched and its species heading checked; the glossary in
[digimon-names.json](digimon-names.json) is enforced by
`python3 tools/check_localization.py`.

The left column is this project's existing English identity, including legacy
spellings, rather than a recommendation to rename emulator or sprite keys.
The right column is the Japanese display name. This covers the current V1–V3
metadata/guide roster (42 species); it does not add decoded V4/D-Power metadata.

Important distinctions:

- Frigimon is **ユキダルモン**, not a transliteration of “Frigimon”.
- Centarumon is **ケンタルモン**; Whamon is **ホエーモン**.
- Monzaemon uses the exact mixed-script spelling **もんざえモン**.
- The runtime's `Vegimon` and the guide's `Vegiemon` both display **ベジーモン**.
- Classic Greymon is `greymon-first`. The bare `greymon` reference identifies
  the 2010 anime variant.
- Classic V1 MetalGreymon is the virus-species entry `metalgreymon-web`, whose
  official heading is **メタルグレイモン（ウィルス種）**. The interface retains the
  classic toy's short name **メタルグレイモン**. The bare `metalgreymon` reference is
  the 2010 anime variant; the vaccine-species entry is a different reference.
- English stage labels are localized by meaning: Rookie → 成長期,
  Champion → 成熟期, Ultimate → 完全体. English “Ultimate” here must not become
  Japanese 究極体 (the English Mega stage).

The browser search service intermittently returned maintenance pages. Direct
reads of the official site succeeded; its public search endpoint resolved the
non-obvious directory names before the corresponding detail headings were checked.
No descriptions or illustrations were copied from those pages.

| Version | Existing English identity | Japanese display name / official reference |
| --- | --- | --- |
| V1 | Botamon | [ボタモン](https://digimon.net/reference/detail.php?directory_name=botamon) |
| V1 | Koromon | [コロモン](https://digimon.net/reference/detail.php?directory_name=koromon) |
| V1 | Agumon | [アグモン](https://digimon.net/reference/detail.php?directory_name=agumon) |
| V1 | Betamon | [ベタモン](https://digimon.net/reference/detail.php?directory_name=betamon) |
| V1 | Greymon | [グレイモン](https://digimon.net/reference/detail.php?directory_name=greymon-first) |
| V1 | Tyranomon | [ティラノモン](https://digimon.net/reference/detail.php?directory_name=tyranomon) |
| V1 | Devimon | [デビモン](https://digimon.net/reference/detail.php?directory_name=devimon) |
| V1 | Meramon | [メラモン](https://digimon.net/reference/detail.php?directory_name=meramon) |
| V1 | Airdramon | [エアドラモン](https://digimon.net/reference/detail.php?directory_name=airdramon) |
| V1 | Seadramon | [シードラモン](https://digimon.net/reference/detail.php?directory_name=seadramon) |
| V1 | Numemon | [ヌメモン](https://digimon.net/reference/detail.php?directory_name=numemon) |
| V1 | MetalGreymon | [メタルグレイモン](https://digimon.net/reference/detail.php?directory_name=metalgreymon-web) |
| V1 | Mamemon | [マメモン](https://digimon.net/reference/detail.php?directory_name=mamemon) |
| V1 | Monzaemon | [もんざえモン](https://digimon.net/reference/detail.php?directory_name=monzaemon) |
| V2 | Punimon | [プニモン](https://digimon.net/reference/detail.php?directory_name=punimon) |
| V2 | Tsunomon | [ツノモン](https://digimon.net/reference/detail.php?directory_name=tunomon) |
| V2 | Gabumon | [ガブモン](https://digimon.net/reference/detail.php?directory_name=gabumon) |
| V2 | Elecmon | [エレキモン](https://digimon.net/reference/detail.php?directory_name=elecmon) |
| V2 | Kabuterimon | [カブテリモン](https://digimon.net/reference/detail.php?directory_name=kabuterimon) |
| V2 | Garurumon | [ガルルモン](https://digimon.net/reference/detail.php?directory_name=garurumon) |
| V2 | Angemon | [エンジェモン](https://digimon.net/reference/detail.php?directory_name=angemon) |
| V2 | Frigimon | [ユキダルモン](https://digimon.net/reference/detail.php?directory_name=yukidarumon) |
| V2 | Birdramon | [バードラモン](https://digimon.net/reference/detail.php?directory_name=birdramon) |
| V2 | Whamon | [ホエーモン](https://digimon.net/reference/detail.php?directory_name=whamon) |
| V2 | Vegiemon | [ベジーモン](https://digimon.net/reference/detail.php?directory_name=vegimon) |
| V2 | SkullGreymon | [スカルグレイモン](https://digimon.net/reference/detail.php?directory_name=skullgreymon) |
| V2 | MetalMamemon | [メタルマメモン](https://digimon.net/reference/detail.php?directory_name=metalmamemon) |
| V2 | Vademon | [ベーダモン](https://digimon.net/reference/detail.php?directory_name=vademon) |
| V3 | Poyomon | [ポヨモン](https://digimon.net/reference/detail.php?directory_name=poyomon) |
| V3 | Tokomon | [トコモン](https://digimon.net/reference/detail.php?directory_name=tokomon) |
| V3 | Patamon | [パタモン](https://digimon.net/reference/detail.php?directory_name=patamon) |
| V3 | Kunemon | [クネモン](https://digimon.net/reference/detail.php?directory_name=kunemon) |
| V3 | Unimon | [ユニモン](https://digimon.net/reference/detail.php?directory_name=yunimon) |
| V3 | Centarumon | [ケンタルモン](https://digimon.net/reference/detail.php?directory_name=centalmon) |
| V3 | Ogremon | [オーガモン](https://digimon.net/reference/detail.php?directory_name=orgemon) |
| V3 | Bakemon | [バケモン](https://digimon.net/reference/detail.php?directory_name=bakemon) |
| V3 | Shellmon | [シェルモン](https://digimon.net/reference/detail.php?directory_name=shellmon) |
| V3 | Drimogemon | [ドリモゲモン](https://digimon.net/reference/detail.php?directory_name=drimogemon) |
| V3 | Scumon | [スカモン](https://digimon.net/reference/detail.php?directory_name=scumon) |
| V3 | Andromon | [アンドロモン](https://digimon.net/reference/detail.php?directory_name=andromon) |
| V3 | Giromon | [ギロモン](https://digimon.net/reference/detail.php?directory_name=giromon) |
| V3 | Etemon | [エテモン](https://digimon.net/reference/detail.php?directory_name=etemon) |
