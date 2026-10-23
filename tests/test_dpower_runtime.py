"""Private-ROM integration: DPOWER_ROM must point to the user's EU 1.01 dump."""
import json
import os
from pathlib import Path
import sys
import unittest
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
from dpower_runtime import Runtime, LCD
from brickemu.GPL191X import GPL191X

ROM = os.environ.get('DPOWER_ROM')

@unittest.skipUnless(ROM, 'Set DPOWER_ROM to the private EU 1.01 dump')
class DPowerTests(unittest.TestCase):
    def test_batched_core_matches_upstream_and_lcd(self):
        adapted = Runtime(ROM)
        reference = Runtime(ROM)
        cycles = 0
        while cycles < 2_200_000 * 3:
            cycles += adapted.advance()
        ref_cycles = 0
        while ref_cycles < cycles:
            elapsed = reference.core.clock()
            reference.bus.advance_audio(elapsed)
            ref_cycles += elapsed
        self.assertEqual(cycles, ref_cycles)
        self.assertEqual(adapted.snapshot(), reference.snapshot())
        self.assertEqual(512, len(adapted.display()))
        self.assertTrue(any(adapted.display()))
        self.assertEqual(512, len(set((a,m) for row in LCD for a,m in row)))
        raw = adapted.core.get_VRAM()
        self.assertEqual(adapted.display(), [int(bool(raw[a] & m)) for row in LCD for a,m in row])

    def test_all_inputs_and_restore_continuation(self):
        original = Runtime(ROM)
        for _ in range(2000): original.advance()
        for port,pin in [('EF',0),('EF',1),('EF',2),('EF',4),('CD',1),('CD',2)]:
            original.pin(port,pin,1)
            for _ in range(50): original.advance()
            self.assertTrue(original.core._port_input[port][1] & (1 << pin))
            original.pin(port,pin,-1)
            self.assertFalse(original.core._port_input[port][1] & (1 << pin))
        restored = Runtime(ROM)
        restored.restore(original.snapshot())
        self.assertEqual(original.snapshot(), restored.snapshot())
        for _ in range(2000):
            self.assertEqual(original.advance(), restored.advance())
        self.assertEqual(original.snapshot(), restored.snapshot())
        self.assertEqual(original.display(), restored.display())

    def test_pcm_restore_and_legacy_snapshot(self):
        original = Runtime(ROM)
        for _ in range(300): original.advance()
        original.drain_pcm()
        saved = original.snapshot()
        restored = Runtime(ROM); restored.restore(saved)
        original.drain_pcm()
        for _ in range(30):
            original.advance(); restored.advance()
            self.assertEqual(original.drain_pcm(), restored.drain_pcm())
        legacy = json.loads(saved); legacy['schema'] = 1; del legacy['pcm']
        restored.restore(json.dumps(legacy))
        self.assertEqual(json.loads(saved)['fields'], json.loads(restored.snapshot())['fields'])
        invalid = json.loads(saved); invalid['pcm']['phase'][0] = -1
        before = restored.snapshot()
        with self.assertRaises(ValueError): restored.restore(json.dumps(invalid))
        self.assertEqual(before, restored.snapshot())

    def test_every_display_segment_uses_one_bit(self):
        runtime = Runtime(ROM)
        # Upstream SVG IDs encode bit INDICES; e.g. 0_1 means mask 2, not mask 1.
        self.assertEqual([0, 1], LCD[0][0])
        self.assertEqual([0, 2], LCD[0][1])
        original_vram = runtime.core.get_VRAM
        for address, mask in (cell for row in LCD for cell in row):
            self.assertIn(mask, (1,2,4,8,16,32,64,128))
            raw = [0] * 128
            raw[address] = mask
            runtime.core.get_VRAM = lambda: raw
            self.assertEqual(1, sum(runtime.display()))
        runtime.core.get_VRAM = original_vram

    def test_invalid_restore_is_atomic(self):
        runtime = Runtime(ROM)
        for _ in range(100): runtime.advance()
        before = runtime.snapshot()
        state = json.loads(before)
        state['fields']['_PC'] = 1234
        state['ram'] = 'AA=='
        with self.assertRaises(ValueError): runtime.restore(json.dumps(state))
        self.assertEqual(before, runtime.snapshot())
        state = json.loads(before)
        state['fields']['_TIMER_CTRL'] = 128
        state['fields']['_TM1_div'] = 0
        with self.assertRaises(ValueError): runtime.restore(json.dumps(state))
        self.assertEqual(before, runtime.snapshot())
        state = json.loads(before); state['rom'] = 'other'
        with self.assertRaises(ValueError): runtime.restore(json.dumps(state))
        self.assertEqual(before, runtime.snapshot())

if __name__ == '__main__': unittest.main()
