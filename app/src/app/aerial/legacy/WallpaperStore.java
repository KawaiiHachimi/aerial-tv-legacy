package app.aerial.legacy;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bounded image cache. Network, decoding and crop work always run outside the UI thread. */
final class WallpaperStore {
  private final Context context;
  private final SharedPreferences prefs;
  private final Handler main;
  private final Runnable changed;
  private volatile boolean closed, fetching;
  private long lastAttempt;
  private final LegacyHttp http;
  WallpaperStore(Context c, SharedPreferences p, Handler h, Runnable callback, LegacyHttp http) {
    context = c.getApplicationContext(); prefs = p; main = h; changed = callback; this.http = http;
  }
  String source() { return prefs.getString("wallpaper_source", "builtin"); }
  File image(String source) { return new File(context.getFilesDir(), "wallpaper-" + source + ".jpg"); }
  boolean hasPhoto() { return image("photo").isFile(); }
  String caption() { return prefs.getString("bing_caption", ""); }
  void close() { closed = true; }
  void select(String source, int preset) {
    if (!source.equals("builtin") && !source.equals("bing") && !source.equals("photo"))
      throw new IllegalArgumentException("Unknown wallpaper source");
    if (preset < 0 || preset >= Gradients.COUNT) throw new IllegalArgumentException("Unknown preset");
    if (source.equals("photo") && !hasPhoto()) throw new IllegalArgumentException("Upload a photo first");
    prefs.edit().putString("wallpaper_source", source).putInt("wallpaper", preset).apply();
    notifyChanged();
    if (source.equals("bing")) refresh(false);
  }
  private void notifyChanged() {
    main.post(new Runnable() { @Override public void run() { if (!closed) changed.run(); } });
  }
  Bitmap render(int preset, int w, int h) {
    String source = source();
    if (!source.equals("builtin")) {
      Bitmap bitmap = decode(image(source));
      if (bitmap != null) {
        Bitmap target = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        float scale = Math.max((float) w / bitmap.getWidth(), (float) h / bitmap.getHeight());
        float bw = bitmap.getWidth() * scale, bh = bitmap.getHeight() * scale;
        new Canvas(target).drawBitmap(bitmap, null,
            new RectF((w-bw)/2, (h-bh)/2, (w+bw)/2, (h+bh)/2), new Paint(3));
        bitmap.recycle(); return target;
      }
    }
    return Gradients.render(preset, w, h);
  }
  Bitmap preview(String source) {
    Bitmap original = decode(image(source));
    if (original == null) return null;
    Bitmap scaled = Bitmap.createScaledBitmap(original, 240, 135, true);
    if (scaled != original) original.recycle();
    return scaled;
  }
  private Bitmap decode(File file) {
    if (!file.isFile()) return null;
    try {
      BitmapFactory.Options opts = new BitmapFactory.Options(); opts.inJustDecodeBounds = true;
      BitmapFactory.decodeFile(file.getPath(), opts);
      if (opts.outWidth <= 0 || opts.outHeight <= 0 ||
          (long) opts.outWidth * opts.outHeight > 100000000L) return null;
      opts.inSampleSize = 1;
      while (opts.outWidth / opts.inSampleSize > 1920 || opts.outHeight / opts.inSampleSize > 1920)
        opts.inSampleSize *= 2;
      opts.inJustDecodeBounds = false;
      return BitmapFactory.decodeFile(file.getPath(), opts);
    } catch (RuntimeException invalid) { return null; }
      catch (OutOfMemoryError tooLarge) { return null; }
  }
  // Normalize an upload before replacing the last good image; malformed bodies never replace it.
  synchronized void upload(byte[] bytes) throws IOException {
    File temp = new File(context.getCacheDir(), "wallpaper-upload.tmp");
    try {
      write(temp, bytes);
      Bitmap bitmap = decode(temp);
      if (bitmap == null) throw new IOException("Choose a JPEG, PNG or WebP image");
      try {
        File ready = new File(context.getFilesDir(), "wallpaper-photo.tmp");
        FileOutputStream out = new FileOutputStream(ready);
        try { if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)) throw new IOException("Image encoding failed"); }
        finally { out.close(); }
        if (!ready.renameTo(image("photo"))) throw new IOException("Unable to save photo");
      } finally { bitmap.recycle(); }
      prefs.edit().putString("wallpaper_source", "photo").apply(); notifyChanged();
    } finally { temp.delete(); }
  }
  synchronized void refresh(boolean force) {
    String today = new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    if (closed || fetching || (!force && ((today.equals(prefs.getString("bing_date", "")) && image("bing").exists())
        || System.currentTimeMillis()-lastAttempt < 30*60000L))) return;
    fetching = true; lastAttempt = System.currentTimeMillis();
    new Thread(new Runnable() { @Override public void run() {
      try {
        JSONObject item = new JSONObject(new String(http.get(
            "https://cn.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=zh-CN", 256*1024), "UTF-8"))
                .getJSONArray("images").getJSONObject(0);
        String path = item.getString("url");
        if (!path.startsWith("/th?") && !path.startsWith("/th/")) throw new IOException("Unexpected Bing image URL");
        byte[] bytes = http.get("https://cn.bing.com" + path, 8*1024*1024);
        File temp = new File(context.getFilesDir(), "wallpaper-bing.tmp"); write(temp, bytes);
        Bitmap valid = decode(temp);
        if (valid == null) { temp.delete(); throw new IOException("Invalid Bing image"); }
        valid.recycle();
        if (!temp.renameTo(image("bing"))) throw new IOException("Unable to cache Bing image");
        prefs.edit().putString("bing_date", new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()))
            .putString("bing_caption", item.optString("copyright"))
            .putString("bing_status", "").apply();
        notifyChanged();
      } catch (Exception e) {
        android.util.Log.w("AerialWallpaper", "Bing refresh failed", e);
        prefs.edit().putString("bing_status", context.getString(R.string.bing_unavailable)).apply();
      } finally { fetching = false; }
    } }, "aerial-bing").start();
  }
  boolean fetching() { return fetching; }
  String status() { return prefs.getString("bing_status", ""); }
  private static void write(File file, byte[] bytes) throws IOException {
    FileOutputStream out = new FileOutputStream(file); try { out.write(bytes); } finally { out.close(); }
  }
}
