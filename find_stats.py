import json

def load_slot(filename):
    with open(filename, 'r') as f:
        return json.load(f)

def run_analysis():
    s1 = load_slot("slot1.json") # Scumon
    s2 = load_slot("slot2.json") # Tokomon
    s3 = load_slot("slot3.json") # Patamon

    print("=== Searching for Species ID Patterns ===")
    # Tokomon (S2) should be 1 less than Patamon (S3) in most internal ID lists
    # And Scumon (S1) should be higher than both (often around 10 or 0x0A)
    for k in ["RAM0", "RAM1"]:
        r1, r2, r3 = s1[k], s2[k], s3[k]
        for idx in range(256):
            if str(idx) in r1 and str(idx) in r2 and str(idx) in r3:
                v1, v2, v3 = r1[str(idx)], r2[str(idx)], r3[str(idx)]
                
                # Condition: S3 = S2 + 1, and S1 > S3
                if (v3 == v2 + 1) and (v1 > v3):
                    print(f"Pattern Match at {k}[{idx}] (hex {hex(idx)}): Scumon={v1}, Tokomon={v2}, Patamon={v3}")

if __name__ == "__main__":
    run_analysis()
