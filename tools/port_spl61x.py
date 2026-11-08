"""Reproduce the explicit Kotlin instruction/SFR port from the pinned Python reference.

The generated hot path uses primitive fields and direct dispatch. Hardware policy,
PCM and portable snapshot validation live in separately maintained Kotlin files.
"""
import ast
from pathlib import Path
root = Path(__file__).resolve().parents[1]
source = root / 'tools/dpower_reference/brickemu/GPL191X.py'
tree = ast.parse(source.read_text())
cls = next(x for x in tree.body if isinstance(x, ast.ClassDef))
methods = {x.name: x for x in cls.body if isinstance(x, ast.FunctionDef)}
constants = {}
for x in tree.body:
 if isinstance(x, ast.Assign):
  exec(compile(ast.Module([x], []), '', 'exec'), {}, constants)
init = methods['__init__']
execute = next(x.value for x in init.body if isinstance(x, ast.Assign) and any(isinstance(t, ast.Attribute) and t.attr == '_execute' for t in x.targets))
sfr = next(x.value for x in init.body if isinstance(x, ast.Assign) and any(isinstance(t, ast.Attribute) and t.attr == '_sfr_tbl' for t in x.targets))
def entries(x):
 if isinstance(x, ast.Attribute): return [x.attr]
 if isinstance(x, (ast.Tuple,ast.List)): return sum((entries(y) for y in x.elts), [])
 if isinstance(x, ast.Starred): return entries(x.value)
 if isinstance(x, ast.BinOp) and isinstance(x.op, ast.Mult): return entries(x.left)*ast.literal_eval(x.right)
 raise ValueError(ast.dump(x))
ops=entries(execute)
assert len(ops)==256
skip={'__init__','_open_rom','examine','edit_state','clock','_read_mem','_write_mem','get_VRAM','get_ROM','pc','istr_counter','_dummy'}
selected=[m for n,m in methods.items() if n not in skip]
arrays={'_PDIR','_PLATCH','_pullup_ext','_port_input','_mem'}
fields=set()
for m in selected:
 for x in ast.walk(m):
  if isinstance(x,ast.Attribute) and isinstance(x.value,ast.Name) and x.value.id=='self' and isinstance(x.ctx,ast.Store): fields.add(x.attr)
fields|={'_clock','_sub_clock_div','_instr_counter','_mem_size'}
ft={f:('Double' if (f.endswith(('_counter','_div')) and f!='_instr_counter') else 'Double' if f=='_instr_counter' else 'Int') for f in fields-arrays}
ft['_sub_clock_div']='Double'
returns={m.name:('Int' if any(isinstance(x,ast.Return) and x.value for x in ast.walk(m)) else 'Unit') for m in selected}
returns.update(clock='Double',_read_mem='Int',_dummy='Int')
ports={'AB':0,'CD':1,'EF':2,'RES':3}
locals_={}
def conv(e,t,w):
 if not w or t==w: return e
 if w=='Boolean': return f'({e} != '+('0.0)' if t=='Double' else '0L)' if t=='Long' else '0)')
 if t=='Boolean': return f'(if ({e}) '+('1.0 else 0.0)' if w=='Double' else '1L else 0L)' if w=='Long' else '1 else 0)')
 return f'({e}).to{w}()'
def expr(x,w=None):
 if isinstance(x,ast.Constant):
  v=x.value
  if isinstance(v,bool): e,t=str(v).lower(),'Boolean'
  elif isinstance(v,str): e,t=str(ports[v]),'Int'
  elif v is None: e,t='null','Unit'
  else: e,t=str(v), 'Double' if isinstance(v,float) else 'Int'
 elif isinstance(x,ast.Name): e,t=x.id, locals_.get(x.id,'Int')
 elif isinstance(x,ast.Attribute): e,t=x.attr,ft.get(x.attr,'Int')
 elif isinstance(x,ast.Subscript):
  a,_=expr(x.value); b,_=expr(x.slice,'Int'); e,t=f'{a}[{b}]','Int'
 elif isinstance(x,ast.UnaryOp):
  if isinstance(x.op,ast.Not): a,_=expr(x.operand,'Boolean');e,t=f'!({a})','Boolean'
  elif isinstance(x.op,ast.Invert): a,t=expr(x.operand,'Int'); e=f'({a}).inv()'
  else: a,t=expr(x.operand);e=f'-({a})'
 elif isinstance(x,ast.BoolOp):
  e='(' + (' && ' if isinstance(x.op,ast.And) else ' || ').join(expr(y,'Boolean')[0] for y in x.values)+')';t='Boolean'
 elif isinstance(x,ast.Compare):
  a,ta=expr(x.left); b,tb=expr(x.comparators[0]); typ='Double' if 'Double' in (ta,tb) else 'Long' if 'Long' in (ta,tb) else 'Int'
  a=conv(a,ta,typ);b=conv(b,tb,typ)
  op={ast.Eq:'==',ast.NotEq:'!=',ast.Lt:'<',ast.LtE:'<=',ast.Gt:'>',ast.GtE:'>='}[type(x.ops[0])]
  e,t=f'({a} {op} {b})','Boolean'
 elif isinstance(x,ast.BinOp):
  a,ta=expr(x.left);b,tb=expr(x.right)
  bit=type(x.op) in (ast.BitAnd,ast.BitOr,ast.BitXor,ast.LShift,ast.RShift)
  t='Int' if bit else 'Double' if 'Double' in (ta,tb) or isinstance(x.op,ast.Div) else 'Long' if 'Long' in (ta,tb) else 'Int'
  a=conv(a,ta,t);b=conv(b,tb,t)
  op={ast.Add:'+',ast.Sub:'-',ast.Mult:'*',ast.Div:'/',ast.FloorDiv:'/',ast.Mod:'%',ast.BitAnd:'and',ast.BitOr:'or',ast.BitXor:'xor',ast.LShift:'shl',ast.RShift:'shr'}[type(x.op)]
  e=f'({a} {op} {b})'
 elif isinstance(x,ast.Call):
  if isinstance(x.func,ast.Name) and x.func.id=='int': return expr(x.args[0],'Int')
  if isinstance(x.func,ast.Attribute):
   name=x.func.attr
   if isinstance(x.func.value,ast.Attribute) and x.func.value.attr=='_interconnect': e,t='Unit','Unit'
   elif isinstance(x.func.value,ast.Attribute) and x.func.value.attr=='_sound':
    e,t='sound.'+name+'('+', '.join(expr(a,'Double' if name=='tone' and i==1 else 'Int')[0] for i,a in enumerate(x.args))+')','Unit'
   else:
    types={'_timers_clock':['Double']}
    e=name+'('+', '.join(expr(a,types.get(name,['Int']*len(x.args))[i])[0] for i,a in enumerate(x.args))+')';t=returns.get(name,'Unit')
  else: raise ValueError(ast.dump(x))
 elif isinstance(x,ast.Dict): e,t='intArrayOf('+', '.join(expr(y,'Int')[0] for y in x.values)+')','Array'
 else: raise ValueError(ast.dump(x))
 return conv(e,t,w),w or t
out=[]
def emit(s,lev):out.append('    '*lev+s)
def block(stmts,lev,ret):
 for s in stmts:
  if isinstance(s,ast.Assign):
   for target in s.targets:
    lhs,_=expr(target); typ=ft.get(target.attr,'Array') if isinstance(target,ast.Attribute) else locals_.get(target.id,'Int') if isinstance(target,ast.Name) else 'Int'
    emit(lhs+' = '+expr(s.value,typ if typ!='Array' else None)[0],lev)
  elif isinstance(s,ast.AugAssign):
   emit(expr(s.target)[0]+' = '+expr(ast.BinOp(s.target,s.op,s.value),expr(s.target)[1])[0],lev)
  elif isinstance(s,(ast.If,ast.While)):
   emit(('if' if isinstance(s,ast.If) else 'while')+' ('+expr(s.test,'Boolean')[0]+') {',lev);block(s.body,lev+1,ret);emit('}',lev)
   if s.orelse: emit('else {',lev);block(s.orelse,lev+1,ret);emit('}',lev)
  elif isinstance(s,ast.Return):emit('return '+expr(s.value,ret)[0],lev)
  elif isinstance(s,ast.Expr):
   v=expr(s.value)[0]
   if v!='Unit':emit(v,lev)
  elif isinstance(s,ast.Pass):pass
  else:raise ValueError(ast.dump(s))
header='''package com.digimon.glyph.emulator

/** SPL61X/GPL191X port of the pinned BrickEmuPy reference. See docs/dpower/UPSTREAM-LICENSE.
 * Instruction quirks and fractional timer arithmetic intentionally match that reference.
 * Regenerate instruction/SFR bodies with tools/port_spl61x.py.
 */
@Suppress("UNUSED_PARAMETER", "VARIABLE_WITH_REDUNDANT_INITIALIZER", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE", "NAME_SHADOWING")
class Spl61xCore(rom: ByteArray, val sound: DPowerSound) {
    val _mem = IntArray(rom.size) { rom[it].toInt() and 255 }
    var _pullup_ext = IntArray(3)
    var _port_input = Array(3) { IntArray(2) }
    var _PDIR = IntArray(3)
    var _PLATCH = IntArray(3)
'''
for name,t in sorted(ft.items()): header+=f'    var {name}: {t} = '+('0.0' if t=='Double' else '0L' if t=='Long' else '0')+'\n'
header+='    init { require(rom.isNotEmpty()); _mem_size = rom.size; _clock = 2_200_000; _sub_clock_div = _clock / 32768.0; reset() }\n'
header+='    companion object {\n'
for name,v in constants.items():
 header+=f'        '+('val '+name+' = intArrayOf('+','.join(map(str,v))+')' if isinstance(v,tuple) else 'const val '+name+' = '+str(v))+'\n'
header+='    }\n'
for m in selected:
 args=m.args.args[1:]
 locals_={a.arg:('Double' if a.arg=='exec_cycles' else 'Int') for a in args}
 names={x.id for x in ast.walk(m) if isinstance(x,ast.Name) and isinstance(x.ctx,ast.Store)}
 locals_.update({n:'Int' for n in names})
 for _ in range(4):
  for a in ast.walk(m):
   if isinstance(a,ast.Assign):
    for t in a.targets:
     if isinstance(t,ast.Name): locals_[t.id]=expr(a.value)[1]
 emit('fun '+m.name+'('+', '.join(a.arg+': '+locals_[a.arg] for a in args)+'): '+returns[m.name]+' {',1)
 for n in sorted(names):emit('var '+n+': '+locals_[n]+' = '+('0.0' if locals_[n]=='Double' else '0'),2)
 block(m.body,2,returns[m.name]);emit('}',1)
header+='\n'.join(out)+'\n'
header+='''    fun _read_mem(addr: Int): Int {
        if (addr >= CPU_RAM_OFFSET) return _mem[((addr and 0x7FFF) or (_BANK * (addr and 0x8000))) % _mem_size]
        return when(addr) {
'''
for k,v in zip(sfr.keys,sfr.values):header+=f'            {ast.literal_eval(k)} -> {v.elts[0].attr}()\n'
header+='            else -> 0\n        }\n    }\n    fun _write_mem(addr: Int, value: Int) {\n        if (addr in CPU_RAM_OFFSET until DPRAM_OFFSET + DPRAM_SIZE) { _mem[addr % _mem_size] = value; return }\n        when(addr) {\n'
for k,v in zip(sfr.keys,sfr.values):header+=f'            {ast.literal_eval(k)} -> {v.elts[1].attr}(value)\n'
header+='        }\n    }\n'
header+='''    fun clock(): Double {
        val cycles: Double
        if (_CPU_ENBL != 0) {
            val pc = _PC
            _PC = (pc + 1) and 0xFFFF
            val opcode = _mem[((pc and 0x7FFF) or (_BANK * (pc and 0x8000))) % _mem_size]
            val instructionCycles = when(opcode) {
'''
for i,n in enumerate(ops):header+=f'                {i} -> {n}()\n'
header+='''                else -> error("Invalid opcode")
            }
            cycles = (instructionCycles * _CPU_DIV).toDouble()
            _instr_counter++
            _timers_clock(cycles)
            if (_IF == 0 && (_IREQ and _INT_CTRL) != 0) _IRQ()
        } else {
            cycles = _sub_clock_div
            if ((_WAKEUP_CTRL and _WAKEUPREQ) != 0) { _CPU_ENBL = 1; _CPU_DIV = SFR_CPU_DIV_DEFAULT }
            if (_CLK32K_ENABLE != 0) _timers_clock(cycles)
        }
        sound.advanceAudio(cycles)
        return cycles
    }
    private fun _dummy() = 2
    fun runCycles(target: Double): Double {
        var elapsed = 0.0
        while (elapsed < target) elapsed += clock()
        return elapsed
    }
}
'''
(root/'app/src/main/java/com/digimon/glyph/emulator/Spl61xCore.kt').write_text(header)
# Portable fields stay outside the instruction path.
f='''package com.digimon.glyph.emulator

import com.google.gson.JsonObject

internal fun Spl61xCore.fields(): JsonObject = JsonObject().also { o ->
'''
for n in sorted(ft):f+=f'    o.addProperty("{n}", {n})\n'
for n in ('_pullup_ext','_PDIR','_PLATCH'):
 f+=f'    o.add("{n}", JsonObject().also {{ p -> '+ '; '.join(f'p.addProperty("{p}", {n}[{i}])' for i,p in enumerate(('AB','CD','EF')))+' })\n'
f+='    o.add("_port_input", JsonObject().also { p ->\n'
for i,p in enumerate(('AB','CD','EF')):f+=f'        p.add("{p}", com.google.gson.JsonArray().also {{ a -> _port_input[{i}].forEach {{ a.add(it) }} }})\n'
f+='    })\n}\ninternal fun Spl61xCore.loadFields(o: JsonObject) {\n'
for n,t in sorted(ft.items()):f+=f'    {n} = o["{n}"].'+('let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1.0 else 0.0 } else it.asDouble }' if t=='Double' else 'asLong' if t=='Long' else 'let { if (it.asJsonPrimitive.isBoolean) { if (it.asBoolean) 1 else 0 } else it.asInt }')+'\n'
for n in ('_pullup_ext','_PDIR','_PLATCH'):
 for i,p in enumerate(('AB','CD','EF')):f+=f'    {n}[{i}] = o["{n}"].asJsonObject["{p}"].asInt\n'
for i,p in enumerate(('AB','CD','EF')):f+=f'    for (j in 0..1) _port_input[{i}][j] = o["_port_input"].asJsonObject["{p}"].asJsonArray[j].asInt\n'
f+='}\n'
(root/'app/src/main/java/com/digimon/glyph/emulator/Spl61xFields.kt').write_text(f)
