package app.aerial.legacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;

/** EUI's signal-source and network panels. */
final class LegacySettings {
  static boolean open(Context context, int section) {
    if (section != 2 && section != 3) return false;
    String action = section == 2 ? "com.letv.action.source"
        : "com.letv.action.network";
    ComponentName receiver = new ComponentName("com.stv.t2.globalsetting",
        "com.stv.t2.globalsetting.GlobalReceiver");
    try {
      ActivityInfo info = context.getPackageManager().getReceiverInfo(receiver, 0);
      if (!info.enabled || !info.exported) return false;
      context.sendBroadcast(new Intent(action).setComponent(receiver));
      return true;
    } catch (android.content.pm.PackageManager.NameNotFoundException genericAndroid) {
      return false;
    } catch (RuntimeException unavailable) {
      Log.w("AerialSettings", "EUI settings unavailable", unavailable);
      return false;
    }
  }
}
