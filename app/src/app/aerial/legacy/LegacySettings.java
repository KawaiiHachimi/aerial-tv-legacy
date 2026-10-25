package app.aerial.legacy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.util.Log;

/** The first four categories of EUI's eight-item settings panel. */
final class LegacySettings {
  static boolean open(Context context, int section) {
    if (section < 0 || section > 3) return false;
    // Picture and sound are private dialogs: use the vendor's main panel as the fallback.
    String action = section == 2 ? "com.letv.action.source"
        : section == 3 ? "com.letv.action.network" : "com.letv.action.setting";
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
