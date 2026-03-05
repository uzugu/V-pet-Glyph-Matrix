import socket
import time
import threading

def run_client(role):
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.settimeout(5)
    try:
        s.connect(('109.224.229.205', 19792))
        payload = f'{{"op":"join","room":"auto","role":"{role}","name":"ai-{role}","proto":"digimon-battle-v1"}}\n'.encode()
        s.sendall(payload)
        time.sleep(1) # wait for host to establish
        response = s.recv(1024)
        print(f"[{role}] Response 1: {response}")
        try:
            r2 = s.recv(1024)
            print(f"[{role}] Response 2: {r2}")
        except:
            pass
    except Exception as e:
        print(f"[{role}] Error: {e}")
    finally:
        s.close()
        
host_thread = threading.Thread(target=run_client, args=("host",))
host_thread.start()
time.sleep(1)
join_thread = threading.Thread(target=run_client, args=("join",))
join_thread.start()

host_thread.join()
join_thread.join()
