import socket
import time

s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
s.settimeout(10)
try:
    s.connect(('109.224.229.205', 19792))
    print("Connected! Sending auto-matchmaking payload...")
    payload = b'{"op":"join","room":"auto","role":"host","name":"ai","proto":"digimon-battle-v1"}\n'
    s.sendall(payload)
    response = s.recv(1024)
    print(f"Response: {response}")
except Exception as e:
    print(f"Error: {e}")
finally:
    s.close()
