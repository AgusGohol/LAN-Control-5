import java.util.*;

/** v1.12.0: client can remember the connection code and auto-open/share on login. This covers the checkbox state mapping. */
class ClientStartupTests {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void run(){
        // {remember, rememberEnabled, autoStart, admin}
        check(Arrays.equals(LanControl.Agent.startupFlags(false,false,false),new boolean[]{false,true,false,false}),"fresh client: nothing checked, remember editable");
        check(Arrays.equals(LanControl.Agent.startupFlags(true,false,false),new boolean[]{true,true,false,false}),"Ingat kode only: remember on and still editable, no auto-start");
        check(Arrays.equals(LanControl.Agent.startupFlags(true,true,false),new boolean[]{true,false,true,false}),"login auto-start: remember forced on and locked, auto-start on, not admin");
        check(Arrays.equals(LanControl.Agent.startupFlags(true,false,true),new boolean[]{true,false,true,true}),"admin auto-start: remember locked on, auto-start on, admin on");
        check(Arrays.equals(LanControl.Agent.startupFlags(false,true,false),new boolean[]{true,false,true,false}),"auto-start always implies the code is kept");
        System.out.println("PASS: v1.12.0 client code memory & auto-start: checkbox state mapping (remember/lock/auto-start/admin) for every saved state.");
    }
}
