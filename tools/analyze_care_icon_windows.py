import re
from collections import Counter, defaultdict
from pathlib import Path


LOG_PATH = Path("reference/BrickEmuPy/e0c6200_watch.log")
EXCLUDED_TIMER_BYTES = {0x40, 0x46, 0x47, 0x58, 0x59, 0x5A, 0x5B, 0x5C}

LINE_RE = re.compile(
    r"^(?P<time>\d{2}:\d{2}:\d{2}) "
    r"PC=(?P<pc>[0-9A-F]+) "
    r"(?P<body>.*)$"
)

PAIR_RE = re.compile(r"([0-9A-F]{2})=([0-9A-F])")


def parse_records():
    text = LOG_PATH.read_text(encoding="utf-8", errors="replace").splitlines()
    last_session_idx = 0
    for i, line in enumerate(text):
        if line.startswith("=== Session "):
            last_session_idx = i
    session_lines = text[last_session_idx + 1 :]

    records = []
    for line in session_lines:
        m = LINE_RE.match(line.strip())
        if not m:
            continue
        body = m.group("body")
        icon_match = re.search(r"ICONS=([01]{8})", body)
        if not icon_match:
            continue
        icons = icon_match.group(1)
        care_on = icons[-1] == "1"

        ram = {}
        hot_match = re.search(r"HOT=([^ ]+)", body)
        if hot_match:
            for addr, value in PAIR_RE.findall(hot_match.group(1)):
                ram[int(addr, 16)] = int(value, 16)

        # Pull the always-present header bytes too.
        head = body.split(" HOT=")[0]
        for addr, value in PAIR_RE.findall(head):
            ram.setdefault(int(addr, 16), int(value, 16))

        records.append(
            {
                "time": m.group("time"),
                "pc": m.group("pc"),
                "care_on": care_on,
                "icons": icons,
                "ram": ram,
            }
        )
    return records


def find_clear_events(records):
    events = []
    for i in range(1, len(records)):
        prev_rec = records[i - 1]
        cur_rec = records[i]
        if prev_rec["care_on"] and not cur_rec["care_on"]:
            events.append((i - 1, i))
    return events


def find_miss_rollover_events(records):
    events = []
    for i in range(1, len(records)):
        prev_rec = records[i - 1]
        cur_rec = records[i]
        if not (prev_rec["care_on"] and cur_rec["care_on"]):
            continue
        prev_5a = prev_rec["ram"].get(0x5A)
        cur_5a = cur_rec["ram"].get(0x5A)
        if prev_5a is None or cur_5a is None:
            continue
        # Candidate miss moment: the slower alert byte wraps while care is active.
        if prev_5a >= 8 and cur_5a <= 1:
            events.append((i - 1, i))
    return events


def changed_bytes(prev_ram, cur_ram):
    out = {}
    for addr in sorted(set(prev_ram) | set(cur_ram)):
        old = prev_ram.get(addr)
        new = cur_ram.get(addr)
        if old != new:
            out[addr] = (old, new)
    return out


def persists(records, clear_idx, addr, new_value, lookahead=5):
    end = min(len(records), clear_idx + 1 + lookahead)
    if clear_idx + 1 >= end:
        return False
    for j in range(clear_idx + 1, end):
        if records[j]["ram"].get(addr) != new_value:
            return False
    return True


def rank_non_timer_candidates(records, events, label):
    freq = Counter()
    persistent_freq = Counter()
    examples = defaultdict(list)

    for prev_idx, cur_idx in events:
        prev_rec = records[prev_idx]
        cur_rec = records[cur_idx]
        diffs = changed_bytes(prev_rec["ram"], cur_rec["ram"])
        for addr, (old, new) in diffs.items():
            if addr in EXCLUDED_TIMER_BYTES:
                continue
            freq[addr] += 1
            if persists(records, cur_idx, addr, new):
                persistent_freq[addr] += 1
            examples[addr].append(f"{old:X}->{new:X}")

    print()
    print(f"Non-timer candidates at {label}:")
    if not freq:
        print("  none")
        return
    for addr, count in sorted(
        freq.items(),
        key=lambda item: (-persistent_freq[item[0]], -item[1], item[0]),
    ):
        persistent = persistent_freq[addr]
        sample = ", ".join(examples[addr][:5])
        print(
            f"  {addr:02X}: changed {count}x, persistent {persistent}x, "
            f"examples [{sample}]"
        )


def main():
    records = parse_records()
    if not records:
        print("No records with ICONS found in latest session.")
        return

    clear_events = find_clear_events(records)
    if not clear_events:
        print("No care-icon clear events found in latest session.")
        return

    freq = Counter()
    persistent_freq = Counter()
    value_examples = defaultdict(list)

    print(f"Latest session records: {len(records)}")
    print(f"Care clear events found: {len(clear_events)}")
    print()

    for event_no, (prev_idx, cur_idx) in enumerate(clear_events, start=1):
        prev_rec = records[prev_idx]
        cur_rec = records[cur_idx]
        diffs = changed_bytes(prev_rec["ram"], cur_rec["ram"])
        print(
            f"Event {event_no}: {prev_rec['time']} {prev_rec['icons']} -> "
            f"{cur_rec['time']} {cur_rec['icons']}"
        )
        if not diffs:
            print("  No RAM changes recorded in watched bytes.")
            continue
        for addr, (old, new) in sorted(diffs.items()):
            freq[addr] += 1
            keep = persists(records, cur_idx, addr, new)
            if keep:
                persistent_freq[addr] += 1
            value_examples[addr].append(f"{old:X}->{new:X}")
            note = " persistent" if keep else ""
            print(f"  {addr:02X}: {old:X}->{new:X}{note}")
        print()

    print("Ranked by change frequency at icon-clear:")
    for addr, count in freq.most_common():
        persistent = persistent_freq[addr]
        examples = ", ".join(value_examples[addr][:4])
        print(
            f"  {addr:02X}: changed {count}x, persistent {persistent}x, "
            f"examples [{examples}]"
        )
    rank_non_timer_candidates(records, clear_events, "icon clear")

    print()
    miss_events = find_miss_rollover_events(records)
    if not miss_events:
        print("No 5A-rollover miss candidates found in latest session.")
        return

    miss_freq = Counter()
    miss_persistent_freq = Counter()
    miss_examples = defaultdict(list)

    print(f"5A-rollover miss candidates found: {len(miss_events)}")
    print()
    for event_no, (prev_idx, cur_idx) in enumerate(miss_events, start=1):
        prev_rec = records[prev_idx]
        cur_rec = records[cur_idx]
        diffs = changed_bytes(prev_rec["ram"], cur_rec["ram"])
        print(
            f"Miss {event_no}: {prev_rec['time']} 5A={prev_rec['ram'].get(0x5A, -1):X} "
            f"-> {cur_rec['time']} 5A={cur_rec['ram'].get(0x5A, -1):X}"
        )
        for addr, (old, new) in sorted(diffs.items()):
            miss_freq[addr] += 1
            keep = persists(records, cur_idx, addr, new)
            if keep:
                miss_persistent_freq[addr] += 1
            miss_examples[addr].append(f"{old:X}->{new:X}")
            note = " persistent" if keep else ""
            print(f"  {addr:02X}: {old:X}->{new:X}{note}")
        print()

    print("Ranked by change frequency at 5A-rollover miss candidates:")
    for addr, count in miss_freq.most_common():
        persistent = miss_persistent_freq[addr]
        examples = ", ".join(miss_examples[addr][:4])
        print(
            f"  {addr:02X}: changed {count}x, persistent {persistent}x, "
            f"examples [{examples}]"
        )
    rank_non_timer_candidates(records, miss_events, "5A-rollover miss")

    print()
    print("Compact miss table (latest session):")
    print("  idx | time     | 5A->5A | 5B->5B | 5C->5C | inferred")
    for idx, (prev_idx, cur_idx) in enumerate(miss_events, start=1):
        prev_rec = records[prev_idx]
        cur_rec = records[cur_idx]
        prev_5a = prev_rec["ram"].get(0x5A)
        cur_5a = cur_rec["ram"].get(0x5A)
        prev_5b = prev_rec["ram"].get(0x5B)
        cur_5b = cur_rec["ram"].get(0x5B)
        prev_5c = prev_rec["ram"].get(0x5C)
        cur_5c = cur_rec["ram"].get(0x5C)
        inferred = None
        if cur_5b is not None and cur_5c is not None:
            inferred = cur_5c * 6 + cur_5b
        inferred_text = "-" if inferred is None else str(inferred)
        print(
            f"  {idx:>3} | {cur_rec['time']} | "
            f"{prev_5a:X}->{cur_5a:X}   | "
            f"{prev_5b:X}->{cur_5b:X}   | "
            f"{prev_5c:X}->{cur_5c:X}   | "
            f"{inferred_text}"
        )


if __name__ == "__main__":
    main()
