import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import javax.imageio.ImageIO;

/**
 * Saat UAC / secure desktop aktif, tangkapan layar biasa (Robot)
 * hanya menghasilkan layar hitam. Kelas ini menjalankan helper SYSTEM (sdhelper.exe) yang
 * memotret secure desktop dan mengalirkannya lewat loopback; frame-nya dipakai menggantikan
 * layar hitam sehingga komputer utama ikut melihat UAC. Bila helper tidak ada / klien bukan
 * Administrator, kelas ini diam saja (perilaku kembali seperti semula).
 */
class SecureDesktop {
    static final int PORT = 45972;
    private static volatile BufferedImage latest;
    private static volatile long latestAt;
    private static volatile boolean running;
    private static Thread worker;
    private static volatile OutputStream out;      // saluran kirim input ke helper (4b)
    private static volatile int lastAx, lastAy;    // posisi mouse absolut terakhir (0..65535)

    /** Apakah secure desktop sedang aktif (frame UAC tersedia). Dipakai klien untuk mengarahkan input. */
    static boolean active() { return current() != null; }

    private static int norm(double n) { if (n < 0) n = 0; if (n > 1) n = 1; return (int) (n * 65535); }
    /** Gerakkan kursor ke posisi ternormalisasi (0..1) di secure desktop. */
    static void move(double nx, double ny) { int ax = norm(nx), ay = norm(ny); lastAx = ax; lastAy = ay; send(mouse(ax, ay, (byte) 0)); }
    /** Tekan/lepas tombol kiri di posisi terakhir. */
    static void button(boolean down) { send(mouse(lastAx, lastAy, (byte) (down ? 1 : 2))); }
    private static byte[] mouse(int ax, int ay, byte action) {
        return new byte[]{1, (byte) ax, (byte) (ax >> 8), (byte) (ax >> 16), (byte) (ax >> 24),
                (byte) ay, (byte) (ay >> 8), (byte) (ay >> 16), (byte) (ay >> 24), action};
    }
    private static synchronized void send(byte[] b) {
        OutputStream o = out;
        if (o == null) return;
        try { o.write(b); o.flush(); } catch (Exception ignored) {}
    }

    /** Frame secure desktop terkini bila UAC sedang aktif & frame masih baru; selain itu null. */
    static BufferedImage current() {
        BufferedImage im = latest;
        if (running && im != null && System.currentTimeMillis() - latestAt < 1500) return im;
        return null;
    }

    static synchronized void start() {
        if (running) return;
        running = true;
        worker = new Thread(SecureDesktop::loop, "secure-desktop");
        worker.setDaemon(true);
        worker.start();
    }

    static synchronized void stop() {
        running = false;
        latest = null;
        if (worker != null) worker.interrupt();
        worker = null;
    }

    private static Path helperExe() {
        try { return RemoteServices.Startup.jar().getParent().resolve("sdhelper.exe"); }
        catch (Exception e) { return null; }
    }

    private static void loop() {
        Path exe = helperExe();
        if (exe == null || !Files.exists(exe)) return;              // helper belum dipasang -> diam
        try { if (!RemoteServices.Startup.isAdmin()) return; } catch (Exception e) { return; } // perlu admin
        while (running) {
            Socket s = tryConnect(400);
            if (s == null) { spawn(exe); s = tryConnect(6000); }
            if (s == null) { sleepQuiet(1000); continue; }
            stream(s);
            latest = null;
            sleepQuiet(500);
        }
        latest = null;
    }

    private static void spawn(Path exe) {
        try { new ProcessBuilder(exe.toString(), "--spawn").start().waitFor(); }
        catch (Exception ignored) {}
    }

    private static Socket tryConnect(int totalMs) {
        long end = System.currentTimeMillis() + totalMs;
        while (running && System.currentTimeMillis() < end) {
            try {
                Socket s = new Socket();
                s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), PORT), 400);
                s.setSoTimeout(5000);
                return s;
            } catch (Exception e) { sleepQuiet(250); }
        }
        return null;
    }

    private static void stream(Socket s) {
        try (Socket sock = s) {
            out = sock.getOutputStream();
            DataInputStream in = new DataInputStream(new BufferedInputStream(sock.getInputStream()));
            while (running) {
                int nameLen = readIntLE(in);
                if (nameLen < 0 || nameLen > 1024) break;
                byte[] nb = new byte[nameLen]; in.readFully(nb);
                String name = new String(nb, "UTF-8");
                int fl = readIntLE(in);
                if (fl < 0 || fl > 50_000_000) break;
                byte[] frame = new byte[fl];
                if (fl > 0) in.readFully(frame);
                boolean win = name.toLowerCase().contains("winlogon");
                if (win && fl > 0) {
                    BufferedImage im = ImageIO.read(new ByteArrayInputStream(frame));
                    if (im != null) { latest = im; latestAt = System.currentTimeMillis(); }
                } else if (!win) {
                    latest = null;
                }
            }
        } catch (Exception ignored) {}
        finally { out = null; }
    }

    /** 32-bit little-endian, sesuai C# BitConverter di Windows. */
    private static int readIntLE(DataInputStream in) throws IOException {
        int b0 = in.read(), b1 = in.read(), b2 = in.read(), b3 = in.read();
        if ((b0 | b1 | b2 | b3) < 0) throw new EOFException();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static void sleepQuiet(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
