package android.os;
/** Synchronous notification dispatcher for host tests, not an Android Looper emulator. */
public class Handler { public boolean post(Runnable r){r.run();return true;} }
