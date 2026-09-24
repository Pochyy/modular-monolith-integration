import urllib.request
import urllib.error
import xml.etree.ElementTree as ET
import time
import json
import random

API_KEY = "LSK-B23C76B4EF7D0D35EBC7"
CLIENT_ID = "21-0587-173"
BASE_URL = "https://legacysupply.onrender.com/api/v1"
SESSION_TOKEN = None

def get_token():
    global SESSION_TOKEN
    body = f"<AuthRequest><ClientId>{CLIENT_ID}</ClientId><ApiKey>{API_KEY}</ApiKey></AuthRequest>".encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/auth/token", data=body, headers={'Content-Type': 'application/xml'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            root = ET.fromstring(response.read())
            SESSION_TOKEN = root.find('SessionToken').text
            print(f"Token: {SESSION_TOKEN[:12]}...")
    except Exception as e:
        print(f"Auth failed: {e}")

def create_po_raw(req_id, buyer_ref, sku="NJL-7284", qty=1):
    global SESSION_TOKEN
    if not SESSION_TOKEN:
        get_token()
    body = f"<PurchaseOrder><SupplierSku>{sku}</SupplierSku><Qty>{qty}</Qty><BuyerRef>{buyer_ref}</BuyerRef></PurchaseOrder>".encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/purchase-orders", data=body, headers={
        'Content-Type': 'application/xml',
        'X-LS-Session': SESSION_TOKEN,
        'X-Request-Id': req_id
    }, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=10) as response:
            root = ET.fromstring(response.read())
            po = root.find('PoNumber').text
            return po, response.getcode(), ""
    except urllib.error.HTTPError as e:
        body_str = e.read().decode('utf-8')
        return None, e.code, body_str
    except Exception as e:
        return None, 0, str(e)

def verify_check():
    body = json.dumps({"studentId": CLIENT_ID, "apiKey": API_KEY}).encode('utf-8')
    req = urllib.request.Request("https://legacysupply.onrender.com/verify/api", data=body,
                                  headers={'Content-Type': 'application/json'}, method='POST')
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            data = json.loads(response.read())
        print("\n--- Checklist ---")
        for c in data['checklist']:
            mark = '[x]' if c['ok'] else '[ ]'
            print(f"  {mark} {c['label']}: {c['detail']}")
        return data
    except Exception as e:
        print(f"Verify failed: {e}")
        return {}

get_token()
verify_check()

print("\n=== OUTAGE RETRY PROBE ===")
print("Submitting POs rapidly until one gets a 503, then retrying with SAME X-Request-Id...")
print("This proves 'orders blocked by outage were placed later'")

attempt = 0
while True:
    attempt += 1
    rng = random.randint(100000, 999999)
    req_id = f"REQ-OTG-{rng}"
    buyer_ref = f"BUY-OTG-{rng}"
    
    po, code, body = create_po_raw(req_id, buyer_ref)
    
    if code == 401:
        get_token()
        continue
    
    if code in (503, 0) or "E-SYS-99" in body or "E-SYS-50" in body or "unavailable" in body.lower():
        print(f"\n  [OUTAGE] Got {code} on attempt {attempt}! req_id={req_id}")
        print(f"  Error body: {body[:200]}")
        print(f"  Retrying with same req_id in 2 seconds...")
        time.sleep(2)
        
        po2, code2, body2 = create_po_raw(req_id, buyer_ref)
        if code2 == 401:
            get_token()
            po2, code2, body2 = create_po_raw(req_id, buyer_ref)
        
        if code2 in (200, 201):
            print(f"  [SUCCESS] Outage recovery! PO={po2}")
            verify_check()
        else:
            print(f"  [FAIL] Retry gave code={code2}: {body2[:200]}")
            # Try once more
            time.sleep(3)
            po3, code3, body3 = create_po_raw(req_id, buyer_ref)
            if code3 in (200, 201):
                print(f"  [SUCCESS 2nd retry] PO={po3}")
                verify_check()
    elif code in (200, 201):
        print(f"  [OK] attempt {attempt}: PO={po}")
    else:
        print(f"  [?] code={code} body={body[:100]}")
    
    time.sleep(0.3)
    
    if attempt % 20 == 0:
        print(f"\n  [Progress] {attempt} attempts so far...")
        verify_check()
