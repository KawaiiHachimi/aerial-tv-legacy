package com.stv.launcher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

/**
 * The firmware's T2launcher entry point, kept for compatibility.
 *
 * <p>The original {@code com.stv.launcher.Launcher} was a shell that hard-coded a jump to a
 * now-removed third-party launcher. This APK keeps the package name and the vendor
 * {@code com.stv.launcher.action.start} action, but sends every entry to the Aerial desktop and
 * never declares HOME, so it cannot steal the default home or appear in Aerial's grid.
 */
public final class Launcher extends Activity {

    private static final String TAG = "T2launcherBridge";
    private static final String AERIAL_PACKAGE = "app.aerial.legacy";
    private static final String AERIAL_ACTIVITY = "app.aerial.legacy.MainActivity";

    private boolean started;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        started = openAerial();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!started) {
            started = true;
            openAerial();
        }
        finish();
    }

    private boolean openAerial() {
        Intent aerial = new Intent(Intent.ACTION_MAIN);
        aerial.setClassName(AERIAL_PACKAGE, AERIAL_ACTIVITY);
        aerial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (start(aerial)) {
            return true;
        }
        Log.w(TAG, "Aerial desktop missing, falling back to Settings");
        Intent settings = new Intent(Settings.ACTION_SETTINGS);
        settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (start(settings)) {
            return true;
        }
        // Last resort: ask the system for whatever HOME is now. Never crash-loop.
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        start(home);
        return false;
    }

    private boolean start(Intent intent) {
        try {
            startActivity(intent);
            return true;
        } catch (RuntimeException missing) {
            Log.w(TAG, "cannot start " + intent, missing);
            return false;
        }
    }
}
