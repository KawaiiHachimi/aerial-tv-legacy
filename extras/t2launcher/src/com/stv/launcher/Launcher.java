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
 * accepts package-scoped HOME requests at a lower priority than Aerial.
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
        if ("signal".equals(getIntent().getStringExtra("toDesktopName"))) {
            Intent signal = new Intent(Intent.ACTION_MAIN);
            signal.setClassName("com.stv.signalsourcemanager",
                "com.stv.signalsourcemanager.MainActivity");
            if (getIntent().getExtras() != null) signal.putExtras(getIntent().getExtras());
            signal.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            if (start(signal)) return true;
        }
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
        // No implicit HOME fallback: this bridge itself now accepts HOME.

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
