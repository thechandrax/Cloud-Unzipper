# 🚀 Google Drive Cloud Unzipper (Native Android + Cloud Engine)

Extract gigabyte-sized archives (`.zip`, `.rar`, `.7z`) inside Google Drive with **0 MB phone storage** and **0 MB mobile data used**!

---

## 📦 What is Inside This Repository?

- **`android-app/`**: Full native Android App written in Kotlin & Jetpack Compose (Material 3).
- **`cloud-backend/`**: FastAPI cloud worker with high-speed multi-core `7z` extraction engine and live SSE progress streaming.
- **`.github/workflows/build-apk.yml`**: Automatic cloud builder that creates the `.apk` on GitHub without needing Android Studio installed.
- **`Drive_Cloud_Unzipper.ipynb`**: Interactive Colab notebook edition with mobile forms and Gradio web interface.

---

## ⚡ How to Get the Android APK in 3 Steps (Zero Coding)

1. **Upload or Push this folder to GitHub**:
   - Create a free new repository on [GitHub](https://github.com/new).
   - Push or upload this project into the repository.

2. **Wait 2 minutes for GitHub to build your APK**:
   - Go to the **Actions** tab on your GitHub repository.
   - You will see the **"Build Android APK"** workflow running.
   - When it turns green with a checkmark (✅), click on the completed run.

3. **Download & Install on your Phone**:
   - Under the **Artifacts** section at the bottom, download **`CloudUnzipper-Debug-APK`**.
   - Open the `.apk` on your Android phone and tap **Install**!

---

## ☁️ How to Run the Free Cloud Worker

Deploy the `/cloud-backend` to any free Python container host (Render, Railway, or Hugging Face Spaces):
1. Create a free account on [Render.com](https://render.com).
2. Connect your GitHub repository and select the `cloud-backend` directory.
3. Render will deploy it automatically and provide a URL like:
   `https://my-unzipper.onrender.com`
4. In your Android app, your extraction requests will route through this high-speed cloud worker.
