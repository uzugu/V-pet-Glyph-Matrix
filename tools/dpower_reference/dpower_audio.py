"""Two-channel PCM rendering from upstream audio events and emulated cycles."""
import math
import struct

class Mixer:
    RATE = 48000
    CLOCK = 2200000
    def __init__(self):
        self.channels = [None, None]
        self.phase = [0.0, 0.0]
        self.fraction = 0.0
        self.area = 0.0
        self.output = bytearray()

    def change(self, channel, value):
        if self.channels[channel] is None and value is not None:
            self.phase[channel] = 0.0
        self.channels[channel] = value

    def advance(self, cycles):
        remaining = cycles * self.RATE / self.CLOCK
        # Both channels silent: advance the sample clock without 48000 Python
        # mixing/packing iterations per second. Keep sample count and fractional
        # state identical so the next audible edge remains correctly aligned.
        if all(channel is None for channel in self.channels):
            if self.fraction > 0 and remaining > 0:
                width = min(remaining, 1.0 - self.fraction)
                self.fraction += width
                remaining -= width
                if self.fraction >= 1.0 - 1e-10:
                    sample = round(max(-1.0, min(1.0, self.area)) * 32767)
                    self.output.extend(struct.pack('<h', sample))
                    self.fraction = self.area = 0.0
            if remaining > 1e-10:
                whole = int(remaining + 1e-10)
                # Buffer is bounded even for long silent intervals.
                self.output.extend(b'\0' * (min(whole, 4800) * 2))
                self.fraction = max(0.0, remaining - whole)
                self.area = 0.0
            if len(self.output) > 9600: del self.output[:-9600]
            return
        while remaining > 1e-10:
            width = min(remaining, 1.0 - self.fraction)
            mixed = 0.0
            for i, channel in enumerate(self.channels):
                if channel is None: continue
                frequency, _, amplitude, _ = channel
                if frequency > 0:
                    delta = frequency * width / self.RATE
                    phase = self.phase[i]
                    # Match upstream audio_engine.py sine tones, integrating each
                    # sample interval so short changes retain their energy.
                    value = (math.cos(2 * math.pi * phase) - math.cos(2 * math.pi * (phase + delta))) / (2 * math.pi * delta)
                    self.phase[i] = (phase + delta) % 1.0
                else:
                    value = 1.0  # direct DAC sample, including its sign
                mixed += value * amplitude * .5
            self.area += mixed * width
            self.fraction += width
            remaining -= width
            if self.fraction >= 1.0 - 1e-10:
                sample = round(max(-1.0, min(1.0, self.area)) * 32767)
                self.output.extend(struct.pack('<h', sample))
                self.fraction = self.area = 0.0
        # Bound undrained playback data to 100 ms; never stall emulation.
        if len(self.output) > 9600: del self.output[:-9600]

    def drain(self):
        result = bytes(self.output)
        self.output.clear()
        return result

    def state(self):
        return {'phase': self.phase[:], 'fraction': self.fraction, 'area': self.area}

    def restore(self, state):
        if not isinstance(state, dict) or set(state) != {'phase', 'fraction', 'area'}:
            raise ValueError('Invalid PCM state')
        phase = state['phase']
        if not isinstance(phase, list) or len(phase) != 2:
            raise ValueError('Invalid PCM phase')
        values = phase + [state['fraction'], state['area']]
        if not all(isinstance(v, (int, float)) and math.isfinite(v) for v in values):
            raise ValueError('Invalid PCM value')
        if not all(0 <= p < 1 for p in phase) or not 0 <= state['fraction'] < 1 or abs(state['area']) > 1:
            raise ValueError('Invalid PCM range')
        self.phase = phase[:]
        self.fraction = state['fraction']
        self.area = state['area']
        self.output.clear()
