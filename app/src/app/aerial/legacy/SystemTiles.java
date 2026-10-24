package app.aerial.legacy;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Aerial system palette: midpoint of each upstream gradient as one solid color; no added shadows. */
final class SystemTiles {
  enum Kind {
    SETTINGS(0xFF6B6B6E, AerialGlyphs.SETTINGS),
    PHOTOS(0xFFFF883D, AerialGlyphs.IMAGE),
    MEDIA(0xFF252527, AerialGlyphs.PLAY_CIRCLE),
    STORE(0xFF2587EB, AerialGlyphs.SHOPPING_BAG),
    WEATHER(0xFF3B9BE8, AerialGlyphs.CLOUD),
    CALENDAR(0xFFE74A3E, AerialGlyphs.CALENDAR_MONTH),
    CAST(0xFF6A68E7, AerialGlyphs.CAST),
    SECURITY(0xFF28B160, AerialGlyphs.VERIFIED_USER),
    BROWSER(0xFF37A6F4, AerialGlyphs.LANGUAGE),
    HOME(0xFF5957D1, AerialGlyphs.HOME),
    KIDS(0xFFEF6A96, AerialGlyphs.KIDS),
    FILES(0xFF2587EB, AerialGlyphs.FOLDER),
    INPUTS(0xFF2C54AF, AerialGlyphs.INPUT);
    final int color;
    final String glyph;
    Kind(int color, String glyph) { this.color=color; this.glyph=glyph; }
  }
  private static final Map<String, Kind> PACKAGES = new HashMap<String, Kind>();
  static {
    PACKAGES.put("com.android.settings", Kind.SETTINGS);
    PACKAGES.put("com.stv.t2.globalsetting", Kind.SETTINGS);
    PACKAGES.put("mstar.tvsetting.ui", Kind.SETTINGS);
    PACKAGES.put("com.xiaomi.mitv.settings", Kind.SETTINGS);
    PACKAGES.put("com.tcl.settings", Kind.SETTINGS);
    PACKAGES.put("com.android.gallery3d", Kind.PHOTOS);
    PACKAGES.put("com.android.gallery", Kind.PHOTOS);
    PACKAGES.put("com.mitv.gallery", Kind.PHOTOS);
    PACKAGES.put("com.tcl.tvsmartalbum", Kind.PHOTOS);
    PACKAGES.put("com.stv.videoplayer", Kind.MEDIA);
    PACKAGES.put("com.xiaomi.mitv.mediaexplorer", Kind.MEDIA);
    PACKAGES.put("com.tcl.ui_mediaCenter", Kind.MEDIA);
    PACKAGES.put("com.xiaomi.mitv.appstore", Kind.STORE);
    PACKAGES.put("com.tcl.appmarket2", Kind.STORE);
    PACKAGES.put("com.android.vending", Kind.STORE);
    PACKAGES.put("com.xiaomi.tweather", Kind.WEATHER);
    PACKAGES.put("com.tcl.common.weather", Kind.WEATHER);
    PACKAGES.put("com.android.calendar", Kind.CALENDAR);
    PACKAGES.put("com.xiaomi.mitv.calendar", Kind.CALENDAR);
    PACKAGES.put("com.stv.smartControl", Kind.CAST);
    PACKAGES.put("com.stv.miracast", Kind.CAST);
    PACKAGES.put("com.xiaomi.mitv.smartshare", Kind.CAST);
    PACKAGES.put("com.tcl.MultiScreenInteraction_TV", Kind.CAST);
    PACKAGES.put("com.xiaomi.mitv.tvmanager", Kind.SECURITY);
    PACKAGES.put("com.tcl.guard", Kind.SECURITY);
    PACKAGES.put("com.android.browser", Kind.BROWSER);
    PACKAGES.put("com.tcl.browser", Kind.BROWSER);
    PACKAGES.put("com.stv.filemanager", Kind.FILES);
    PACKAGES.put("com.android.documentsui", Kind.FILES);
    PACKAGES.put("com.stv.signalsourcemanager", Kind.INPUTS);
  }
  static Kind of(String pkg) { return PACKAGES.get(pkg); }
  // Use actual manifest categories, never app-label guesses. Only applied to system applications.
  static Map<String, Kind> categories(PackageManager pm) {
    Map<String, Kind> result = new HashMap<String, Kind>();
    String[] categories = {"android.intent.category.APP_GALLERY", "android.intent.category.APP_CALENDAR",
        "android.intent.category.APP_BROWSER", "android.intent.category.APP_MARKET", "android.intent.category.APPSTORE_LAUNCHER"};
    Kind[] kinds = {Kind.PHOTOS, Kind.CALENDAR, Kind.BROWSER, Kind.STORE, Kind.STORE};
    for (int i=0;i<categories.length;i++)
      query(pm, new Intent(Intent.ACTION_MAIN).addCategory(categories[i]), kinds[i], result);
    query(pm, new Intent("android.settings.SETTINGS"), Kind.SETTINGS, result);
    query(pm, new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), Kind.HOME, result);
    return result;
  }
  private static void query(PackageManager pm, Intent intent, Kind kind, Map<String, Kind> result) {
    try {
      List<ResolveInfo> list=pm.queryIntentActivities(intent, 0);
      if(list!=null) for(ResolveInfo info:list) if(info.activityInfo!=null)
        result.put(info.activityInfo.packageName, kind);
    } catch(RuntimeException ignored) { }
  }
  static void background(Canvas c, int w, int h, int color) {
    Paint p = new Paint(); p.setColor(color); c.drawRect(0, 0, w, h, p);
  }
  static boolean draw(Canvas c, String pkg, int w, int h) { return draw(c, of(pkg), w, h); }
  static boolean draw(Canvas c, Kind kind, int w, int h) {
    if(kind==null) return false;
    background(c,w,h,kind.color);
    float size=h*.58f, x=(w-size)/2, y=(h-size)/2;
    Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setColor(Color.WHITE);
    if(kind==Kind.WEATHER) {
      // Match the upstream two-layer sun/cloud rather than tinting the entire weather glyph white.
      p.setColor(0xFFFFD60A);
      AerialGlyphs.draw(c,AerialGlyphs.LIGHT_MODE,x-size*.14f+size*.11f,y-size*.15f+size*.11f,size*.78f,p);
      p.setColor(Color.WHITE);
      AerialGlyphs.draw(c,AerialGlyphs.CLOUD,x+size*.1f+size*.07f,y+size*.12f+size*.07f,size*.86f,p);
    } else AerialGlyphs.draw(c,kind.glyph,x,y,size,p);
    return true;
  }
}
