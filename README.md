# LAN Control 5

Kontrol 1 komputer utama + beberapa komputer klien Windows melalui LAN atau Tailscale:
layar, mouse/keyboard, kontrol serentak, transfer file, clipboard, restart/shutdown.
Koneksi terenkripsi AES-GCM dengan kode koneksi minimal 12 karakter.

## Unduh
Klik **Code → Download ZIP**. Di folder `rilis/`, ambil `LAN-Control-5-<versi>.jar`
yang terbaru (lihat `rilis/versi-terbaru.txt`), ganti namanya menjadi `LAN-Control-5.jar`,
dan simpan bersama file `.bat`. Butuh Java 17 atau lebih baru.

- Komputer utama: `Jalankan-Utama.bat`
- Klien: `Jalankan-Klien.bat` (atau `Jalankan-Klien-Admin.bat`)

Panduan lengkap: [PANDUAN.txt](PANDUAN.txt). Riwayat perubahan: `PEMBARUAN-v*.txt`.

## Pembaruan otomatis
Mulai v1.6.0 komputer utama memeriksa folder `rilis/` di repository ini, memverifikasi tanda tangan
Ed25519 setiap JAR, memasangnya saat tidak dipakai, lalu meneruskannya ke klien.
Hanya JAR yang ditandatangani kunci rilis pemilik yang diterima.

## Build dari sumber
`java Build.java` lalu `java -jar LAN-Control-5.jar --test`.
Cara menerbitkan versi: [CARA-RILIS.txt](CARA-RILIS.txt).
