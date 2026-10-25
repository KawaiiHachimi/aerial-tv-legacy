package app.aerial.legacy;

import android.util.Log;
import java.lang.reflect.Method;

/** Optional EUI protocol. All vendor classes stay on the device; generic API18 is supported. */
final class LegacyDesktop {
  static void initialize(android.content.Context context, boolean shown) {
    try {
      Class<?> api = Class.forName("letv.desktop.DesktopManager");
      Object manager = api.getMethod("get").invoke(null);
      initializeArray(api, manager, "AllDesktopTags", new String[] {"signal", "app"});
      initializeArray(api, manager, "AllDesktopNames", new String[] {context.getString(R.string.cc_inputs), context.getString(R.string.desktop_home)});
      initializeArray(api, manager, "EnabledDesktopTags", new String[] {"signal", "app"});
      initializeArray(api, manager, "EnabledDesktopNames", new String[] {context.getString(R.string.cc_inputs), context.getString(R.string.desktop_home)});
      initializeArray(api, manager, "LockedDesktopTags", new String[0]);
      // We own this two-desktop configuration. Refresh its display names after a locale/update change.
      String[] tags = (String[]) api.getMethod("getEnabledDesktopTags").invoke(manager);
      if (java.util.Arrays.equals(tags, new String[] {"signal", "app"})) {
        String[] names = {context.getString(R.string.cc_inputs), context.getString(R.string.desktop_home)};
        api.getMethod("setEnabledDesktopNames", String[].class).invoke(manager, (Object) names);
        api.getMethod("setAllDesktopNames", String[].class).invoke(manager, (Object) names);
      }
      Object main = api.getMethod("getMainDesktopTag").invoke(manager);
      if (main == null || "".equals(main) || java.util.Arrays.equals(tags, new String[] {"signal", "app"}))
        api.getMethod("setMainDesktopTag", String.class).invoke(manager, "app");
      if (shown) {
        api.getMethod("setCurrentDesktopScreen", String.class).invoke(manager, "app");
        api.getMethod("setCurrentDesktopVisibility", boolean.class).invoke(manager, true);
      }
    } catch (ClassNotFoundException genericAndroid) {
      // The LeTV extension is absent on other Android devices.
    } catch (Exception unavailable) {
      Log.w("AerialInputs", "EUI desktop initialization failed", unavailable);
    } catch (LinkageError unavailable) {
      Log.w("AerialInputs", "EUI desktop API unavailable", unavailable);
    }
  }
  static void onHidden() {
    try {
      Class<?> api = Class.forName("letv.desktop.DesktopManager");
      Object manager = api.getMethod("get").invoke(null);
      api.getMethod("setCurrentDesktopVisibility", boolean.class).invoke(manager, false);
    } catch (ClassNotFoundException genericAndroid) {
      // Generic API18 device.
    } catch (Exception unavailable) {
      Log.w("AerialInputs", "Cannot mark EUI desktop hidden", unavailable);
    } catch (LinkageError unavailable) {
      Log.w("AerialInputs", "EUI desktop API unavailable", unavailable);
    }
  }
  private static void initializeArray(Class<?> api, Object manager, String suffix, String[] value)
      throws Exception {
    Method getter = api.getMethod("get" + suffix);
    if (getter.invoke(manager) == null)
      api.getMethod("set" + suffix, String[].class).invoke(manager, (Object) value);
  }
}
