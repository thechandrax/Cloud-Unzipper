import asyncio
import os
import glob
import shutil
import subprocess
import zipfile
import json
import re
import time
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="Drive Cloud Unzipper API", version="1.5.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

class ExtractRequest(BaseModel):
    source_folder: str = "GDFlix"
    destination_folder: str = "MOVIES & WEB SERIES INFO"
    exact_file_name: str = ""
    password: str = ""

class TransferRequest(BaseModel):
    url: str
    destination_folder: str = "GDFlix"
    custom_filename: str = ""
    auto_extract: bool = True
    extract_destination: str = "MOVIES & WEB SERIES INFO"

def get_drive_base():
    # Supports Google Colab default, environment variable, or local test directory
    env_path = os.getenv("DRIVE_MOUNT_PATH")
    if env_path and os.path.exists(env_path):
        return os.path.abspath(env_path)
    if os.path.exists("/content/drive/MyDrive"):
        return "/content/drive/MyDrive"
    # Local fallback for testing
    if os.path.exists("./test_drive"):
        return os.path.abspath("./test_drive")
    return "/content/drive/MyDrive"

@app.get("/")
def health_check():
    drive_base = get_drive_base()
    mounted = os.path.exists(drive_base)
    return {
        "status": "online",
        "drive_mounted": mounted,
        "drive_base": drive_base,
        "message": "Cloud Unzipper Backend is ready!"
    }

@app.get("/api/list-folders")
def list_folders(path: str = ""):
    drive_base = get_drive_base()
    target_dir = os.path.join(drive_base, path.strip("/\\")) if path.strip("/\\") else drive_base
    if not os.path.exists(target_dir):
        return {"status": "error", "message": f"Folder not found: {path}", "folders": [], "archives": []}
    try:
        folders = []
        archives = []
        for item in sorted(os.listdir(target_dir), key=lambda s: s.lower()):
            if item.startswith("."):
                continue
            full_path = os.path.join(target_dir, item)
            if os.path.isdir(full_path):
                folders.append(item)
            elif item.lower().endswith(('.zip', '.rar', '.7z', '.tar', '.gz', '.bz2', '.iso', '.mkv', '.mp4', '.avi')):
                archives.append(item)
        return {
            "status": "success",
            "current_path": path.strip("/\\"),
            "folders": folders,
            "archives": archives
        }
    except Exception as e:
        return {"status": "error", "message": str(e), "folders": [], "archives": []}

@app.post("/api/create-folder")
def create_folder(folder_path: str):
    drive_base = get_drive_base()
    full_path = os.path.join(drive_base, folder_path.strip("/\\"))
    try:
        os.makedirs(full_path, exist_ok=True)
        return {"status": "success", "folder": folder_path}
    except Exception as e:
        return {"status": "error", "message": str(e)}

@app.post("/api/extract-stream")
async def extract_stream(req: ExtractRequest):
    async def event_generator():
        yield f"data: {json.dumps({'status': 'starting', 'message': '🚀 Initializing Cloud Extraction engine...'})}\n\n"
        await asyncio.sleep(0.3)

        drive_base = get_drive_base()
        if not os.path.exists(drive_base):
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Drive directory not found at {drive_base}. In Colab, please run drive.mount(\"/content/drive\").'})}\n\n"
            return

        src_path = os.path.join(drive_base, req.source_folder.strip("/\\"))
        dest_path = os.path.join(drive_base, req.destination_folder.strip("/\\"))

        yield f"data: {json.dumps({'status': 'scanning', 'message': f'🔍 Scanning source folder: {req.source_folder}'})}\n\n"
        await asyncio.sleep(0.3)

        if not os.path.exists(src_path):
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Source folder not found: MyDrive/{req.source_folder}'})}\n\n"
            return

        # Target archive detection
        target_archive = None
        if req.exact_file_name.strip():
            candidate = os.path.join(src_path, req.exact_file_name.strip())
            if os.path.exists(candidate):
                target_archive = candidate
            else:
                # Fuzzy match in folder
                search_term = req.exact_file_name.strip().lower()
                for f in os.listdir(src_path):
                    if search_term in f.lower() or f.lower() in search_term:
                        target_archive = os.path.join(src_path, f)
                        break

        if not target_archive:
            # Auto-find newest archive
            exts = ('*.zip', '*.rar', '*.7z', '*.tar', '*.tgz')
            files = []
            for ext in exts:
                files.extend(glob.glob(os.path.join(src_path, ext)))
                files.extend(glob.glob(os.path.join(src_path, ext.upper())))
            if files:
                files.sort(key=os.path.getmtime, reverse=True)
                target_archive = files[0]

        if not target_archive or not os.path.exists(target_archive):
            existing = [f for f in os.listdir(src_path) if f.endswith(('.zip', '.rar', '.7z', '.mkv', '.mp4'))][:5]
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ No archive found in {req.source_folder}. Available: {existing}'})}\n\n"
            return

        os.makedirs(dest_path, exist_ok=True)
        file_size_gb = os.path.getsize(target_archive) / (1024 ** 3)
        file_name = os.path.basename(target_archive)

        yield f"data: {json.dumps({'status': 'progress', 'progress': 10, 'message': f'🎯 Target: {file_name} ({file_size_gb:.2f} GB)'})}\n\n"
        yield f"data: {json.dumps({'status': 'progress', 'progress': 20, 'message': f'📂 Extracting to: MyDrive/{req.destination_folder}'})}\n\n"
        await asyncio.sleep(0.2)

        # Extraction Engine Selection: 7z -> tar -> Python zipfile fallback
        has_7z = shutil.which("7z") is not None
        has_tar = shutil.which("tar") is not None

        extraction_success = False

        if has_7z:
            yield f"data: {json.dumps({'status': 'progress', 'progress': 30, 'message': '⚡ Engine: Multi-core 7-Zip acceleration active'})}\n\n"
            cmd = ["7z", "x", "-y", "-bsp1", "-mmt=on", f"-o{dest_path}"]
            if req.password:
                cmd.append(f"-p{req.password}")
            else:
                cmd.append("-p-")
            cmd.append(target_archive)

            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            buf = ""
            while True:
                char = proc.stdout.read(1)
                if not char and proc.poll() is not None:
                    break
                if char:
                    buf += char
                    if char in ('\r', '\n'):
                        l = buf.strip()
                        buf = ""
                        if l and ("%" in l or "Extracting" in l):
                            m_pct = re.search(r'(\d+)%', l)
                            pct_val = int(m_pct.group(1)) if m_pct else None
                            payload = {'status': 'progress', 'message': f'⏳ {l[:80]}'}
                            if pct_val is not None:
                                mapped = 30 + int(pct_val * 0.68)
                                payload['progress'] = mapped
                            yield f"data: {json.dumps(payload)}\n\n"
                            await asyncio.sleep(0.01)
            proc.wait()
            extraction_success = (proc.returncode == 0)

        elif has_tar and target_archive.lower().endswith(('.zip', '.tar', '.tgz', '.tar.gz')):
            yield f"data: {json.dumps({'status': 'progress', 'progress': 30, 'message': '⚡ Engine: High-speed BSDTar active'})}\n\n"
            cmd = ["tar", "-xf", target_archive, "-C", dest_path]
            proc = subprocess.run(cmd, capture_output=True, text=True)
            extraction_success = (proc.returncode == 0)

        else:
            # Universal Python zipfile fallback (works on any system without external binaries)
            yield f"data: {json.dumps({'status': 'progress', 'progress': 30, 'message': '⚡ Engine: Standard Python Extractor active'})}\n\n"
            try:
                with zipfile.ZipFile(target_archive, 'r') as zf:
                    if req.password:
                        zf.setpassword(req.password.encode('utf-8'))
                    namelist = zf.namelist()
                    total = len(namelist)
                    for idx, member in enumerate(namelist, start=1):
                        zf.extract(member, dest_path)
                        pct = 30 + int((idx / total) * 65)
                        yield f"data: {json.dumps({'status': 'progress', 'progress': pct, 'message': f'Extracting [{idx}/{total}]: {os.path.basename(member)}'})}\n\n"
                        await asyncio.sleep(0.02)
                extraction_success = True
            except Exception as e:
                yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Extraction failed: {str(e)}'})}\n\n"
                return

        if extraction_success:
            # Count extracted files to confirm
            extracted_items = os.listdir(dest_path)
            yield f"data: {json.dumps({'status': 'success', 'progress': 100, 'message': f'🎉 ✅ SUCCESS! Extracted {len(extracted_items)} item(s) to MyDrive/{req.destination_folder}'})}\n\n"
        else:
            yield f"data: {json.dumps({'status': 'error', 'message': '❌ Extraction failed. Archive may be corrupted or require a password.'})}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

@app.post("/api/transfer-stream")
async def transfer_stream(req: TransferRequest):
    async def event_generator():
        yield f"data: {json.dumps({'status': 'starting', 'message': '🚀 Initializing Cloud Transfer Engine...'})}\n\n"
        await asyncio.sleep(0.2)

        drive_base = get_drive_base()
        if not os.path.exists(drive_base):
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Drive directory not found at {drive_base}. In Colab, run drive.mount(\"/content/drive\").'})}\n\n"
            return

        target_url = req.url.strip()
        if not target_url or not target_url.startswith(("http://", "https://")):
            yield f"data: {json.dumps({'status': 'error', 'message': '❌ Invalid download URL provided.'})}\n\n"
            return

        dest_dir = os.path.join(drive_base, req.destination_folder.strip("/\\"))
        os.makedirs(dest_dir, exist_ok=True)

        # Smart GDFlix URL resolving
        if "gdflix.io/file/" in target_url:
            yield f"data: {json.dumps({'status': 'progress', 'progress': 5, 'message': '🔍 Resolving GDFlix direct stream link...'})}\n\n"
            wurl = target_url.replace("/file/", "/wfile/")
            try:
                import urllib.request
                h_req = urllib.request.Request(wurl, headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'})
                with urllib.request.urlopen(h_req, timeout=5) as u_resp:
                    html_c = u_resp.read().decode('utf-8', errors='ignore')
                    found = re.findall(r'href=[\"\'](https?://[^\'\"]*(?:workers\.dev|gofile\.io)[^\'\"]*)[\"\']', html_c)
                    if found:
                        target_url = found[0]
                        yield f"data: {json.dumps({'status': 'progress', 'progress': 8, 'message': '✅ Resolved direct cloud link!'})}\n\n"
            except Exception:
                pass

        yield f"data: {json.dumps({'status': 'progress', 'progress': 10, 'message': f'📂 Destination: MyDrive/{req.destination_folder}'})}\n\n"
        await asyncio.sleep(0.2)

        has_aria2c = shutil.which("aria2c") is not None
        has_curl = shutil.which("curl") is not None or shutil.which("curl.exe") is not None

        download_success = False
        saved_file_path = None

        if has_aria2c:
            yield f"data: {json.dumps({'status': 'progress', 'progress': 15, 'message': '⚡ Engine: Multi-connection aria2c accelerator (16 streams)'})}\n\n"
            cmd = [
                "aria2c",
                "-x", "16",
                "-s", "16",
                "-k", "1M",
                "--file-allocation=none",
                "--summary-interval=1",
                "--console-log-level=warn",
                "--allow-overwrite=true",
                "--auto-file-renaming=false",
                "-d", dest_dir
            ]
            if req.custom_filename.strip():
                cmd.extend(["-o", req.custom_filename.strip()])
            cmd.append(target_url)

            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
            last_pct = 15
            aria_regex = re.compile(r'\[#[0-9a-fA-F]+\s+([0-9.]+\w+)/([0-9.]+\w+)\((\d+)%\)\s+CN:\d+\s+DL:([0-9.]+\w+)(?:\s+ETA:([0-9a-zA-Z]+))?\]')

            buf = ""
            while True:
                char = proc.stdout.read(1)
                if not char and proc.poll() is not None:
                    break
                if char:
                    buf += char
                    if char in ('\r', '\n'):
                        line = buf.strip()
                        buf = ""
                        if line:
                            m = aria_regex.search(line)
                            if m:
                                downloaded, total, pct_str, speed, eta = m.groups()
                                pct = int(pct_str)
                                if pct > last_pct:
                                    last_pct = pct
                                eta_str = f" • ETA: {eta}" if eta else ""
                                msg = f"📥 {downloaded}/{total} ({pct}%) • {speed}/s{eta_str}"
                                yield f"data: {json.dumps({'status': 'progress', 'progress': last_pct, 'message': msg})}\n\n"
                                await asyncio.sleep(0.01)
                            elif "Download complete" in line:
                                yield f"data: {json.dumps({'status': 'progress', 'progress': 99, 'message': f'💾 {line[:80]}'})}\n\n"
            proc.wait()
            download_success = (proc.returncode == 0)

        elif has_curl:
            curl_bin = "curl.exe" if shutil.which("curl.exe") else "curl"
            yield f"data: {json.dumps({'status': 'progress', 'progress': 15, 'message': '⚡ Engine: High-speed cURL streaming active'})}\n\n"

            out_name = req.custom_filename.strip() or os.path.basename(target_url.split("?")[0]) or "downloaded_archive.zip"
            if not out_name.endswith(('.zip', '.rar', '.7z', '.tar', '.mkv', '.mp4')):
                out_name += ".zip"
            target_path = os.path.join(dest_dir, out_name)

            cmd = [curl_bin, "-L", "-k", target_url, "-o", target_path]
            proc = subprocess.run(cmd, capture_output=True, text=True)
            download_success = (proc.returncode == 0 and os.path.exists(target_path) and os.path.getsize(target_path) > 0)
            if download_success:
                saved_file_path = target_path

        else:
            yield f"data: {json.dumps({'status': 'progress', 'progress': 15, 'message': '⚡ Engine: Standard Python HTTP stream'})}\n\n"
            try:
                import urllib.request
                req_obj = urllib.request.Request(target_url, headers={'User-Agent': 'Mozilla/5.0'})
                out_name = req.custom_filename.strip() or os.path.basename(target_url.split("?")[0]) or "downloaded_archive.zip"
                target_path = os.path.join(dest_dir, out_name)

                with urllib.request.urlopen(req_obj) as resp, open(target_path, 'wb') as out_f:
                    total_len = int(resp.headers.get('Content-Length', 0))
                    downloaded = 0
                    last_emit = time.time()

                    while True:
                        chunk = resp.read(1024 * 1024 * 2)
                        if not chunk:
                            break
                        out_f.write(chunk)
                        downloaded += len(chunk)
                        if total_len > 0 and time.time() - last_emit > 0.5:
                            last_emit = time.time()
                            pct = 15 + int((downloaded / total_len) * 80)
                            mb_down = downloaded / (1024 * 1024)
                            mb_tot = total_len / (1024 * 1024)
                            yield f"data: {json.dumps({'status': 'progress', 'progress': pct, 'message': f'📥 Downloading: {mb_down:.1f} MB / {mb_tot:.1f} MB ({pct}%)'})}\n\n"
                            await asyncio.sleep(0.01)
                download_success = True
                saved_file_path = target_path
            except Exception as e:
                yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Download failed: {str(e)}'})}\n\n"
                return

        if not download_success:
            yield f"data: {json.dumps({'status': 'error', 'message': '❌ Transfer failed. Please check the URL or use a direct mirror.'})}\n\n"
            return

        if not saved_file_path:
            candidates = [os.path.join(dest_dir, f) for f in os.listdir(dest_dir)]
            if candidates:
                candidates.sort(key=os.path.getmtime, reverse=True)
                saved_file_path = candidates[0]

        file_name = os.path.basename(saved_file_path) if saved_file_path else "file"
        file_size_gb = (os.path.getsize(saved_file_path) / (1024 ** 3)) if (saved_file_path and os.path.exists(saved_file_path)) else 0.0

        yield f"data: {json.dumps({'status': 'progress', 'progress': 100, 'message': f'💾 ✅ Saved: {file_name} ({file_size_gb:.2f} GB) in MyDrive/{req.destination_folder}'})}\n\n"
        await asyncio.sleep(0.3)

        if req.auto_extract and saved_file_path and saved_file_path.endswith(('.zip', '.rar', '.7z', '.tar', '.tgz')):
            yield f"data: {json.dumps({'status': 'progress', 'progress': 10, 'message': f'🚀 Auto-Extracting {file_name} to MyDrive/{req.extract_destination}...'})}\n\n"
            extract_dest = os.path.join(drive_base, req.extract_destination.strip("/\\"))
            os.makedirs(extract_dest, exist_ok=True)

            has_7z = shutil.which("7z") is not None
            if has_7z:
                cmd_ext = ["7z", "x", "-y", "-bsp1", "-mmt=on", f"-o{extract_dest}", "-p-", saved_file_path]
                proc_ext = subprocess.Popen(cmd_ext, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, bufsize=1)
                buf_e = ""
                while True:
                    c = proc_ext.stdout.read(1)
                    if not c and proc_ext.poll() is not None:
                        break
                    if c:
                        buf_e += c
                        if c in ('\r', '\n'):
                            le = buf_e.strip()
                            buf_e = ""
                            if le:
                                me = re.search(r'(\d+)%', le)
                                if me:
                                    pct_ext = int(me.group(1))
                                    yield f"data: {json.dumps({'status': 'progress', 'progress': pct_ext, 'message': f'⏳ Extracting: {pct_ext}% ({le[:50]})'})}\n\n"
                                    await asyncio.sleep(0.01)
                proc_ext.wait()
                if proc_ext.returncode == 0:
                    yield f"data: {json.dumps({'status': 'success', 'progress': 100, 'message': f'🎉 ✅ SUCCESS! Transferred & Extracted to MyDrive/{req.extract_destination}'})}\n\n"
                else:
                    yield f"data: {json.dumps({'status': 'error', 'message': f'⚠️ Downloaded to MyDrive/{req.destination_folder}, but extraction exited with code {proc_ext.returncode}'})}\n\n"
            else:
                yield f"data: {json.dumps({'status': 'success', 'progress': 100, 'message': f'🎉 ✅ File saved in MyDrive/{req.destination_folder}!'})}\n\n"
        else:
            yield f"data: {json.dumps({'status': 'success', 'progress': 100, 'message': f'🎉 ✅ SUCCESS! Transferred to MyDrive/{req.destination_folder}'})}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
