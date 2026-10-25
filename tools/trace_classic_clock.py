#!/usr/bin/env python3
"""Trace private classic ROM clocks with the Frame vendored upstream Python core.

Outputs metadata, observed clock fields and read/write instruction addresses;
never copies firmware or full memory dumps into the report.
"""
import argparse
from collections import Counter
import hashlib
import importlib
import json
from pathlib import Path
import sys
import types


def trace(root, rom_dir, name, expected_hash, core_type):
    profile = json.loads((root / 'config/profiles' / (name + '.brick')).read_text())
    rom = rom_dir / (name + '.bin')
    digest = hashlib.sha256(rom.read_bytes()).hexdigest()
    if digest != expected_hash:
        raise ValueError('ROM identity mismatch for ' + name)
    mask = dict(profile['mask_options'], rom_path=str(rom))

    class Bus:
        def register_port_device(self, *args): pass
        def emit_port(self, *args): pass
        def emit_audio(self, *args): pass

    core = core_type(mask, profile['clock'], Bus())
    debt = 0.0
    reads, writes = Counter(), Counter()
    original_get, original_set = core.get_mem, core.set_mem

    def get_mem(address):
        if 0x1a <= address <= 0x1f:
            reads[(address, core.pc())] += 1
        return original_get(address)

    def set_mem(address, value):
        if 0x1a <= address <= 0x1f:
            writes[(address, core.pc())] += 1
        original_set(address, value)

    core.get_mem, core.set_mem = get_mem, set_mem

    def run(seconds):
        nonlocal debt
        debt += seconds * profile['clock']
        while debt > 0:
            debt -= core.clock()

    def press(key):
        core.port_handler('K0', key, 0)
        run(.2)
        core.port_handler('K0', key, -1)
        run(.3)

    def fields(): return list(core._RAM[0x1a:0x20])
    observations = []

    def observe(label, expected=None):
        actual = fields()
        if expected is not None and actual != expected:
            raise AssertionError((name, label, expected, actual))
        observations.append(dict(label=label, fields=actual, pc=core.pc(), halted=bool(core._HALT)))

    run(2)
    press(2)  # Middle button opens the initial clock setting screen.
    observe('initial clock setting', [0, 0, 0, 0, 0, 0])
    for hour in range(1, 25):
        press(4)
        h24 = hour % 24
        observe('set hour ' + str(h24), [0, 0, 0, 0, h24 % 12 or 12, int(h24 >= 12)])
    for minute in range(1, 61):
        press(2)
        m = minute % 60
        observe('set minute ' + str(m), [0, 0, m % 10, m // 10, 12, 0])
    # End at 11:59 PM using only buttons, then confirm and observe midnight.
    for _ in range(23): press(4)
    for _ in range(59): press(2)
    observe('set 23:59', [0, 0, 9, 5, 11, 1])
    press(1)
    observe('confirm 23:59', [0, 0, 9, 5, 11, 1])
    run(59)
    observe('23:59:59', [9, 5, 9, 5, 11, 1])
    run(1)
    observe('midnight rollover', [0, 0, 0, 0, 12, 0])
    run(1)
    observe('midnight plus one second', [1, 0, 0, 0, 12, 0])
    return dict(profile=name, sha256=digest, observations=observations,
        reads=[dict(address=a, pc=p, count=n) for (a,p),n in sorted(reads.items())],
        writes=[dict(address=a, pc=p, count=n) for (a,p),n in sorted(writes.items())])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--frame-root', type=Path, required=True)
    parser.add_argument('--rom-dir', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    package = types.ModuleType('clock_trace_cores')
    package.__path__ = [str(args.frame_root / 'backend/vendor/brickemupy')]
    sys.modules[package.__name__] = package
    core_type = importlib.import_module(package.__name__ + '.E0C6200').E0C6200
    hashes = json.loads((args.frame_root / 'config/rom-hashes.json').read_text())
    reports = []
    for digest, name in hashes.items():
        print('Tracing ' + name, flush=True)
        reports.append(trace(args.frame_root, args.rom_dir, name, digest, core_type))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(dict(schema=1, clock_fields_start=0x1a, reports=reports), indent=2) + '\n')
    print('Verified all six classic clock traces: ' + str(args.output))


if __name__ == '__main__':
    main()
