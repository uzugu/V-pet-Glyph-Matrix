#!/usr/bin/env python3
"""Write private CPU/audio golden traces outside Git; never writes a ROM.

DPOWER_ROM=/private/eu101.bin python3 tools/dump_dpower_trace.py --output /tmp/dpower.json
Use --synthetic for original ROM-free test code. --opcodes tests all 256 dispatch slots.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import sys
import time
import contextlib
sys.path.insert(0, str(Path(__file__).resolve().parent / 'dpower_reference'))
from dpower_runtime import Runtime, Bus
from brickemu.GPL191X import GPL191X


def synthetic():
    data = bytearray([0xea]) * 98304
    # Original test program: enable timers and LCD, drive DAC, then exercise RAM/flags.
    code = [0xa9, 1, 0x85, 0x1b, 0xa9, 0x80, 0x85, 0x0b,
            0xa9, 2, 0x85, 0x20, 0xa9, 180, 0x85, 0x0f,
            0xa2, 0, 0xe8, 0x86, 0x40, 0x8a, 0x69, 7, 0x85, 0x41,
            0x4c, 0x12, 0x04]
    data[0x400:0x400+len(code)] = bytes(code)
    for a in (0x7ffa,0x7ffc,0x7ffe): data[a:a+2] = bytes([0,4])
    return data


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--output', required=True)
    ap.add_argument('--rom', default=os.environ.get('DPOWER_ROM'))
    ap.add_argument('--synthetic', action='store_true')
    ap.add_argument('--opcodes', action='store_true')
    ap.add_argument('--mixer', action='store_true')
    ap.add_argument('--benchmark', action='store_true')
    ap.add_argument('--seconds', type=int, default=30)
    args = ap.parse_args()
    output = Path(args.output).resolve()
    repo = Path(__file__).resolve().parents[1]
    if output.is_relative_to(repo): ap.error('Trace output must be outside the repository')
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        path = args.rom
        if args.synthetic or args.opcodes:
            path = str(Path(d)/'synthetic.bin'); Path(path).write_bytes(synthetic())
        if args.mixer: path = 'unused'
        if not path: ap.error('Set DPOWER_ROM or pass --synthetic')
        def runtime():
            if not (args.synthetic or args.opcodes): return Runtime(path)
            r = Runtime.__new__(Runtime); r.path = path; r.bus = Bus()
            r.core = GPL191X({'rom_path':path,'port_pullup':{}},2200000,r.bus)
            return r
        start = time.process_time()
        if args.mixer:
            from dpower_audio import Mixer
            m=Mixer(); h=hashlib.sha256()
            for i in range(100):
                if i%7==0:m.change(0,(440+i,False,.7,0))
                if i%11==0:m.change(1,(771+i,False,-.4,0))
                if i%13==0:m.change(0,None)
                m.advance(2200.0+i*1.25)
                h.update(m.drain())
            result={'pcmSha256':h.hexdigest(),'pcm':m.state()}
        elif args.benchmark:
            r=runtime(); total=0.0
            for i in range(2000):
                r.advance()
                if i%10==0:r.drain_playback_pcm()
            start=time.process_time()
            for i in range(args.seconds*1000):
                total+=r.advance()
                if i%10==0:r.drain_playback_pcm()
            result={'cycles':total}
        elif args.opcodes:
            traces=[]
            with open(os.devnull, "w") as quiet, contextlib.redirect_stdout(quiet):
                for opcode in range(256):
                    for seed in range(4):
                        r=runtime(); c=r.core
                        c._mem[0x400:0x403]=bytes([opcode,0x40,0x04])
                        c._A=seed*67; c._X=seed*17; c._Y=seed*31; c._SP=0xf0
                        c._set_ps(seed*85)
                        cycles=c.clock();r.bus.advance_audio(cycles)
                        traces.append({'opcode':opcode,'seed':seed,'cycles':cycles,'state':json.loads(r.snapshot())})
            result={'opcodes':traces}
        else:
            r=runtime(); events=[]; total=0.0
            original=r.bus.emit_audio
            def event(channel,value):
                events.append([total,channel,value]);original(channel,value)
            r.bus.emit_audio=event
            # Reference's original advance_audio also records event timestamps exactly.
            advance_audio=r.bus.advance_audio
            def advance(cycles):
                nonlocal total
                total+=cycles;advance_audio(cycles)
            r.bus.advance_audio=advance
            pcm=hashlib.sha256();points=[]
            for ms in range(args.seconds*1000):
                # Three front keys, then repeated step contacts, in firmware order.
                if ms in (1000,3000,5000):r.pin('EF',{1000:0,3000:4,5000:1}[ms],1)
                if ms in (1100,3100,5100):r.pin('EF',{1100:0,3100:4,5100:1}[ms],0)
                if ms>=6000 and ms%500==0:r.pin('CD',1,1)
                if ms>=6000 and ms%500==80:r.pin('CD',1,-1)
                r.advance()
                if (ms+1)%10==0:pcm.update(r.drain_playback_pcm())
                if (ms+1)%1000==0:points.append({'ms':ms+1,'cycles':total,'state':json.loads(r.snapshot())})
            result={'checkpoints':points,'events':events,'pcmSha256':pcm.hexdigest(),'cycles':total}
        result['cpuSeconds']=time.process_time()-start
        output.write_text(json.dumps(result,separators=(',',':'),allow_nan=False))
        print(f'Trace written; CPU seconds: {result["cpuSeconds"]:.6f}',file=sys.stderr)

if __name__=='__main__':main()
