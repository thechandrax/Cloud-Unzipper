"""
Google Drive Cloud Unzipper
High-speed extraction for large video archives (.zip, .rar, .7z)
"""

import os
import sys
import glob
import subprocess
from google.colab import drive

# Mount Google Drive if not already mounted
if not os.path.exists('/content/drive/MyDrive'):
    print("🔄 Connecting to Google Drive...")
    drive.mount('/content/drive')
else:
    print("✅ Google Drive is connected.")

# Default paths (Customize as needed)
DEFAULT_SCAN_FOLDER = "/content/drive/MyDrive/GDFlix"
DEFAULT_DESTINATION = "/content/drive/MyDrive/MOVIES & WEB SERIES INFO"

def get_newest_archive(folder_path):
    if not os.path.exists(folder_path):
        raise FileNotFoundError(f"Folder not found: {folder_path}")
    
    valid_extensions = ('*.zip', '*.rar', '*.7z', '*.tar', '*.tgz')
    found_archives = []
    
    for ext in valid_extensions:
        found_archives.extend(glob.glob(os.path.join(folder_path, ext)))
        found_archives.extend(glob.glob(os.path.join(folder_path, ext.upper())))
    
    if not found_archives:
        raise FileNotFoundError(f"No archive files (.zip, .rar, .7z) found in {folder_path}")
    
    # Sort files by modified time (newest first)
    found_archives.sort(key=os.path.getmtime, reverse=True)
    return found_archives[0]

def extract_archive(archive_path, destination_path, delete_after=False, password=None):
    os.makedirs(destination_path, exist_ok=True)
    file_size_gb = os.path.getsize(archive_path) / (1024 ** 3)
    
    print("=" * 60)
    print(f"📦 Archive:   {os.path.basename(archive_path)}")
    print(f"📏 Size:      {file_size_gb:.2f} GB")
    print(f"📂 Extract To: {destination_path}")
    print("=" * 60)
    print("🚀 Extracting in Google Cloud... please wait...")

    # Install p7zip for maximum extraction speed across zip, rar, and 7z
    subprocess.run(["apt-get", "install", "-y", "-qq", "p7zip-full"], check=False)

    cmd = ["7z", "x", "-y", f"-o{destination_path}"]
    if password:
        cmd.append(f"-p{password}")
    else:
        cmd.append("-p-")
    cmd.append(archive_path)

    process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    
    for line in iter(process.stdout.readline, ''):
        clean = line.strip()
        if "%" in clean or "Extracting" in clean:
            sys.stdout.write(f"\r⏳ {clean[:80]}")
            sys.stdout.flush()

    process.wait()

    if process.returncode == 0:
        print("\n\n🎉 [SUCCESS] All files extracted successfully!")
        if delete_after:
            print(f"🗑️ Deleting original archive to free Drive storage...")
            os.remove(archive_path)
            print("✅ Archive deleted.")
    else:
        print(f"\n\n❌ Extraction failed with error code: {process.returncode}")

if __name__ == '__main__':
    # Auto-find newest file in GDFlix and extract
    try:
        newest_file = get_newest_archive(DEFAULT_SCAN_FOLDER)
        print(f"🎯 Target archive detected: {os.path.basename(newest_file)}")
        extract_archive(newest_file, DEFAULT_DESTINATION, delete_after=False)
    except Exception as err:
        print(f"\n⚠️ Error occurred: {err}")
