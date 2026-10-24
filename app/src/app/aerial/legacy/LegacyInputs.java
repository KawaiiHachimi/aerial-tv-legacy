package app.aerial.legacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * Legacy LeTV input manager declares MAIN+MONKEY, not LAUNCHER; package launch lookup misses it.
 */
final class LegacyInputs {
  static Intent letvIntent() {
    return new Intent(Intent.ACTION_MAIN)
        .setComponent(new ComponentName(
            "com.stv.signalsourcemanager", "com.stv.signalsourcemanager.MainActivity"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
  }
  static boolean open(Context context) {
    LegacyDesktop.initialize(context, false);
    Intent intent = letvIntent();
    try {
      context.getPackageManager().getActivityInfo(intent.getComponent(), 0);
      context.startActivity(intent);
      return true;
    } catch (PackageManager.NameNotFoundException absent) {
      return false;
    } catch (RuntimeException failed) {
      Log.w("AerialInputs", "Cannot open native input manager", failed);
      return false;
    }
  }
}
