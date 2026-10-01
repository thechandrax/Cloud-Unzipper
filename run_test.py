import os
import sys

# Force UTF-8 stdout on Windows console
if sys.platform == "win32":
    try:
        sys.stdout.reconfigure(encoding='utf-8')
        sys.stderr.reconfigure(encoding='utf-8')
    except Exception:
        pass

import time
import zipfile
import shutil
import subprocess
import json
import urllib.request

TEST_BASE = os.path.abspath("./test_drive")
SRC_DIR = os.path.join(TEST_BASE, "GDFlix")
DEST_DIR = os.path.join(TEST_BASE, "MOVIES & WEB SERIES INFO")
TEST_ZIP = os.path.join(SRC_DIR, "Better Call Saul (2015) S01 1080p 10bit BluRay.zip")

print("=" * 65)
print("[TEST] AUTOMATED CLOUD UNZIPPER TEST HARNESS")
print("=" * 65)

# 1. Clean & prepare mock environment
if os.path.exists(TEST_BASE):
    shutil.rmtree(TEST_BASE)

os.makedirs(SRC_DIR, exist_ok=True)
os.makedirs(DEST_DIR, exist_ok=True)

# 2. Create real dummy video files inside zip
print("\n[Step 1] Creating mock video archive with episodes...")
dummy_files = [
    "Better.Call.Saul.S01E01.Uno.1080p.mkv",
    "Better.Call.Saul.S01E02.Mijo.1080p.mkv",
    "Better.Call.Saul.S01E03.Nacho.1080p.mkv"
]

with zipfile.ZipFile(TEST_ZIP, 'w') as zf:
    for fname in dummy_files:
        # 1MB dummy content for each episode
        zf.writestr(fname, b"FAKE_VIDEO_STREAM_DATA_" * 50000)

print(f"[OK] Created mock zip: {os.path.basename(TEST_ZIP)} ({os.path.getsize(TEST_ZIP) / (1024*1024):.2f} MB)")
print(f"[INFO] Files inside zip: {dummy_files}")

# 3. Start server.py in background
print("\n[Step 2] Starting Cloud Unzipper FastAPI Server on port 8089...")
env = os.environ.copy()
env["DRIVE_MOUNT_PATH"] = TEST_BASE

server_proc = subprocess.Popen(
    [sys.executable, "-m", "uvicorn", "server:app", "--host", "127.0.0.1", "--port", "8089"],
    cwd=os.path.abspath("./cloud-backend"),
    env=env,
    stdout=subprocess.PIPE,
    stderr=subprocess.PIPE,
    text=True
)

time.sleep(2.5)

try:
    # 4. Health Check
    print("\n[Step 3] Testing / health check...")
    with urllib.request.urlopen("http://127.0.0.1:8089/") as resp:
        health_data = json.loads(resp.read().decode('utf-8'))
        print(f"[OK] Health Check Status: {health_data.get('status')} (Mounted: {health_data.get('drive_mounted')})")
        assert health_data.get("status") == "online"

    # 5. Execute Extraction via SSE stream
    print("\n[Step 4] Calling /api/extract-stream (simulating Android app request)...")
    req_body = json.dumps({
        "source_folder": "GDFlix",
        "destination_folder": "MOVIES & WEB SERIES INFO",
        "exact_file_name": "",
        "password": ""
    }).encode('utf-8')

    req = urllib.request.Request(
        "http://127.0.0.1:8089/api/extract-stream",
        data=req_body,
        headers={"Content-Type": "application/json"}
    )

    events_received = []
    with urllib.request.urlopen(req) as stream:
        for line_bytes in stream:
            line = line_bytes.decode('utf-8').strip()
            if line.startswith("data: "):
                payload = json.loads(line[6:])
                events_received.append(payload)
                status = payload.get("status")
                msg = payload.get("message", "")
                pct = payload.get("progress", "")
                pct_str = f"[{pct}%] " if pct != "" else ""
                print(f"   -> SSE Stream: {pct_str}{msg}")

    # 6. Verify Files On Disk
    print("\n[Step 5] Verifying extracted files on disk...")
    extracted_files = os.listdir(DEST_DIR)
    print(f"[INFO] Found in destination folder: {extracted_files}")

    for expected in dummy_files:
        if expected in extracted_files:
            size_mb = os.path.getsize(os.path.join(DEST_DIR, expected)) / (1024 * 1024)
            print(f"   [VERIFIED FILE] {expected} ({size_mb:.2f} MB)")
        else:
            raise FileNotFoundError(f"Missing expected extracted file: {expected}")

    print("\n" + "=" * 65)
    print("[SUCCESS] ALL TESTS PASSED! REAL EXTRACTION VERIFIED ON DISK!")
    print("=" * 65)

finally:
    # Terminate server
    server_proc.terminate()
    server_proc.wait()
