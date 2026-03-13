import json
import re
from pathlib import Path


SRC = Path("app/src/main/assets/evolution_dex.json")
DST = Path("app/src/main/assets/evolution_dex_en.json")


STAGE_MAP = {
    "幼年期Ⅰ": "Baby I",
    "幼年期Ⅱ": "Baby II",
    "成長期": "Rookie",
    "成熟期": "Champion",
    "完全体": "Ultimate",
}

RANK_MAP = str.maketrans({
    "Ａ": "A",
    "Ｂ": "B",
    "Ｃ": "C",
    "Ｄ": "D",
    "Ｅ": "E",
    "Ｆ": "F",
    "Ｓ": "S",
})

HAND_NAME_MAP = {
    "もんざえモン": "Monzaemon",
    "エアドラモン": "Airdramon",
    "シードラモン": "Seadramon",
    "メラモン": "Meramon",
    "アグモン": "Agumon",
    "ベタモン": "Betamon",
    "グレイモン": "Greymon",
    "ティラノモン": "Tyranomon",
    "デビモン": "Devimon",
    "ヌメモン": "Numemon",
    "エレキモン": "Elecmon",
    "ガブモン": "Gabumon",
    "ガルルモン": "Garurumon",
    "ユキダルモン": "Yukidarumon",
    "ホエーモン": "Whamon",
    "クネモン": "Kunemon",
    "パタモン": "Patamon",
    "ユニモン": "Unimon",
    "ケンタルモン": "Centarumon",
    "バードラモン": "Birdramon",
    "カブテリモン": "Kabuterimon",
    "エンジェモン": "Angemon",
    "オーガモン": "Ogremon",
    "バケモン": "Bakemon",
    "シェルモン": "Shellmon",
    "ドリモゲモン": "Drimogemon",
    "スカモン": "Sukamon",
    "ベジーモン": "Vegiemon",
    "アンドロモン": "Andromon",
    "エテモン": "Etemon",
    "ギロモン": "Giromon",
    "ベーダモン": "Vademon",
    "マメモン": "Mamemon",
    "メタルグレイモン": "MetalGreymon",
    "メタルマメモン": "MetalMamemon",
    "スカルグレイモン": "SkullGreymon",
    "コロモン": "Koromon",
    "ツノモン": "Tsunomon",
    "トコモン": "Tokomon",
    "ボタモン": "Botamon",
    "プニモン": "Punimon",
    "ポヨモン": "Poyomon",
}


def load():
    return json.loads(SRC.read_text(encoding="utf-8"))


def build_name_map(data):
    name_map = {}
    for entries in data.values():
        for entry in entries:
            name_map[entry["jpName"]] = entry["name"]
    name_map.update(HAND_NAME_MAP)
    return dict(sorted(name_map.items(), key=lambda item: len(item[0]), reverse=True))


def translate_names(text, name_map):
    translated = text
    for jp, en in name_map.items():
        translated = translated.replace(jp, en)
    return translated


def translate_header(raw, name_map):
    text = translate_names(raw, name_map)
    for jp, en in STAGE_MAP.items():
        text = text.replace(jp, en)
    text = text.replace("系", " line")
    text = text.replace("強さランク：", "Power Rank ")
    text = text.replace("（グー）", " (Rock)")
    text = text.replace("（チョキ）", " (Scissors)")
    text = text.replace("（パー）", " (Paper)")
    text = text.translate(RANK_MAP)
    return re.sub(r"\s+", " ", text).strip()


def translate_activity(raw):
    if raw == "不定":
        return "Irregular"
    match = re.fullmatch(r"AM(\d+)：(\d+)-PM(\d+)：(\d+)", raw)
    if match:
        sh, sm, eh, em = match.groups()
        return f"{int(sh)}:{sm} AM - {int(eh)}:{em} PM"
    return raw.replace("：", ":")


def translate_duration(raw, unit_jp, unit_en):
    if raw == "-":
        return "-"
    about = raw.startswith("約")
    core = raw[1:] if about else raw
    value = core.replace(unit_jp, "")
    suffix = unit_en[:-1] if value == "1" and unit_en.endswith("s") else unit_en
    prefix = "About " if about else ""
    return f"{prefix}{value} {suffix}".strip()


def translate_injury(raw):
    if raw == "-":
        return "-"
    match = re.fullmatch(r"(\d+/\d+)（(\d+)回）", raw)
    if match:
        rate, heals = match.groups()
        heal_label = "heal" if heals == "1" else "heals"
        return f"{rate} ({heals} {heal_label})"
    return raw


def translate_simple_clause(clause):
    for pattern, repl in (
        (r"トレーニング(\d+)回以上", r"Training \1+"),
        (r"トレーニング(\d+)回以下", r"Training \1 or less"),
        (r"トレーニング(\d+)～(\d+)回", r"Training \1-\2"),
        (r"満腹(\d+)回以上", r"Overfeeds \1+"),
        (r"満腹(\d+)回以下", r"Overfeeds \1 or less"),
        (r"睡眠妨害(\d+)回以上", r"Sleep disturbances \1+"),
        (r"睡眠妨害(\d+)回以下", r"Sleep disturbances \1 or less"),
    ):
        clause = re.sub(pattern, repl, clause)
    return clause


def translate_condition(raw, name_map):
    if "\n" in raw:
        return "\n".join(
            translate_condition(part.strip(), name_map) for part in raw.splitlines() if part.strip()
        )

    if raw == "デジタマから孵化":
        return "Hatches from DigiEgg"

    match = re.fullmatch(r"(.+)から約1時間経過", raw)
    if match:
        source = translate_names(match.group(1), name_map)
        return f"About 1 hour after {source}"

    match = re.fullmatch(r"(.+)から完全体条件を満たす", raw)
    if match:
        sources = translate_names(match.group(1), name_map).replace("、", ", ")
        return f"From {sources}: meets Ultimate battle requirement"

    parts = raw.split("、")
    translated_parts = []
    if parts:
        head = parts[0]
        match = re.fullmatch(r"(.+)から規則的", head)
        if match:
            source = translate_names(match.group(1), name_map)
            translated_parts.append(f"From {source}: low-mistake route")
        else:
            match = re.fullmatch(r"(.+)から不規則", head)
            if match:
                source = translate_names(match.group(1), name_map)
                translated_parts.append(f"From {source}: high-mistake route")
            else:
                translated_parts.append(translate_names(head, name_map))

    for part in parts[1:]:
        subparts = [translate_simple_clause(chunk.strip()) for chunk in part.split("or")]
        translated_parts.append(" or ".join(subparts))

    return "; ".join(translated_parts)


def translate_evolves(raw, name_map):
    text = translate_names(raw, name_map)
    parts = [part for part in text.split() if part]
    return ", ".join(parts)


def translate_entry(entry, name_map):
    return {
        **entry,
        "header": translate_header(entry["header"], name_map),
        "activity": translate_activity(entry["activity"]),
        "lifespan": translate_duration(entry["lifespan"], "時間", "hours"),
        "weight": entry["weight"],
        "fullness": entry["fullness"],
        "cycle": translate_duration(entry["cycle"], "分", "min"),
        "stamina": entry["stamina"],
        "injury": translate_injury(entry["injury"]),
        "conditionText": translate_condition(entry["conditionText"], name_map),
        "evolvesToText": [translate_evolves(value, name_map) for value in entry["evolvesToText"]],
    }


def main():
    data = load()
    name_map = build_name_map(data)
    translated = {
        version: [translate_entry(entry, name_map) for entry in entries]
        for version, entries in data.items()
    }
    DST.write_text(json.dumps(translated, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
