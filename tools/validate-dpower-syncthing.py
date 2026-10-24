#!/usr/bin/env python3
"""Host-side real D-Power runtime handoff over Syncthing; never touches working pet saves.
Start Frame's validate-syncthing-handoff.py first with a dedicated exchange subdirectory.
"""
import argparse
import importlib.util
import json
from pathlib import Path
import sys
import time

ROOT=Path(__file__).resolve().parents[1]

def run(args):
    sys.path.insert(0,str(ROOT/'app/src/main/python'))
    import dpower_runtime
    spec=importlib.util.spec_from_file_location('exchange',args.frame_checkout/'backend/save_exchange.py')
    e=importlib.util.module_from_spec(spec);spec.loader.exec_module(e)
    phone=dpower_runtime.Runtime(args.rom)
    for _ in range(250):phone.advance()
    state=dict(CORE='SPL61X',DPOWER_SNAPSHOT=phone.snapshot(),STEP_PENDING=0,STEP_PHASE=0,STEP_REMAINING=0)
    exchange=e.Exchange(args.exchange_dir,'phone_test')
    record=e.make(state,dpower_runtime.ROM_SHA256,'DPowerEU101','phone_test',None)
    incoming=exchange.publish(record)
    deadline=time.monotonic()+90
    response=None
    while response is None:
        if time.monotonic()>deadline:raise TimeoutError('No Frame response; inspect responder/Syncthing')
        response=next(((p,r) for p,r in exchange.records() if r['origin']=='frame_test' and r['parent']==record['id']),None)
        if response is None:time.sleep(.1)
    try:
        elapsed=0
        while elapsed<106000:elapsed+=phone.advance()
        returned=json.loads(response[1]['payload'])
        assert set(returned)==set(state)
        assert returned['CORE']=='SPL61X'
        assert all(returned[k]==0 for k in ('STEP_PENDING','STEP_PHASE','STEP_REMAINING'))
        resumed=dpower_runtime.Runtime(args.rom);resumed.restore(returned['DPOWER_SNAPSHOT'])
        def compare():
            a=json.loads(phone.snapshot());b=json.loads(resumed.snapshot())
            for key in ('fields','ram','sound','audio'):assert a[key]==b[key],key
        compare()
        for _ in range(100):assert phone.advance()==resumed.advance()
        compare()
        print('PASS D-Power phone runtime → Syncthing → Frame ARM import/execute/export → Syncthing → phone runtime; continuation matched')
    finally:
        incoming.unlink();response[0].unlink()

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exchange-dir',type=Path,required=True)
    parser.add_argument('--rom',type=Path,required=True)
    parser.add_argument('--frame-checkout',type=Path,default=ROOT.parent/'V-pet-frame')
    run(parser.parse_args())
