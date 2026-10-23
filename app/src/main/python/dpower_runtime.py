"""Android adapter for pinned BrickEmuPy GPL191X. No firmware is bundled."""
import base64
import hashlib
import json
import math
from pathlib import Path
from brickemu.GPL191X import GPL191X

ROM_SHA256 = 'f1f0a92bdcbd87514b7c981ca126713ee7e050ec262fcbfe90e75d2e1bc852ca'
LCD = json.loads(Path(__file__).with_name('dpower_lcd.json').read_text())

class Bus:
    def __init__(self):
        self.audio = [None, None]
    def register_port_device(self, device):
        pass
    def emit_port(self, *args):
        pass  # Link support is deliberately unavailable for this device.
    def emit_audio(self, channel, value):
        self.audio[channel] = value

class Runtime:
    def __init__(self, rom_path):
        self.path = str(rom_path)
        data = Path(self.path).read_bytes()
        if len(data) != 98304 or hashlib.sha256(data).hexdigest() != ROM_SHA256:
            raise ValueError('Unrecognised D-Power ROM; expected EU 1.01')
        self.bus = Bus()
        self.core = GPL191X({'rom_path': self.path, 'port_pullup': {}}, 2200000, self.bus)

    def advance(self):
        # At most roughly 1 ms of emulated time per JNI/Python boundary.
        cycles = 0.0
        while cycles < 2200:
            cycles += self.core.clock()
        return cycles

    def display(self):
        raw = self.core.get_VRAM()
        return [int(bool(raw[a] & mask)) for row in LCD for a, mask in row]

    def ram(self):
        return list(self.core._mem[0x40:0x300])

    def raw_lcd(self):
        return list(self.core.get_VRAM())

    def pin(self, port, pin, level):
        if port not in ('EF', 'CD', 'RES') or not 0 <= pin <= 7 or level not in (-1, 0, 1):
            raise ValueError('Invalid device input')
        self.core.port_handler(port, 1 << pin, level)

    def reset(self):
        replacement = Runtime(self.path)
        self.core, self.bus = replacement.core, replacement.bus

    def audio(self):
        # Existing Android buzzer is a tone synthesizer, not a two-channel PCM mixer.
        tones = [v for v in self.bus.audio if v and v[0] > 0]
        if not tones:
            return [0.0, 0.0]
        tone = max(tones, key=lambda v: abs(v[2]))
        return [float(tone[0]), abs(float(tone[2]))]

    def _fields(self):
        return {k: v for k, v in vars(self.core).items()
                if isinstance(v, (int, float, dict, list)) and k != '_sfr_tbl'}

    def snapshot(self):
        return json.dumps({'schema': 1, 'rom': ROM_SHA256,
            'fields': self._fields(),
            'ram': base64.b64encode(self.core._mem[0x40:0x380]).decode('ascii'),
            'sound': {'envelope': self.core._sound._envelope,
                      'toggle': self.core._sound._toggle_state},
            'audio': self.bus.audio}, allow_nan=False, separators=(',', ':'))

    def restore(self, payload):
        if len(payload) > 32768:
            raise ValueError('Snapshot too large')
        state = json.loads(payload)
        if set(state) != {'schema','rom','fields','ram','sound','audio'} or state['schema'] != 1 or state['rom'] != ROM_SHA256:
            raise ValueError('Incompatible snapshot')
        fresh = Runtime(self.path)
        # Validate the entire structure against this pinned core before mutation.
        def check(value, template):
            if isinstance(template, dict):
                if not isinstance(value, dict) or value.keys() != template.keys():
                    raise ValueError('Invalid snapshot fields')
                for k in template: check(value[k], template[k])
            elif isinstance(template, list):
                if not isinstance(value, list) or len(value) != len(template):
                    raise ValueError('Invalid snapshot array')
                for v, t in zip(value, template): check(v, t)
            elif isinstance(template, (int, float)):
                if not isinstance(value, (int,float)) or not math.isfinite(value) or abs(value) > 2**53:
                    raise ValueError('Invalid snapshot number')
                if isinstance(template, int) and not isinstance(value, int):
                    raise ValueError('Integer register required')
            else:
                raise ValueError('Unexpected snapshot type')
        template = fresh._fields()
        for key in template:
            if key.endswith(('_counter', '_div')):
                template[key] = float(template[key])
        check(state['fields'], template)
        for key in ('_clock', '_sub_clock_div', '_mem_size', '_pullup_ext'):
            if state['fields'][key] != fresh._fields()[key]:
                raise ValueError('Snapshot changes device configuration')
        fields = state['fields']
        if fields['_CPU_DIV'] not in (2, 4, 8, 16):
            raise ValueError('Invalid CPU divisor')
        if fields['_TM0_div'] < 0 or fields['_TM1_div'] < 0 or (fields['_TIMER_CTRL'] & 0x80 and fields['_TM1_div'] <= 0):
            raise ValueError('Invalid timer divisor')
        for key, limit in {'_PC': 65535, '_A': 255, '_X': 255, '_Y': 255, '_SP': 255, '_BANK': 3}.items():
            if not 0 <= fields[key] <= limit:
                raise ValueError('Invalid register range')
        check(state['sound'], {'envelope': [0.0,0.0], 'toggle': [1,1]})
        ram = base64.b64decode(state['ram'], validate=True)
        if len(ram) != 0x340:
            raise ValueError('Invalid RAM length')
        audio = state['audio']
        if not isinstance(audio, list) or len(audio) != 2:
            raise ValueError('Invalid audio state')
        for tone in audio:
            if tone is not None:
                if not isinstance(tone, list) or len(tone) != 4 or not all(isinstance(v,(int,float)) and math.isfinite(v) for v in tone):
                    raise ValueError('Invalid audio channel')
        fresh.core.__dict__.update(state['fields'])
        fresh.core._mem[0x40:0x380] = ram
        fresh.core._sound._envelope = state['sound']['envelope']
        fresh.core._sound._toggle_state = state['sound']['toggle']
        fresh.bus.audio = audio
        self.core, self.bus = fresh.core, fresh.bus
