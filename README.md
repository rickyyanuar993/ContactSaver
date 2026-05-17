# 📱 Contact Saver — Panduan Lengkap

Aplikasi Android untuk import kontak massal dari file VCF/CSV **tanpa duplikat**.

---

## ✨ Fitur Aplikasi

- ✅ Import kontak dari file **VCF** (.vcf) dan **CSV** (.csv)
- ✅ Cek otomatis kontak yang sudah ada di HP
- ✅ Hanya simpan kontak yang **belum ada** (unik)
- ✅ Tampilkan ringkasan: total, duplikat, unik, tersimpan
- ✅ Support nomor Indonesia (format 08xx, +628xx, 628xx)

---

## 🚀 CARA BUILD APK (Tanpa Android Studio)

### LANGKAH 1 — Buat Akun GitHub
1. Buka https://github.com
2. Klik **Sign Up**, daftar gratis
3. Verifikasi email

---

### LANGKAH 2 — Upload Kode ke GitHub

1. Login ke GitHub
2. Klik tombol **"+"** di pojok kanan atas → **New repository**
3. Isi:
   - Repository name: `ContactSaver`
   - Pilih: **Public**
   - Centang: **Add a README file**
4. Klik **Create repository**

5. Di halaman repository, klik **"uploading an existing file"**
6. Upload semua folder/file yang ada di folder `ContactSaver` ini
   - Drag & drop seluruh isi folder
7. Klik **Commit changes**

---

### LANGKAH 3 — Setup GitHub Actions (Auto Build APK)

1. Di repository GitHub kamu, klik tab **"Actions"**
2. Klik **"set up a workflow yourself"**
3. Hapus semua isi yang ada, ganti dengan kode ini:

```yaml
name: Build APK

on:
  push:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Grant execute permission for gradlew
        run: chmod +x gradlew
      
      - name: Build Debug APK
        run: ./gradlew assembleDebug
      
      - name: Upload APK
        uses: actions/upload-artifact@v3
        with:
          name: ContactSaver-APK
          path: app/build/outputs/apk/debug/app-debug.apk
```

4. Klik **"Commit changes"**

---

### LANGKAH 4 — Download APK

1. Tunggu beberapa menit (lihat tab **Actions** — ada lingkaran kuning = sedang proses)
2. Setelah centang hijau ✅, klik workflow yang selesai
3. Di bagian bawah halaman, cari **"Artifacts"**
4. Klik **ContactSaver-APK** → file ZIP akan terdownload
5. Ekstrak ZIP → ada file **app-debug.apk**

---

### LANGKAH 5 — Install APK di HP

1. Pindahkan file APK ke HP Android kamu
2. Buka file APK di HP
3. Jika muncul peringatan "Install from unknown sources":
   - Buka **Pengaturan → Keamanan → Izinkan sumber tidak dikenal**
4. Tap **Install**
5. Selesai! 🎉

---

## 📂 Format File yang Didukung

### VCF (.vcf)
Export dari HP lain atau Google Contacts. Format standar.

### CSV (.csv)
Minimal 2 kolom dengan header:
```
Nama,Nomor
Budi Santoso,08123456789
Siti Rahayu,+6281234567890
```

Header yang dikenali otomatis: name, nama, phone, tel, hp, nomor

---

## ❓ FAQ

**Q: Apakah kontak yang sudah ada akan terhapus?**
A: Tidak. App hanya menambah, tidak menghapus.

**Q: Kontak disimpan ke mana?**
A: Ke kontak lokal HP (Account: Phone). Bisa sync ke Google manual.

**Q: Apakah aman?**
A: Ya. App tidak kirim data ke internet. Semua proses di HP kamu sendiri.

**Q: Berapa maksimal kontak yang bisa diimport?**
A: Tidak ada batas. 10.000 kontak pun bisa, hanya butuh waktu lebih lama.

---

## 🆘 Butuh Bantuan?

Jika ada error saat build, cek tab **Actions** di GitHub dan lihat pesan errornya.
