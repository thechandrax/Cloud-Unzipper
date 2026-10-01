import asyncio
import os
import glob
import shutil
import subprocess
import zipfile
import json
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="Drive Cloud Unzipper API", version="1.2.0")

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
            cmd = ["7z", "x", "-y", f"-o{dest_path}"]
            if req.password:
                cmd.append(f"-p{req.password}")
            else:
                cmd.append("-p-")
            cmd.append(target_archive)

            proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
            for line in iter(proc.stdout.readline, ''):
                l = line.strip()
                if "%" in l or "Extracting" in l:
                    yield f"data: {json.dumps({'status': 'progress', 'message': f'⏳ {l[:80]}'})}\n\n"
                    await asyncio.sleep(0.04)
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

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
