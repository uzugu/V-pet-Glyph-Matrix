#!/usr/bin/env python3
"""Validate Japanese resource coverage and translated formatting contracts."""
from collections import Counter
from pathlib import Path
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
FORMAT = re.compile(r"%(?:(\d+)\$)?[-#+ 0,(]*\d*(?:\.\d+)?([a-zA-Z%])")


def resources(folder):
    elements = list(ET.parse(RES / folder / "strings.xml").getroot())
    names = [element.attrib["name"] for element in elements]
    assert len(names) == len(set(names)), f"Duplicate resources in {folder}"
    return {element.attrib["name"]: element for element in elements}


def signature(text):
    return Counter((index, kind) for index, kind in FORMAT.findall(text) if kind != "%")


def main():
    english, japanese = resources("values"), resources("values-ja")
    assert english.keys() == japanese.keys(), (
        "Resource coverage differs", english.keys() ^ japanese.keys()
    )
    count = 0
    for name, original in english.items():
        translated = japanese[name]
        assert original.tag == translated.tag, name
        left = [original] if original.tag == "string" else list(original)
        right = [translated] if translated.tag == "string" else list(translated)
        assert len(left) == len(right), f"Array length differs: {name}"
        for en, ja in zip(left, right):
            a, b = en.text or "", ja.text or ""
            assert b.strip(), f"Empty Japanese value: {name}"
            if original.get("formatted") != "false":
                assert signature(a) == signature(b), f"Format arguments differ: {name}"
            if name.startswith("evolution_requirement_"):
                assert Counter(re.findall(r"\d+", a)) == Counter(re.findall(r"\d+", b)), name
            count += 1
    # Labels in layouts must resolve through resources, except machine identifiers.
    namespace = "{http://schemas.android.com/apk/res/android}"
    identifiers = {"—", "V1", "V2", "V3", "A + B", "A + C", "B + C"}
    for path in (RES / "layout").glob("*.xml"):
        for element in ET.parse(path).iter():
            for attr in ("text", "hint", "contentDescription"):
                value = element.get(namespace + attr, "")
                if value.startswith("@string/"):
                    assert value[8:] in english, (path.name, value)
                else:
                    assert not value or value.startswith("@") or value in identifiers, (
                        "Unlocalized layout label", path.name, value
                    )
    # Pin species names to the researched glossary, rather than accepting an
    # arbitrary Japanese transliteration just because it has the right key.
    glossary = json.loads((ROOT / "docs/localization/digimon-names.json").read_text())
    species = glossary["species"]
    expected = {(entry["version"], entry["englishId"]): entry for entry in species}
    assert len(expected) == len(species), "Duplicate glossary species"
    presentation = (ROOT / "app/src/main/java/com/digimon/glyph/LocalizedText.kt").read_text()
    labels = {name.lower(): resource for name, resource in re.findall(
        r'"([^"]+)" to R.string.(\w+)', presentation
    )}
    for asset in ("evolution_dex.json", "evolution_dex_en.json"):
        dex = json.loads((ROOT / "app/src/main/assets" / asset).read_text())
        identities = {(version, entry["name"]) for version, entries in dex.items() for entry in entries}
        assert identities == expected.keys(), f"Glossary coverage differs: {asset}"
        for version, entries in dex.items():
            for entry in entries:
                name = expected[version, entry["name"]]
                assert entry["jpName"] == name["japanese"], (asset, entry["name"])
                assert japanese[name["resource"]].text == name["japanese"], entry["name"]
                assert labels[entry["name"].lower()] == name["resource"], entry["name"]
    assert labels["vegimon"] == labels["vegiemon"], "Runtime/guide spelling alias differs"
    for name, resource in (("Rookie", "stage_rookie"), ("Champion", "stage_champion"),
                           ("Ultimate", "stage_ultimate")):
        assert labels[name.lower()] == resource, name
    assert japanese["stage_ultimate"].text == "完全体", "English Ultimate is Japanese Perfect, not Mega"
    print(f"Japanese localization: {count} values and {len(species)} verified species; "
          "coverage, placeholders, arrays, route numbers, layouts and names passed")


if __name__ == "__main__":
    main()
