# 🚀 Google Drive Cloud Unzipper (Native Android + Cloud Engine)

Extract gigabyte-sized archives (`.zip`, `.rar`, `.7z`) inside Google Drive with **0 MB phone storage** and **0 MB mobile data used**!

[![Open In Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/thechandrax/Cloud-Unzipper/blob/main/Cloud_Unzipper.ipynb)
[![Download APK](https://img.shields.io/badge/Download-Android%20APK%20v1.5.3-brightgreen?logo=android)](https://github.com/thechandrax/Cloud-Unzipper/releases/latest)

---

## 📱 Daily Usage: 3 Easy Steps

1. **Open Colab on your Phone or PC** (tap the **Open In Colab** badge above).
2. **Tap the Play (▶️) button**:
   - Google Drive will connect.
   - It will display your secure temporary link:
     ```
     👉 https://xxxx.trycloudflare.com 👈
     ```
3. **Open the Cloud Unzipper Android App**:
   - Tap the **Paste (📋)** button in the URL field.
   - Tap **START CLOUD EXTRACTION**!
   - All files will be extracted directly into your Google Drive in seconds.

---

## 📦 What is Inside This Repository?

- **`android-app/`**: Native Android app written in Kotlin & Jetpack Compose (Material 3, Cambria font, 1-tap clipboard paste, honest real-time progress).
- **`Cloud_Unzipper.ipynb`**: 1-Click Google Colab cloud engine with 7-Zip acceleration and Cloudflare tunnel.
- **`cloud-backend/`**: FastAPI cloud backend with multi-engine extraction (7-Zip / BSDTar).
- **`.github/workflows/build-apk.yml`**: GitHub Actions automated APK compilation.
