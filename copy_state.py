import subprocess

src_adb = "192.168.0.23:44863"
dst_adb = "192.168.0.24:43421"
pkg = "com.digimon.glyph"

def pull_shared_prefs(device, pref_name, out_path):
    print(f"[{device}] Extracting {pref_name}...")
    cmd = ["adb", "-s", device, "exec-out", "run-as", pkg, "cat", f"shared_prefs/{pref_name}"]
    out = subprocess.check_output(cmd)
    with open(out_path, "wb") as f:
        f.write(out)
    print(f"Saved {len(out)} bytes to {out_path}")

def push_shared_prefs(device, pref_name, in_path):
    print(f"[{device}] Injecting {pref_name} directly to app data via stdin...")
    with open(in_path, "rb") as f:
        data = f.read()
    
    cmd = ["adb", "-s", device, "exec-in", "run-as", pkg, "sh", "-c", f"cat > shared_prefs/{pref_name}"]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    p.communicate(input=data)
    if p.returncode != 0:
        raise Exception(f"Failed to inject {pref_name}")
    print(f"[{device}] Injected {len(data)} bytes")
    
if __name__ == "__main__":
    try:
        # Transfer State
        pull_shared_prefs(src_adb, "digimon_state.xml", "tools/digimon_state.xml")
        push_shared_prefs(dst_adb, "digimon_state.xml", "tools/digimon_state.xml")
        
        # Transfer Transport Configs
        pull_shared_prefs(src_adb, "battle_transport_settings.xml", "tools/battle_transport_settings.xml")
        push_shared_prefs(dst_adb, "battle_transport_settings.xml", "tools/battle_transport_settings.xml")
        
        # Force stop the app on the destination device to recognize the new preferences
        subprocess.call(["adb", "-s", dst_adb, "shell", "am", "force-stop", pkg])
        print("Transfer complete!")
        
    except Exception as e:
        print(f"Error transferring data: {e}")
