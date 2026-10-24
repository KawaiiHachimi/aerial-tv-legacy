package app.aerial.legacy;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

final class NetworkStatus {
  static final int OFFLINE = 0, WIFI = 1, ETHERNET = 2;
  static int read(Context context) {
    try {
      ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
      NetworkInfo n = cm == null ? null : cm.getActiveNetworkInfo();
      if (n != null && n.isConnected()) {
        if (n.getType() == ConnectivityManager.TYPE_ETHERNET) return ETHERNET;
        if (n.getType() == ConnectivityManager.TYPE_WIFI) return WIFI;
      }
    } catch (RuntimeException ignored) { }
    return OFFLINE;
  }
  static String glyph(int kind) {
    return kind == ETHERNET ? AerialGlyphs.LAN : kind == WIFI ? AerialGlyphs.WIFI : AerialGlyphs.WIFI_OFF;
  }
  static int label(int kind) {
    return kind == ETHERNET ? R.string.network_ethernet : kind == WIFI ? R.string.network_wifi : R.string.network_offline;
  }
}
