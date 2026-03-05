import xml.etree.ElementTree as ET
import json
import base64
import struct

def load_ram(file_path):
    tree = ET.parse(file_path)
    root = tree.getroot()
    rams = {}
    
    for child in root:
        if child.attrib.get('name', '').startswith('slot_state_json_'):
            slot_id = child.attrib['name'].split('_')[-1]
            data = json.loads(child.text)
            ram_b64 = data.get("RAM", "")
            if ram_b64:
                ram_bytes = base64.b64decode(ram_b64)
                # Unpack Java/Kotlin IntArray: each Int is 4 bytes (big-endian usually in DataOutputStream, or let's check structure)
                # Actually, E0C6200 uses IntArray, which Gson or default serialization might encode via ByteBuffer/ObjectOutputStream or a custom method?
                # Looking at E0C6200.kt, it might just use custom packing in `saveState` or it's just raw bytes.
                # Let's decode assuming 4-byte big-endian integers.
                count = len(ram_bytes) // 4
                try:
                    ram_ints = struct.unpack(f">{count}i", ram_bytes)
                except:
                    # try little endian
                    ram_ints = struct.unpack(f"<{count}i", ram_bytes)
                rams[f"Slot {slot_id}"] = ram_ints
    
    return rams

def compare_rams(rams):
    if len(rams) < 2:
        print("Not enough slots to compare")
        return

    keys = list(rams.keys())
    ref_key = keys[0]
    ref_ram = rams[ref_key]
    
    # We want to find nibbles that differ across the slots.
    indices_of_interest = []
    
    for i in range(len(ref_ram)):
        vals = set()
        for k in keys:
            if i < len(rams[k]):
                vals.add(rams[k][i])
        if len(vals) > 1:
            indices_of_interest.append(i)
            
    print(f"Found {len(indices_of_interest)} nibbles that differ across the slots.")
    print("Slot 1 is Scumon (from user image if they just saved current, or maybe they are in any order). Patamon and Tokomon are others.")
    print("In typical V-Pet V1/V2/V3, Scumon is ID 0x0A (or similar), Patamon is 0x04 or 0x05, Tokomon is 0x02.")
    
    # Let's see the progression of these values
    for idx in indices_of_interest:
        values_str = ", ".join([f"{k}: {rams[k][idx]:02X}" for k in keys if idx < len(rams[k])])
        print(f"Offset 0x{idx:03X} : {values_str}")

if __name__ == "__main__":
    rams = load_ram("scumon_state.xml")
    for k, v in rams.items():
        print(f"{k} RAM size: {len(v)} nibbles")
    compare_rams(rams)
