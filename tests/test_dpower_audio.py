import sys
import struct
import math
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'app/src/main/python'))
from dpower_audio import Mixer


def samples(mixer):
    data = mixer.drain()
    return struct.unpack('<' + 'h' * (len(data) // 2), data)

class AudioTests(unittest.TestCase):
    def test_upstream_dac_events_keep_changes_inside_delivery_batch(self):
        from dpower_runtime import Bus
        from brickemu.GPL191Xsound import GPL191Xsound
        bus = Bus(); sound = GPL191Xsound(bus, 2200000)
        sound.set_data(0, 0, 192)  # +0.5
        sound.set_data(1, 0, 96)   # -0.25
        bus.advance_audio(1100)
        sound.stop(0)
        bus.advance_audio(1100); bus.flush_audio()
        self.assertEqual((4096,) * 24 + (-4096,) * 24, samples(bus.mixer))

    def test_both_signed_dac_channels_are_mixed(self):
        m = Mixer()
        m.change(0, (0, False, .5, 0)); m.change(1, (0, False, -.25, 0))
        m.advance(2200)
        self.assertEqual(48, len(result := samples(m)))
        self.assertTrue(all(x == 4096 for x in result))

    def test_tone_frequency_and_stop_are_not_stretched(self):
        m = Mixer(); m.change(0, (1000, False, 1, 0))
        m.advance(22000)
        result = samples(m)
        self.assertEqual(480, len(result))
        self.assertEqual(19, sum((a > 0) != (b > 0) for a,b in zip(result, result[1:])))
        m.change(0, None); m.advance(2200)
        self.assertTrue(all(x == 0 for x in samples(m)))

    def test_tone_matches_upstream_sine_shape(self):
        m = Mixer(); m.change(0, (1000, False, 1, 0)); m.advance(2200)
        result = samples(m)
        for i, actual in enumerate(result):
            a, b = 2 * math.pi * i / 48, 2 * math.pi * (i + 1) / 48
            expected = round((math.cos(a) - math.cos(b)) / (b - a) * .5 * 32767)
            self.assertLessEqual(abs(actual - expected), 1)

    def test_quiet_volume_and_short_pulse_preserved(self):
        m = Mixer(); m.change(0, (0, False, .01, 0)); m.advance(1100)
        m.change(0, None); m.advance(1100)
        result = samples(m)
        self.assertEqual((164,) * 24 + (0,) * 24, result)

    def test_subsample_changes_are_integrated(self):
        m = Mixer(); m.change(0, (0, False, 1, 0)); m.advance(2200000 / 48000 / 2)
        m.change(0, (0, False, -1, 0)); m.advance(2200000 / 48000 / 2)
        self.assertEqual((0,), samples(m))

    def test_phase_continuation_and_invalid_state(self):
        a = Mixer(); a.change(0, (1234, False, .7, 0)); a.advance(12345); a.drain()
        b = Mixer(); b.channels = a.channels[:]; b.restore(a.state())
        a.advance(10000); b.advance(10000)
        self.assertEqual(a.drain(), b.drain())
        before = b.state()
        with self.assertRaises(ValueError): b.restore({'phase':[0, float('nan')], 'fraction':0, 'area':0})
        self.assertEqual(before, b.state())

    def test_bounded_output_and_fractional_sample_count(self):
        m = Mixer()
        for _ in range(1000): m.advance(2200)
        self.assertLessEqual(len(m.drain()), 9600)
        m = Mixer(); count = 0
        for _ in range(1000): m.advance(2200); count += len(m.drain()) // 2
        self.assertEqual(48000, count)

class IdleAudioTests(unittest.TestCase):
    def test_silent_fast_path_preserves_partial_audible_sample(self):
        mixer = Mixer()
        mixer.change(0, (0, False, 1, 0))
        mixer.advance(Mixer.CLOCK / Mixer.RATE / 2)
        mixer.change(0, None)
        mixer.advance(Mixer.CLOCK / Mixer.RATE * 2.5)
        self.assertEqual((8192, 0, 0), samples(mixer))
        self.assertEqual(0, mixer.fraction)

    def test_silent_clock_continuation(self):
        mixer = Mixer()
        for _ in range(1000): mixer.advance(2200); mixer.drain()
        self.assertEqual([0.0, 0.0], mixer.phase)
        self.assertAlmostEqual(0, mixer.fraction, places=7)
        mixer.change(0, (1000, False, 1, 0)); mixer.advance(2200)
        expected = Mixer(); expected.change(0, (1000, False, 1, 0)); expected.advance(2200)
        self.assertEqual(expected.drain(), mixer.drain())
