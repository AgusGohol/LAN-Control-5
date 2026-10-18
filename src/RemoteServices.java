import java.awt.*;
import java.awt.datatransfer.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Visible, explicitly enabled LAN administration in the logged-in user's session. */
class RemoteServices implements AutoCloseable {
    static final int CHUNK=256*1024,LIST_LIMIT=5000;
    final Map<String,Upload> uploads=new HashMap<>();
    record Upload(Path target,Path temporary,OutputStream output) {}
    /** Set by the client session: schedules install + restart after UPDATE_APPLY has been answered. */
    volatile Runnable onUpdate;
    static String b64(String s){return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));}
    static String un64(String s){return new String(Base64.getDecoder().decode(s),StandardCharsets.UTF_8);}
    static byte[] text(String s){return s.getBytes(StandardCharsets.UTF_8);}
    static Path path(String s)throws IOException{if(s.isBlank())throw new IOException("Isi alamat file/folder klien.");return Path.of(s).toAbsolutePath().normalize();}
    static void windows()throws IOException{if(!System.getProperty("os.name").toLowerCase().contains("win"))throw new IOException("Fitur ini memerlukan Windows.");}
    /** Synchronized: file RPCs run on a worker thread while close() may run on the session thread. */
    synchronized byte[] execute(String op,List<String> a)throws Exception {
        switch(op){
            case "LIST": {
                List<Path> paths;
                if(a.get(0).isBlank())paths=Arrays.stream(File.listRoots()).map(File::toPath).toList();
                else try(var stream=Files.list(path(a.get(0)))){paths=stream.sorted().limit(LIST_LIMIT).toList();}
                // Fields: path, type, size, modified epoch ms (4th field ignored by masters older than v1.5.0).
                StringBuilder b=new StringBuilder();for(Path p:paths){boolean dir=Files.isDirectory(p);long size=0,modified=0;try{if(!dir)size=Files.size(p);}catch(IOException ignored){}try{if(!a.get(0).isBlank())modified=Files.getLastModifiedTime(p).toMillis();}catch(IOException ignored){}b.append(b64(p.toString())).append('\t').append(dir?"D":"F").append('\t').append(size).append('\t').append(modified).append('\n');}return text(b.toString());
            }
            case "SIZE":return text(Long.toString(Files.size(path(a.get(0)))));
            case "GET": {
                Path p=path(a.get(0));long offset=Long.parseLong(a.get(1));int count=Integer.parseInt(a.get(2));
                if(offset<0||count<1||count>CHUNK)throw new IOException("Ukuran blok tidak valid.");
                try(RandomAccessFile f=new RandomAccessFile(p.toFile(),"r")){f.seek(offset);byte[] b=new byte[count];int n=f.read(b);return n<0?new byte[0]:Arrays.copyOf(b,n);}
            }
            case "PUT_BEGIN": {
                if(uploads.size()>=4)throw new IOException("Terlalu banyak transfer aktif.");
                Path target=path(a.get(0));if(Files.exists(target))throw new IOException("File tujuan sudah ada. Gunakan nama lain.");
                Path temporary=Files.createTempFile(target.getParent(),".lan5-",".part");String id=UUID.randomUUID().toString();
                uploads.put(id,new Upload(target,temporary,Files.newOutputStream(temporary)));return text(id);
            }
            case "PUT_CHUNK": {
                Upload u=uploads.get(a.get(0));if(u==null)throw new IOException("Transfer tidak ditemukan.");byte[] b=Base64.getDecoder().decode(a.get(1));if(b.length>CHUNK)throw new IOException("Blok terlalu besar.");u.output.write(b);return text("OK");
            }
            case "PUT_END": {
                Upload u=uploads.remove(a.get(0));if(u==null)throw new IOException("Transfer tidak ditemukan.");
                try{u.output.close();Files.move(u.temporary,u.target);}finally{Files.deleteIfExists(u.temporary);}return text("Tersimpan");
            }
            case "PUT_CANCEL": {Upload u=uploads.remove(a.get(0));if(u!=null){u.output.close();Files.deleteIfExists(u.temporary);}return text("Dibatalkan");}
            case "VERSION":return text(LanControl.VERSION);
            case "UPDATE_BEGIN": {
                String version=a.get(0);if(!Updater.newer(version))throw new IOException("Versi "+version+" tidak lebih baru dari klien v"+LanControl.VERSION+".");
                if(onUpdate==null)throw new IOException("Klien ini tidak dapat memulai ulang otomatis.");
                if(uploads.size()>=4)throw new IOException("Terlalu banyak transfer aktif.");
                Path target=Updater.staged();Files.createDirectories(target.getParent());Files.deleteIfExists(target);
                Path temporary=Files.createTempFile(target.getParent(),".lan5-",".part");String id=UUID.randomUUID().toString();
                uploads.put(id,new Upload(target,temporary,Files.newOutputStream(temporary)));return text(id);
            }
            case "UPDATE_APPLY": {
                String version=a.get(0),found=Updater.inspect(Updater.staged());
                if(found==null||!found.equals(version)||!Updater.newer(found))throw new IOException("File pembaruan tidak lengkap atau tidak valid.");
                Runnable apply=onUpdate;if(apply==null)throw new IOException("Klien ini tidak dapat memulai ulang otomatis.");
                apply.run();return text("Klien memasang v"+version+" dan mulai ulang.");
            }
            case "MKDIR":Files.createDirectory(path(a.get(0)));return text("Folder dibuat");
            case "RENAME":Files.move(path(a.get(0)),path(a.get(1)));return text("Nama diubah");
            case "DELETE":Files.delete(path(a.get(0)));return text("Dihapus (folder harus kosong)");
            case "OPEN":Desktop.getDesktop().open(path(a.get(0)).toFile());return text("Dibuka di klien");
            case "CLIP_GET":return text((String)Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor));
            case "CLIP_SET":Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(a.get(0)),null);return text("Teks clipboard klien diperbarui");
            case "LOCK":windows();new ProcessBuilder("rundll32.exe","user32.dll,LockWorkStation").start();return text("Permintaan kunci dikirim");
            case "RESTART":case "SHUTDOWN":windows();new ProcessBuilder("shutdown.exe",op.equals("RESTART")?"/r":"/s","/t","30").start();return text("Dijadwalkan 30 detik. Batalkan dengan tombol Batal shutdown.");
            case "CANCEL_POWER":windows();new ProcessBuilder("shutdown.exe","/a").start();return text("Permintaan pembatalan dikirim");
            default:throw new IOException("Perintah tidak dikenal.");
        }
    }
    public synchronized void close(){for(Upload u:uploads.values())try{u.output.close();Files.deleteIfExists(u.temporary);}catch(IOException ignored){}uploads.clear();}

    static class Startup {
        static final String IDENTITY="$id=[Security.Principal.WindowsIdentity]::GetCurrent();$sid=$id.User.Value;$task='LANControl5-Client-'+$sid;";
        static boolean isAdmin()throws Exception{return powershell("$p=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent());[Console]::Write($p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator))","").equalsIgnoreCase("True");}
        static Path jar()throws Exception {Path p=Path.of(LanControl.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();if(!Files.isRegularFile(p)||!p.toString().endsWith(".jar"))throw new IOException("Jalankan JAR dari folder permanen yang sudah diekstrak.");return p;}
        static Path javaw()throws Exception {Path p=Path.of(System.getProperty("java.home"),"bin","javaw.exe");if(!Files.exists(p))throw new IOException("javaw.exe tidak ditemukan.");return p;}
        static String elevationScript(Path executable,Path jar){return "Start-Process -FilePath "+literal(executable.toString())+" -ArgumentList "+literal("-jar \""+jar+"\" client")+" -WorkingDirectory "+literal(jar.getParent().toString())+" -Verb RunAs";}
        static void elevate()throws Exception {windows();powershell(elevationScript(javaw(),jar()),"",180);}
        static void removeTask()throws Exception {
            powershell(IDENTITY+"$t=Get-ScheduledTask -TaskName $task -ErrorAction SilentlyContinue;if($null -ne $t){Unregister-ScheduledTask -TaskName $task -Confirm:$false}","");
        }
        static String taskScript(Path executable,Path jar){return IDENTITY+
            "$a=New-ScheduledTaskAction -Execute "+literal(executable.toString())+" -Argument "+literal("-jar \""+jar+"\" client --auto")+" -WorkingDirectory "+literal(jar.getParent().toString())+";"+
            "$t=New-ScheduledTaskTrigger -AtLogOn -User $sid;"+
            "$p=New-ScheduledTaskPrincipal -UserId $sid -LogonType Interactive -RunLevel Highest;"+
            "$s=New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero);"+
            "Register-ScheduledTask -TaskName $task -Action $a -Trigger $t -Principal $p -Settings $s -Description 'LAN Control 5: klien terlihat saat login, izin administrator disetujui lokal' -Force | Out-Null";
        }
        static void enableAdmin(String password)throws Exception {
            windows();if(!isAdmin())throw new IOException("Tutup klien, buka Jalankan-Klien-Admin.bat dan setujui UAC di komputer klien dahulu.");
            enable(password);powershell(taskScript(javaw(),jar()),"");Files.deleteIfExists(shortcut());
        }
        static Path config(){return Path.of(System.getenv("APPDATA"),"LANControl5","client.properties");}
        static Path shortcut(){return Path.of(System.getenv("APPDATA"),"Microsoft","Windows","Start Menu","Programs","Startup","LAN Control 5 Klien.lnk");}
        static String literal(String s){return "'"+s.replace("'","''")+"'";}
        static String powershell(String script,String input)throws Exception{return powershell(script,input,20);}
        static String powershell(String script,String input,int timeout)throws Exception{
            windows();String encoded=Base64.getEncoder().encodeToString(("$ErrorActionPreference='Stop';"+script).getBytes(StandardCharsets.UTF_16LE));
            Process p=new ProcessBuilder("powershell.exe","-NoProfile","-NonInteractive","-EncodedCommand",encoded).start();
            try(OutputStream out=p.getOutputStream()){out.write(input.getBytes(StandardCharsets.UTF_8));}
            // Bound execution and drain both pipes without displaying secret-bearing error text.
            var stdout=LanControl.WORK.submit(()->p.getInputStream().readAllBytes());
            var stderr=LanControl.WORK.submit(()->p.getErrorStream().readAllBytes());
            if(!p.waitFor(timeout,TimeUnit.SECONDS)){p.destroyForcibly();throw new IOException("Pengaturan Windows melewati batas waktu.");}
            byte[] result=stdout.get(5,TimeUnit.SECONDS);stderr.get(5,TimeUnit.SECONDS);
            if(p.exitValue()!=0)throw new IOException("Pengaturan Windows gagal. Pastikan PowerShell tersedia dan folder aplikasi dapat diakses.");
            return new String(result,StandardCharsets.UTF_8).trim();
        }
        static String protect(String password,boolean encrypt)throws Exception {
            String script="Add-Type -AssemblyName System.Security;$b=[Convert]::FromBase64String([Console]::In.ReadToEnd());$r=[Security.Cryptography.ProtectedData]::"+(encrypt?"Protect":"Unprotect")+"($b,$null,[Security.Cryptography.DataProtectionScope]::CurrentUser);[Console]::Write([Convert]::ToBase64String($r))";
            return powershell(script,password);
        }
        static void enable(String password)throws Exception {
            windows();if(password.length()<12)throw new IOException("Gunakan kode minimal 12 karakter.");
            removeTask();
            Path jar=Path.of(LanControl.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
            if(!Files.isRegularFile(jar)||!jar.toString().endsWith(".jar"))throw new IOException("Jalankan JAR dari folder permanen yang sudah diekstrak.");
            Path javaw=Path.of(System.getProperty("java.home"),"bin","javaw.exe");if(!Files.exists(javaw))throw new IOException("javaw.exe tidak ditemukan.");
            Properties props=new Properties();props.setProperty("code",protect(b64(password),true));Files.createDirectories(config().getParent());
            Path temp=Files.createTempFile(config().getParent(),"settings-",".tmp");try(OutputStream out=Files.newOutputStream(temp)){props.store(out,"LAN Control 5: Windows DPAPI CurrentUser");}
            Files.move(temp,config(),StandardCopyOption.REPLACE_EXISTING);
            try{powershell("$s=(New-Object -ComObject WScript.Shell).CreateShortcut("+literal(shortcut().toString())+");$s.TargetPath="+literal(javaw.toString())+";$s.Arguments="+literal("-jar \""+jar+"\" client --auto")+";$s.WorkingDirectory="+literal(jar.getParent().toString())+";$s.Description='LAN Control 5 - klien terlihat saat login';$s.Save()","");}
            catch(Exception e){Files.deleteIfExists(config());throw e;}
        }
        static String load()throws Exception{windows();Properties p=new Properties();try(InputStream in=Files.newInputStream(config())){p.load(in);}return un64(protect(p.getProperty("code"),false));}
        static void disable()throws Exception{windows();removeTask();Files.deleteIfExists(shortcut());Files.deleteIfExists(config());}
    }
}
