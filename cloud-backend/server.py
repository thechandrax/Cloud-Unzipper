import asyncio
import os
import glob
import subprocess
import json
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse
from pydantic import BaseModel

app = FastAPI(title="Drive Cloud Unzipper API", version="1.1.0")

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

@app.get("/")
def health_check():
    drive_mounted = os.path.exists("/content/drive/MyDrive")
    return {
        "status": "online",
        "drive_mounted": drive_mounted,
        "message": "Cloud Unzipper Backend is ready!"
    }

@app.post("/api/extract-stream")
async def extract_stream(req: ExtractRequest):
    async def event_generator():
        yield f"data: {json.dumps({'status': 'starting', 'message': '🚀 Initializing Cloud Extraction engine...'})}\n\n"
        await asyncio.sleep(0.3)

        # Drive base directory in Google Colab
        drive_base = os.getenv("DRIVE_MOUNT_PATH", "/content/drive/MyDrive")
        if not os.path.exists(drive_base):
            yield f"data: {json.dumps({'status': 'error', 'message': '❌ Google Drive not mounted at /content/drive/MyDrive! Please run drive.mount in Colab.'})}\n\n"
            return

        src_path = os.path.join(drive_base, req.source_folder.strip("/"))
        dest_path = os.path.join(drive_base, req.destination_folder.strip("/"))

        yield f"data: {json.dumps({'status': 'scanning', 'message': f'🔍 Scanning: {src_path}'})}\n\n"
        await asyncio.sleep(0.3)

        if not os.path.exists(src_path):
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ Source folder not found: {src_path}'})}\n\n"
            return

        # Target archive detection
        target_archive = None
        if req.exact_file_name.strip():
            candidate = os.path.join(src_path, req.exact_file_name.strip())
            if os.path.exists(candidate):
                target_archive = candidate
            else:
                # Fuzzy match in folder
                for f in os.listdir(src_path):
                    if req.exact_file_name.strip().lower() in f.lower():
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
            existing = [f for f in os.listdir(src_path) if f.endswith(('.zip', '.rar', '.7z', '.mkv'))][:5]
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ No matching archive found in {req.source_folder}. Existing files: {existing}'})}\n\n"
            return

        os.makedirs(dest_path, exist_ok=True)
        file_size_gb = os.path.getsize(target_archive) / (1024 ** 3)
        file_name = os.path.basename(target_archive)

        yield f"data: {json.dumps({'status': 'progress', 'progress': 10, 'message': f'🎯 Target: {file_name} ({file_size_gb:.2f} GB)'})}\n\n"
        yield f"data: {json.dumps({'status': 'progress', 'progress': 20, 'message': f'📂 Extracting to: MyDrive/{req.destination_folder}'})}\n\n"

        cmd = ["7z", "x", "-y", f"-o{dest_path}"]
        if req.password:
            cmd.append(f"-p{req.password}")
        else:
            cmd.append("-p-")
        cmd.append(target_archive)

        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)

        for line in iter(proc.stdout.readline, ''):
            line_str = line.strip()
            if "%" in line_str or "Extracting" in line_str:
                yield f"data: {json.dumps({'status': 'progress', 'message': f'⏳ {line_str[:80]}'})}\n\n"
                await asyncio.sleep(0.04)

        proc.wait()

        if proc.returncode == 0:
            yield f"data: {json.dumps({'status': 'success', 'progress': 100, 'message': f'🎉 ✅ SUCCESS! All files extracted into MyDrive/{req.destination_folder}'})}\n\n"
        else:
            yield f"data: {json.dumps({'status': 'error', 'message': f'❌ 7-Zip failed with code {proc.returncode}. Corrupted archive or password required.'})}\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
