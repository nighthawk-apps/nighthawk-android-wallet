import socket
import json
import time

def send_rpc(method, params=[]):
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.connect(('127.0.0.1', 18345))
        
        req = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
            "id": 1
        }
        
        s.sendall((json.dumps(req) + "\n").encode('utf-8'))
        
        resp = b""
        while True:
            chunk = s.recv(4096)
            if not chunk:
                break
            resp += chunk
            if b'\n' in resp:
                break
                
        s.close()
        
        return json.loads(resp.decode('utf-8'))
    except Exception as e:
        return {"error": str(e)}

print(send_rpc("get_network_info"))
