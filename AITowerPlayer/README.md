# AI Tower Player

**Pemain otomatis (bot) untuk game Tower Smash di Android.**

Aplikasi ini menggunakan **Accessibility Service** + **MediaProjection** + **Computer Vision** untuk:

1. Mendeteksi area hitam (bahaya) vs area aman di tower
2. Menahan / melepaskan sentuhan secara otomatis
3. Mengenali layar kemenangan (**TOWER SMASHED!**)
4. Menekan tombol Level berikutnya
5. Mengenali popup **ROYAL DECREE** dan menekan **SMASH!**
6. Mengulangi proses sampai level habis atau Anda menekan STOP

Tidak memerlukan root.

---

## Fitur

| Fitur | Keterangan |
|-------|------------|
| AI Gameplay | Deteksi area hitam real-time, hold/release otomatis |
| Auto Level | Win → Level button → Popup SMASH → lanjut level |
| Task Manager | Tambah/edit/hapus task klik kustom (koordinat X/Y) |
| Floating Control | Overlay Start/Stop saat game berjalan |
| Konfigurasi | Garis deteksi, sensitivitas hitam, FPS, delay animasi |
| Tanpa Root | Accessibility Service + MediaProjection resmi Android |

---

## Cara Build APK lewat GitHub (tanpa PC)

1. Buat repository baru di GitHub (misal `AITowerPlayer`)
2. Upload **semua isi folder project** ini ke repository (bisa lewat aplikasi GitHub di HP atau web)
3. Pastikan file `.github/workflows/build-apk.yml` ikut ter-upload
4. Buka tab **Actions** di repository
5. Pilih workflow **Build APK** → **Run workflow**
6. Setelah selesai (hijau), buka run tersebut → **Artifacts** → download **app-debug**
7. Extract ZIP → dapatkan `app-debug.apk`
8. Install di HP (aktifkan "Install from unknown sources")

Atau cukup **push ke branch main**, workflow akan jalan otomatis.

---

## Cara Pakai di HP

### 1. Install & Izin

1. Install APK
2. Buka **AI Tower Player**
3. Tekan **Aktifkan Accessibility** → cari **AI Tower Player** → nyalakan
4. Tekan **Minta Izin Screen Capture** → izinkan
5. (Opsional) Izinkan **Draw over other apps** untuk floating control

### 2. Kalibrasi Task (penting!)

Setiap HP punya resolusi berbeda. Buka **Task Manager**:

- **Level Button** – koordinat tombol "Level XX" di layar kemenangan  
  Default: X=540, Y=1600 (cocok untuk ~1080x2400)
- **SMASH Button** – koordinat tombol SMASH! di popup  
  Default: X=540, Y=1200

**Cara dapat koordinat akurat:**
- Screenshot layar kemenangan / popup
- Buka di aplikasi editor gambar / gallery yang menampilkan koordinat
- Atau gunakan aplikasi "Coordinate Finder" / "Pointer Location" (Developer Options → Pointer location)

Sesuaikan X/Y lalu simpan.

### 3. Pengaturan Deteksi

Buka **Pengaturan**:

| Parameter | Rekomendasi awal | Keterangan |
|-----------|------------------|------------|
| Garis Deteksi (Y %) | 30–40% | Posisi horizontal band yang dianalisis |
| Ambang Hitam | 35–50 | RGB max agar dianggap hitam |
| Ambang Piksel (%) | 20–30% | % pixel hitam di band = bahaya |
| Lebar Area Deteksi | 30–60 px | Tinggi band analisis |
| FPS Analisis | 12–18 | Semakin tinggi = lebih responsif, lebih boros baterai |
| Tunggu Animasi | 1200–2000 ms | Delay setelah menang sebelum klik Level |

### 4. Main

1. Buka game **Tower Smash**
2. Masuk ke level yang ingin dimainkan
3. Kembali ke **AI Tower Player** → tekan **START AI**
4. Segera kembali ke game (AI butuh 1–2 detik untuk mulai)
5. Biarkan AI bermain. Pantau log di aplikasi atau floating control.
6. Tekan **STOP** kapan saja untuk menghentikan.

---

## Struktur Project

```
AITowerPlayer/
├── app/
│   ├── src/main/
│   │   ├── java/com/aitowerplayer/
│   │   │   ├── AITowerApp.kt
│   │   │   ├── model/          # GameState, AppConfig, TaskItem
│   │   │   ├── vision/         # ScreenAnalyzer (CV)
│   │   │   ├── service/        # Accessibility, ScreenCapture, Floating
│   │   │   ├── ui/             # Main, Settings, TaskManager
│   │   │   └── util/           # ConfigManager, LogManager
│   │   ├── res/                # Layout, drawable, values
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── .github/workflows/build-apk.yml
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md
```

---

## Batasan & Catatan Teknis

1. **Koordinat task** harus dikalibrasi per resolusi HP. Default hanya perkiraan.
2. **Deteksi warna** bergantung pada tema visual game. Jika update game mengubah warna tower / UI, parameter deteksi mungkin perlu disesuaikan.
3. **Hold gesture** lewat Accessibility Service di beberapa OEM (Xiaomi, Oppo, Vivo) kadang perlu izin tambahan di pengaturan baterai / autostart.
4. **MediaProjection** hanya aktif selama service berjalan; jika sistem menghentikan service, AI berhenti.
5. OCR (ML Kit) opsional dan default **mati** untuk hemat resource. Deteksi win/popup utama memakai analisis warna area.
6. Aplikasi **tidak** menjamin 100% win rate — area hitam yang sangat tipis atau animasi cepat bisa lolos deteksi. Sesuaikan sensitivitas.
7. Gunakan dengan bijak. Automasi game dapat melanggar ToS game tertentu.

---

## Build Lokal (jika punya PC)

```bash
# Butuh JDK 17 + Android SDK
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

---

## Lisensi

Project ini dibuat untuk keperluan edukasi dan otomasi pribadi.  
Gunakan dengan tanggung jawab sendiri.

---

**AI Tower Player** — dari nol, siap di-upload ke GitHub, siap di-build APK.
