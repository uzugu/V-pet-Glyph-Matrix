import xml.etree.ElementTree as ET
import json
import base64
import struct

def convert():
    tree = ET.parse("scumon_state.xml")
    root = tree.getroot()
    
    for child in root:
        if child.attrib.get('name', '').startswith('slot_state_json_'):
            slot_id = child.attrib['name'].split('_')[-1]
            data = json.loads(child.text)
            
            # The RAM in data is base64 encoded IntArray (each nibble is 4 bytes).
            ram_b64 = data.get("RAM", "")
            if not ram_b64:
                continue
            
            ram_bytes = base64.b64decode(ram_b64)
            count = len(ram_bytes) // 4
            try:
                ram_ints = struct.unpack(f">{count}i", ram_bytes)
            except:
                ram_ints = struct.unpack(f"<{count}i", ram_bytes)
            
            # Same for VRAM.
            vram_b64 = data.get("VRAM", "")
            vram_bytes = base64.b64decode(vram_b64)
            vcount = len(vram_bytes) // 4
            try:
                vram_ints = struct.unpack(f">{vcount}i", vram_bytes)
            except:
                vram_ints = struct.unpack(f"<{vcount}i", vram_bytes)
                
            out = {
                "RAM0": {str(i): ram_ints[i] for i in range(min(256, count))},
                "RAM1": {str(i): ram_ints[256 + i] for i in range(min(256, count - 256))},
                "RAM2": {str(i): ram_ints[512 + i] for i in range(min(128, count - 512))},
                "VRAM": {str(i): vram_ints[i] for i in range(vcount)}
            }
            
            # Additional registers just for safety
            for k in ["A", "B", "IX", "IY", "SP", "PC", "NPC"]:
                if k in data:
                    out[k] = data[k]
            
            out_file = f"slot{slot_id}.json"
            with open(out_file, "w") as f:
                json.dump(out, f, indent=2)
            print(f"Generated {out_file}")

if __name__ == "__main__":
    convert()
