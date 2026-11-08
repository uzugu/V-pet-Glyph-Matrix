IO_AUDIO_CTRL_PWM_ENBL = 0x80
IO_AUDIO_CTRL_TONE = 0x40
   
class GPL191Xsound():
    def __init__(self, interconnect, clock):
        self._interconnect = interconnect
        self._envelope = [0, 0]
        self._toggle_state = [1, 1]
        self._main_clock = clock

    def set_data(self, channel, ctrl, data):
        if (ctrl & IO_AUDIO_CTRL_TONE):
            self._envelope[channel] = ((data & 0xFF) / 0xFF)
            if (data == 0):
                self._interconnect.emit_audio(channel, None)
        else:
            if (data != 0):
                amplitude = ((data & 0xFF) / 128) - 1
                self._interconnect.emit_audio(channel, (0, False, amplitude, 0))
            else:  
                self._interconnect.emit_audio(channel, None)

    def tone(self, channel, div):
        if (self._envelope[channel]):
            self._interconnect.emit_audio(channel, (self._main_clock / div, False, self._toggle_state[channel] * self._envelope[channel], 0))

    def stop(self, channel):
        self._interconnect.emit_audio(channel, None)