package app.aerial.legacy;

import android.animation.ValueAnimator;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Toast;
import java.text.Collator;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Aerial's home screen for API 18, laid out on a 960x540 design canvas and drawn by hand.
 *
 * <p>The look follows Aerial's own tvOS-style home (see {@code aerial-tv/site/img/home.jpg}):
 * a full-bleed soft gradient wallpaper, a small clock and date in the top-left, two line icons in
 * the top-right, one row of 16:9 tiles on a frosted glass shelf near the bottom, with the next row
 * peeking 12dp into the frame. Focus rings and permanent labels are configurable: the focused tile
 * grows by 15% without a drop shadow, and its name appears underneath.
 *
 * <p>Every expensive effect is baked once: the wallpaper (with its shading), the blurred glass
 * shelf sampled from the wallpaper, and each tile's
 * artwork. Drawing a frame is just bitmap blits plus text, and nothing animates while idle.
 */
final class HomeView extends View {
  private static final String TAG = "AerialHome";

  /** The firmware bridge that replaces T2launcher; it must not appear in the grid. */
  private static final String BRIDGE_PACKAGE = "com.stv.launcher";

  // ---- design canvas: 960 x 540 "dp" at 1080p (scale 2.0) ----

  private static final float DESIGN_W = 960f;
  private static final float DESIGN_H = 540f;

  private static final int COLS = 5;
  private static final float MARGIN = 56f;
  private static final float GAP = 28f;
  private static final float TILE_W = (DESIGN_W - MARGIN * 2f - GAP * (COLS - 1)) / COLS; // 147.2
  private static final float TILE_H = TILE_W * 9f / 16f; // 82.8
  private static final float PITCH_Y = TILE_H + 50f; // 132.8
  /** The second row sits 12dp below the frame, as in Aerial's screenshot. */
  private static final float TOP_ROW_Y = DESIGN_H - PITCH_Y - 12f; // 395.2
  private static final float PLATE_PAD_H = 20f;
  private static final float PLATE_PAD_V = 14f;
  private static final float PLATE_RADIUS = 24f;
  private static final float TILE_RADIUS = 12f;
  /** Once you go down into the grid, the focused row rests here. */
  private static final float REST_Y = 200f;
  private static final float FOCUS_SCALE = 1.15f;
  private static final float LABEL_GAP = 10f;
  private static final float LABEL_SIZE = 15f;
  private static final float CLOCK_X = 56f;
  private static final float CLOCK_BASELINE = 58f;
  private static final float CLOCK_SIZE = 32f;
  private static final float DATE_SIZE = 16f;
  private static final float CHROME_ICON_Y = 44f;
  private static final float CHROME_RIGHT = 56f;
  private static final float MENU_W = 260f;
  private static final float MENU_X = DESIGN_W - CHROME_RIGHT - MENU_W - 24f;
  private static final float MENU_Y = 108f;
  private static final float SCROLL_FADE = 130f;
  private static final long SCROLL_MS = 250L;
  private static final long LONG_PRESS_MS = 650L;

  /** Some 4.3 x86 GL stacks mis-sample one large bitmap upload; everything is drawn tiled. */
  private static final int MAX_TILE_PX = 384;
  /** Padding baked around the glass shelf so its soft drop shadow fits. */
  private static final float PLATE_SHADOW_PAD = 18f;
  private static final float CHROME_ICON_SIZE = 20f;

  private static final int[] FALLBACK_COLORS = {
      0xFF41537A,
      0xFF554A7E,
      0xFF3E6170,
      0xFF6E4F66,
      0xFF4A5C7A,
      0xFF5E4B44,
  };

  private final Handler handler = new Handler(Looper.getMainLooper());
  final DesktopUi ui;
  private final ArrayList<AppEntry> allApps = new ArrayList<AppEntry>();
  private final Map<String, Float> focusWeights = new HashMap<String, Float>();
  private final Map<String, float[]> oldPositions = new HashMap<String, float[]>();
  private ValueAnimator focusAnimator, positionAnimator, pressAnimator, edgeAnimator;
  private float positionProgress = 1f, pressScale = 1f, edgeX, edgeY;
  private int wallpaperVersion, downKey = -1;
  private boolean okArmed, shown;
  private final Runnable longPress = new Runnable() {
    @Override
    public void run() {
      if (okArmed && !destroyed) {
        okLongPressed = true;
        okArmed = false;
        animatePress(1f);
        ui.appMenu();
      }
    }
  };

  private final ArrayList<AppEntry> items = new ArrayList<AppEntry>();
  /** Tile artwork keyed by package, reused across reloads (it is expensive). */
  private final Map<String, Bitmap> tileArt = new HashMap<String, Bitmap>();

  // ---- baked bitmaps, made on the loader thread ----
  private int generation;

  private int width;
  private int height;
  private float scale;
  private float offX;
  private float offY;

  private float tileWpx;
  private float tileHpx;
  private float plateLeftPx;
  private float plateTopPx;
  private float plateWpx;
  private float plateHpx;
  private float plateRadiusPx;
  private float tileRadiusPx;
  private float clockXpx;
  private float clockBaselinePx;
  private float dateSizePx;
  private float labelSizePx;
  private float labelGapPx;
  private float focusWpx;
  private float focusHpx;

  private int focusIndex;
  private float scrollDp;
  private ValueAnimator scrollAnimator;

  private String clockText = "--:--";
  private String dateText = "";

  private boolean prepared;
  private boolean preparing;
  private boolean loadingApps;
  private boolean destroyed;
  private boolean okLongPressed;

  private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
  private final Paint solidPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Paint iconPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint clockPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint datePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint labelPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint menuTitlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint menuItemPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final TextPaint messagePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final RectF tmpRect = new RectF();

  /** Baked bitmaps are split into small tiles before drawing (see drawTiled). */
  private final ArrayList<Bitmap> wallpaperTiles = new ArrayList<Bitmap>();
  private final ArrayList<int[]> wallpaperTileRects = new ArrayList<int[]>();
  private final ArrayList<Bitmap> plateTiles = new ArrayList<Bitmap>();
  private final ArrayList<int[]> plateTileRects = new ArrayList<int[]>();
  private float plateBitmapLeftPx;
  private float plateBitmapTopPx;
  private float chromeIconSizePx;
  private float chromeWifiCx;
  private float chromeCcCx;
  private float chromeCy;

  private final Runnable tick = new Runnable() {
    @Override
    public void run() {
      updateClock();
    }
  };

  HomeView(Context context) {
    super(context);
    ui = new DesktopUi(this);
    setBackgroundColor(0xFF14111E);
    clockPaint.setTypeface(AppFonts.regular(context));
    clockPaint.setColor(0xFFFFFFFF);
    datePaint.setTypeface(AppFonts.regular(context));
    labelPaint.setTypeface(AppFonts.medium(context));
    menuTitlePaint.setTypeface(AppFonts.medium(context));
    menuItemPaint.setTypeface(AppFonts.regular(context));
    messagePaint.setTypeface(AppFonts.regular(context));
    messagePaint.setColor(0xB3FFFFFF);
    iconPaint.setStyle(Paint.Style.STROKE);
    iconPaint.setStrokeCap(Paint.Cap.ROUND);
    iconPaint.setStrokeJoin(Paint.Join.ROUND);
    iconPaint.setColor(0xE6FFFFFF);
    strokePaint.setStyle(Paint.Style.STROKE);
  }

  // ---- lifecycle from the Activity ----

  /** Called on every onResume: refresh the clock and pick up apps installed since last time. */
  void onShown() {
    shown = true;
    ui.onShown();
    updateClock();
    if (width > 0 && height > 0) {
      startPrepare();
      reloadApps();
    }
  }

  /** Called from {@link MainActivity#onDestroy}. */
  void release() {
    destroyed = true;
    handler.removeCallbacksAndMessages(null);
    ui.release();
    if (focusAnimator != null)
      focusAnimator.cancel();
    if (positionAnimator != null)
      positionAnimator.cancel();
    if (pressAnimator != null)
      pressAnimator.cancel();
    if (edgeAnimator != null)
      edgeAnimator.cancel();
    if (scrollAnimator != null) {
      scrollAnimator.cancel();
      scrollAnimator = null;
    }
  }

  void onHidden() {
    shown = false;
    ui.onHidden();
    handler.removeCallbacks(tick);
    handler.removeCallbacks(longPress);
    okArmed = false;
    downKey = -1;
    animatePress(1f);
  }

  void resetHome() {
    ui.resetHome();
  }

  // ---- measurement ----

  @Override
  protected void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
    super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);
    width = newWidth;
    height = newHeight;
    if (width <= 0 || height <= 0) {
      return;
    }
    computeMetrics();
    generation++;
    prepared = false;
    preparing = false;
    loadingApps = false;
    wallpaperTiles.clear();
    wallpaperTileRects.clear();
    plateTiles.clear();
    plateTileRects.clear();
    synchronized (tileArt) {
      tileArt.clear();
    }
    items.clear();
    startPrepare();
    reloadApps();
    invalidate();
  }

  private void computeMetrics() {
    scale = Math.min(width / DESIGN_W, height / DESIGN_H);
    offX = (width - DESIGN_W * scale) / 2f;
    offY = (height - DESIGN_H * scale) / 2f;

    tileWpx = d(TILE_W);
    tileHpx = d(TILE_H);
    tileRadiusPx = d(TILE_RADIUS);

    plateLeftPx = Math.round(d(MARGIN - PLATE_PAD_H));
    plateTopPx = Math.round(d(TOP_ROW_Y - PLATE_PAD_V));
    plateWpx = Math.round(tileWpx * COLS + d(GAP) * (COLS - 1) + 2f * d(PLATE_PAD_H));
    plateHpx = Math.round(tileHpx + 2f * d(PLATE_PAD_V));
    plateRadiusPx = d(PLATE_RADIUS);
    plateBitmapLeftPx = plateLeftPx - d(PLATE_SHADOW_PAD);
    plateBitmapTopPx = plateTopPx - d(PLATE_SHADOW_PAD);

    clockXpx = d(CLOCK_X);
    clockBaselinePx = d(CLOCK_BASELINE);
    dateSizePx = d(DATE_SIZE);
    labelSizePx = d(LABEL_SIZE);
    labelGapPx = d(LABEL_GAP);

    focusWpx = Math.round(tileWpx * FOCUS_SCALE);
    focusHpx = Math.round(tileHpx * FOCUS_SCALE);

    clockPaint.setTextSize(d(CLOCK_SIZE));
    datePaint.setTextSize(dateSizePx);
    labelPaint.setTextSize(labelSizePx);
    menuTitlePaint.setTextSize(d(16f));
    menuItemPaint.setTextSize(d(15f));
    messagePaint.setTextSize(d(18f));
    iconPaint.setStrokeWidth(Math.max(1.5f, d(1.6f)));

    chromeIconSizePx = d(CHROME_ICON_SIZE);
    chromeCcCx = d(DESIGN_W - CHROME_RIGHT - 10f);
    chromeWifiCx = chromeCcCx - d(42f);
    chromeCy = d(CHROME_ICON_Y);
  }

  private float d(float design) {
    return design * scale;
  }

  private float dx(float design) {
    return offX + design * scale;
  }

  /** Shared RectF, so drawing a frame does not allocate. */
  private RectF rect(float left, float top, float right, float bottom) {
    tmpRect.set(left, top, right, bottom);
    return tmpRect;
  }

  private float dy(float design) {
    return offY + design * scale;
  }

  // ---- wallpaper, glass shelf and focus shadow (baked once) ----

  /** Splits a baked bitmap into small tiles so each draw call stays under MAX_TILE_PX. */
  private static void tileBitmap(Bitmap source, List<Bitmap> tiles, List<int[]> rects) {
    int w = source.getWidth();
    int h = source.getHeight();
    for (int y = 0; y < h; y += MAX_TILE_PX) {
      int th = Math.min(MAX_TILE_PX, h - y);
      for (int x = 0; x < w; x += MAX_TILE_PX) {
        int tw = Math.min(MAX_TILE_PX, w - x);
        tiles.add(Bitmap.createBitmap(source, x, y, tw, th));
        rects.add(new int[] {x, y, tw, th});
      }
    }
  }

  /** Draws tiled bitmaps at one-to-one pixel size, with no filtering, so no seams appear. */
  private void drawTiled(
      Canvas canvas, List<Bitmap> tiles, List<int[]> rects, float left, float top, Paint paint) {
    for (int i = 0; i < tiles.size(); i++) {
      int[] r = rects.get(i);
      canvas.drawBitmap(tiles.get(i), null,
          rect(left + r[0], top + r[1], left + r[0] + r[2], top + r[1] + r[3]), paint);
    }
  }

  private void startPrepare() {
    if (preparing || prepared || destroyed || width <= 0) {
      return;
    }
    preparing = true;
    final int w = width;
    final int h = height;
    final int gen = generation;
    final int version = wallpaperVersion;
    final int selectedPreset = ui.preset();
    Thread loader = new Thread(new Runnable() {
      @Override
      public void run() {
        Bitmap wp = ui.wallpapers.render(selectedPreset, w, h);
        bakeShade(wp);
        final Bitmap frost = Bitmap.createScaledBitmap(wp, 320, 180, true);
        int[] pixels = new int[320 * 180];
        frost.getPixels(pixels, 0, 320, 0, 0, 320, 180);
        for (int i = 0; i < 3; i++) Gradients.boxBlur(pixels, 320, 180, 5);
        frost.setPixels(pixels, 0, 320, 0, 0, 320, 180);
        final ArrayList<Bitmap> wpTiles = new ArrayList<Bitmap>();
        final ArrayList<int[]> wpRects = new ArrayList<int[]>();
        tileBitmap(wp, wpTiles, wpRects);
        Bitmap shelf = bakePlate(wp);
        final ArrayList<Bitmap> shelfTiles = new ArrayList<Bitmap>();
        final ArrayList<int[]> shelfRects = new ArrayList<int[]>();
        if (shelf != null) {
          tileBitmap(shelf, shelfTiles, shelfRects);
        }
        if (destroyed || gen != generation || version != wallpaperVersion) {
          return;
        }
        post(new Runnable() {
          @Override
          public void run() {
            if (destroyed || gen != generation || version != wallpaperVersion) {
              return;
            }
            wallpaperTiles.clear();
            wallpaperTiles.addAll(wpTiles);
            wallpaperTileRects.clear();
            wallpaperTileRects.addAll(wpRects);
            plateTiles.clear();
            plateTiles.addAll(shelfTiles);
            plateTileRects.clear();
            plateTileRects.addAll(shelfRects);
            ui.setFrost(frost);
            preparing = false;
            prepared = true;
            Log.i(TAG,
                "wallpaper preset " + selectedPreset + " at " + w + "x" + h + ", glass shelf "
                    + Math.round(plateWpx) + "x" + Math.round(plateHpx));
            invalidate();
          }
        });
      }
    }, "aerial-wallpaper");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  /** Gentle darkening baked into the wallpaper, mostly at the bottom under the shelf. */
  private void bakeShade(Bitmap bitmap) {
    Canvas canvas = new Canvas(bitmap);
    float w = bitmap.getWidth();
    float h = bitmap.getHeight();
    Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    shade.setShader(new LinearGradient(0f, 0f, 0f, h,
        new int[] {0x1A000000, 0x00000000, 0x2E000000, 0x66000000},
        new float[] {0f, 0.18f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    canvas.drawRect(0f, 0f, w, h, shade);
  }

  /**
   * The frosted glass shelf: the wallpaper underneath it, blurred, darkened and lifted a
   * little, with a hairline highlight along the top and rounded corners.
   */
  private Bitmap bakePlate(Bitmap source) {
    int pad = Math.round(d(PLATE_SHADOW_PAD));
    int contentW = Math.max(2, Math.round(plateWpx));
    int contentH = Math.max(2, Math.round(plateHpx));
    int pw = contentW + pad * 2;
    int ph = contentH + pad * 2;
    Bitmap out = Bitmap.createBitmap(pw, ph, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);

    // Keep the shelf flat: the sampled glass and hairline are enough separation.

    int sx = Math.max(0, Math.round(plateLeftPx));
    int sy = Math.max(0, Math.round(plateTopPx));
    int sw = Math.min(contentW, source.getWidth() - sx);
    int sh = Math.min(contentH, source.getHeight() - sy);
    if (sw <= 0 || sh <= 0) {
      return out;
    }
    // A real blur is too slow on a 4.3 TV: downscale, blur small, scale back up.
    int bw = Math.max(8, contentW / 4);
    int bh = Math.max(8, contentH / 4);
    Bitmap small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    Canvas smallCanvas = new Canvas(small);
    smallCanvas.drawBitmap(
        source, new Rect(sx, sy, sx + sw, sy + sh), new Rect(0, 0, bw, bh), bitmapPaint);
    int[] px = new int[bw * bh];
    small.getPixels(px, 0, bw, 0, 0, bw, bh);
    Gradients.boxBlur(px, bw, bh, 4);
    Gradients.boxBlur(px, bw, bh, 4);
    small.setPixels(px, 0, bw, 0, 0, bw, bh);

    Path clip = new Path();
    clip.addRoundRect(new RectF(pad, pad, pad + contentW, pad + contentH), plateRadiusPx,
        plateRadiusPx, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);
    canvas.drawBitmap(
        small, null, new RectF(pad, pad, pad + contentW, pad + contentH), bitmapPaint);
    small.recycle();

    // Lift the glass: a white wash that is strongest along the top edge.
    Paint overlay = new Paint(Paint.ANTI_ALIAS_FLAG);
    overlay.setColor(0x1A000000);
    canvas.drawRect(0f, 0f, pw, ph, overlay);
    overlay.setShader(new LinearGradient(0f, pad, 0f, pad + contentH,
        new int[] {0x44FFFFFF, 0x24FFFFFF, 0x16FFFFFF}, new float[] {0f, 0.2f, 1f},
        Shader.TileMode.CLAMP));
    canvas.drawRect(0f, 0f, pw, ph, overlay);
    overlay.setShader(null);
    overlay.setColor(0x1EFFFFFF);
    canvas.drawRect(0f, 0f, pw, ph, overlay);
    canvas.restore();

    strokePaint.setStrokeWidth(Math.max(1f, d(0.8f)));
    strokePaint.setColor(0x26FFFFFF);
    float inset = strokePaint.getStrokeWidth() / 2f;
    canvas.drawRoundRect(
        new RectF(pad + inset, pad + inset, pad + contentW - inset, pad + contentH - inset),
        plateRadiusPx, plateRadiusPx, strokePaint);
    strokePaint.setStrokeWidth(Math.max(1f, d(1.2f)));
    strokePaint.setColor(0x40FFFFFF);
    canvas.drawLine(pad + plateRadiusPx * 0.6f, pad + d(1.2f),
        pad + contentW - plateRadiusPx * 0.6f, pad + d(1.2f), strokePaint);
    return out;
  }

  // ---- apps ----

  private void reloadApps() {
    if (loadingApps || destroyed || width <= 0) {
      return;
    }
    loadingApps = true;
    final int gen = generation;
    final Context context = getContext().getApplicationContext();
    Thread loader = new Thread(new Runnable() {
      @Override
      public void run() {
        final List<AppEntry> loaded = loadApps(context);
        if (destroyed || gen != generation) {
          return;
        }
        post(new Runnable() {
          @Override
          public void run() {
            if (destroyed || gen != generation) {
              return;
            }
            loadingApps = false;
            applyItems(loaded);
          }
        });
      }
    }, "aerial-apps");
    loader.setPriority(Thread.MIN_PRIORITY);
    loader.start();
  }

  private List<AppEntry> loadApps(Context context) {
    PackageManager pm = context.getPackageManager();
    String self = context.getPackageName();
    Map<String, SystemTiles.Kind> categoryTiles = SystemTiles.categories(pm);
    List<ResolveInfo> found = new ArrayList<ResolveInfo>();
    Intent launcherIntent = new Intent(Intent.ACTION_MAIN);
    launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    try {
      List<ResolveInfo> plain = pm.queryIntentActivities(launcherIntent, 0);
      if (plain != null) {
        found.addAll(plain);
      }
    } catch (RuntimeException ignored) {
      // A broken package in the image must not take the desktop down.
    }
    // Intent.CATEGORY_LEANBACK_LAUNCHER only exists from API 21; query by its wire value.
    Intent tvIntent = new Intent(Intent.ACTION_MAIN);
    tvIntent.addCategory("android.intent.category.LEANBACK_LAUNCHER");
    try {
      List<ResolveInfo> tv = pm.queryIntentActivities(tvIntent, 0);
      if (tv != null) {
        found.addAll(tv);
      }
    } catch (RuntimeException ignored) {
      // Not available on this firmware.
    }

    Map<String, AppEntry> byPackage = new LinkedHashMap<String, AppEntry>();
    for (int i = 0; i < found.size(); i++) {
      ResolveInfo info = found.get(i);
      ActivityInfo ai = info.activityInfo;
      if (ai == null || ai.packageName == null || ai.name == null) {
        continue;
      }
      if (ai.packageName.equals(self) || ai.packageName.equals(BRIDGE_PACKAGE)) {
        continue;
      }
      if (byPackage.containsKey(ai.packageName)) {
        continue;
      }
      CharSequence label;
      try {
        label = info.loadLabel(pm);
      } catch (RuntimeException badLabel) {
        label = null;
      }
      if (label == null || label.length() == 0) {
        label = ai.name;
      }
      String artKey = ai.packageName;
      try {
        artKey += ":" + pm.getPackageInfo(ai.packageName, 0).lastUpdateTime;
      } catch (Exception ignored) {
      }
      Bitmap art;
      synchronized (tileArt) {
        art = tileArt.get(artKey);
      }
      if (art == null) {
        art = bakeTileArt(pm, info, ai.packageName, categoryTiles.get(ai.packageName));
        if (art != null) {
          synchronized (tileArt) {
            tileArt.put(artKey, art);
          }
        }
      }
      byPackage.put(ai.packageName, new AppEntry(ai.packageName, ai.name, label.toString(), art));
    }

    List<AppEntry> list = new ArrayList<AppEntry>(byPackage.values());
    final Collator collator = Collator.getInstance(Locale.getDefault());
    Collections.sort(list, new Comparator<AppEntry>() {
      @Override
      public int compare(AppEntry a, AppEntry b) {
        int order = collator.compare(a.label, b.label);
        if (order != 0) {
          return order;
        }
        return a.packageName.compareTo(b.packageName);
      }
    });
    Log.i(TAG, "found " + list.size() + " apps");
    for (int i = 0; i < list.size(); i++) {
      Log.i(TAG, "app[" + i + "] " + list.get(i).packageName + " label=" + list.get(i).label);
    }
    return list;
  }

  private void applyItems(List<AppEntry> loaded) {
    // A transient PackageManager scan failure must not erase persisted organization.
    if (loaded.isEmpty() && (!allApps.isEmpty() || !ui.layout.entries.isEmpty()))
      return;
    String focusedPackage = focusedItem() == null ? null : focusedItem().packageName;
    items.clear();
    allApps.clear();
    allApps.addAll(loaded);
    items.addAll(ui.project(loaded));
    fitLabels();
    if (focusedPackage != null) {
      for (int i = 0; i < items.size(); i++) {
        if (focusedPackage.equals(items.get(i).packageName)) {
          focusIndex = i;
          break;
        }
      }
    }
    clampFocus();
    scrollDp = targetScrollFor(focusIndex / COLS);
    animateSelection();
    invalidate();
  }

  private void fitLabels() {
    if (tileWpx <= 0) {
      return;
    }
    float maxWidth = tileWpx;
    for (int i = 0; i < items.size(); i++) {
      AppEntry entry = items.get(i);
      entry.displayLabel =
          TextUtils.ellipsize(entry.label, labelPaint, maxWidth, TextUtils.TruncateAt.END)
              .toString();
    }
  }

  private void clampFocus() {
    if (items.isEmpty()) {
      focusIndex = 0;
      return;
    }
    if (focusIndex < 0) {
      focusIndex = 0;
    }
    if (focusIndex >= items.size()) {
      focusIndex = items.size() - 1;
    }
  }

  private AppEntry focusedItem() {
    if (focusIndex < 0 || focusIndex >= items.size()) {
      return null;
    }
    return items.get(focusIndex);
  }

  // ---- tile artwork ----

  private Bitmap bakeTileArt(PackageManager pm, ResolveInfo info, String packageName, SystemTiles.Kind category) {
    int artW = Math.max(2, Math.round(tileWpx * 1.2f));
    int artH = Math.max(2, Math.round(tileHpx * 1.2f));
    Bitmap art = Bitmap.createBitmap(artW, artH, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(art);
    Path clip = new Path();
    clip.addRoundRect(new RectF(0f, 0f, artW, artH), tileRadiusPx * 1.2f, tileRadiusPx * 1.2f, Path.Direction.CW);
    canvas.save();
    canvas.clipPath(clip);

    boolean system = (info.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    Bitmap banner = renderBanner(pm, info, artW, artH);
    if (banner == null && system) {
      SystemTiles.Kind kind = SystemTiles.of(packageName);
      if (SystemTiles.draw(canvas, kind == null ? category : kind, artW, artH)) {
        canvas.restore(); return art;
      }
    }
    if (banner != null) {
      canvas.drawBitmap(banner, 0f, 0f, bitmapPaint);
      banner.recycle();
    } else {
      Drawable icon;
      try { icon = info.loadIcon(pm); } catch (RuntimeException badIcon) { icon = null; }
      if (icon != null) {
        AppTileArt.draw(canvas, icon, artW, artH);
      } else {
        drawArtBackground(canvas, artW, artH, tileBase(fallbackColor(packageName)));
      }
    }
    canvas.restore();
    return art;
  }

  /**
   * Reads {@code android:banner} / {@code ApplicationInfo.banner} reflectively: those fields
   * only exist from API 21, but a firmware image may still carry a banner on a TV app. On
   * Android 4.3 the manifest fallback reads the resource ID without requiring the newer fields.
   */
  private static Drawable loadBanner(PackageManager pm, ActivityInfo ai) {
    try {
      ApplicationInfo app = pm.getApplicationInfo(ai.packageName, 0);
      int resId = bannerResource(ai);
      if (resId == 0) {
        resId = bannerResource(app);
      }
      Resources resources = pm.getResourcesForApplication(app);
      if (resId == 0) resId = manifestBanner(resources, ai);
      if (resId != 0) return resources.getDrawable(resId);
    } catch (Exception ignored) {
      // No banner, or the package went away mid-scan.
    }
    return null;
  }

  private static int manifestBanner(Resources resources, ActivityInfo activity) {
    android.content.res.XmlResourceParser xml = null;
    String android = "http://schemas.android.com/apk/res/android";
    int applicationBanner = 0, activityBanner = 0;
    try {
      xml = resources.getAssets().openXmlResourceParser("AndroidManifest.xml");
      while (xml.next() != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
        if (xml.getEventType() != org.xmlpull.v1.XmlPullParser.START_TAG) continue;
        if ("application".equals(xml.getName()))
          applicationBanner = xml.getAttributeResourceValue(android, "banner", 0);
        if ("activity".equals(xml.getName()) || "activity-alias".equals(xml.getName())) {
          String name = xml.getAttributeValue(android, "name");
          if (name == null) continue;
          if (name.startsWith(".")) name = activity.packageName + name;
          else if (name.indexOf('.') < 0) name = activity.packageName + "." + name;
          if (name.equals(activity.name)) activityBanner = xml.getAttributeResourceValue(android, "banner", 0);
        }
      }
    } catch (Exception ignored) {
      // A malformed manifest must not hide or crash an otherwise launchable app.
    } finally { if (xml != null) xml.close(); }
    return activityBanner != 0 ? activityBanner : applicationBanner;
  }

  private static int bannerResource(Object info) {
    try {
      java.lang.reflect.Field field = info.getClass().getField("banner");
      Object value = field.get(info);
      if (value instanceof Integer) {
        return (Integer) value;
      }
    } catch (Exception ignored) {
      // Field not present on this API level.
    }
    return 0;
  }

  /** TV banner, centre-cropped to the tile's 16:9 frame, or null when the app has none. */
  private Bitmap renderBanner(PackageManager pm, ResolveInfo info, int outW, int outH) {
    ActivityInfo ai = info.activityInfo;
    if (ai == null) {
      return null;
    }
    Drawable banner = loadBanner(pm, ai);
    if (banner == null) {
      return null;
    }
    int iw = banner.getIntrinsicWidth();
    int ih = banner.getIntrinsicHeight();
    if (iw <= 0 || ih <= 0 || iw < ih * 4f / 3f) {
      return null;
    }
    int cap = 1024;
    float fit = Math.min(1f, cap / (float) Math.max(iw, ih));
    int bw = Math.max(1, Math.round(iw * fit));
    int bh = Math.max(1, Math.round(ih * fit));
    Bitmap source = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
    Canvas sourceCanvas = new Canvas(source);
    banner.setBounds(0, 0, bw, bh);
    banner.draw(sourceCanvas);

    float dstAspect = outW / (float) outH;
    float srcAspect = bw / (float) bh;
    int cw;
    int ch;
    int left;
    int top;
    if (srcAspect > dstAspect) {
      ch = bh;
      cw = Math.round(bh * dstAspect);
      left = (bw - cw) / 2;
      top = 0;
    } else {
      cw = bw;
      ch = Math.round(bw / dstAspect);
      left = 0;
      top = (bh - ch) / 2;
    }
    Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(out);
    canvas.drawBitmap(
        source, new Rect(left, top, left + cw, top + ch), new Rect(0, 0, outW, outH), bitmapPaint);
    source.recycle();
    return out;
  }

  private static int fallbackColor(String packageName) {
    int hash = packageName.hashCode() & 0x7FFFFFFF;
    return FALLBACK_COLORS[hash % FALLBACK_COLORS.length];
  }

  /** Pushes the colour into a mid-tone range so tiles read as tinted glass, not neon. */
  private static int tileBase(int color) {
    float[] hsv = new float[3];
    Color.colorToHSV(color, hsv);
    hsv[1] = Math.max(0.30f, Math.min(0.72f, hsv[1]));
    hsv[2] = Math.max(0.40f, Math.min(0.68f, hsv[2]));
    return Color.HSVToColor(hsv);
  }

  private void drawArtBackground(Canvas canvas, int w, int h, int baseColor) {
    SystemTiles.background(canvas, w, h, baseColor);
  }

  // ---- drawing ----

  @Override
  protected void onDraw(Canvas canvas) {
    if (!wallpaperTiles.isEmpty()) {
      drawTiled(canvas, wallpaperTiles, wallpaperTileRects, 0f, 0f, null);
    } else {
      canvas.drawColor(0xFF14111E);
    }

    float plateAlpha = clamp01(1f - scrollDp / SCROLL_FADE);
    if (ui.mode == DesktopUi.SAVER) {
      canvas.save();
      canvas.translate(offX, offY);
      canvas.scale(scale, scale);
      ui.draw(canvas);
      canvas.restore();
      return;
    }
    if (ui.plate() && !plateTiles.isEmpty() && plateAlpha > 0.02f) {
      solidPaint.setAlpha((int) (plateAlpha * 255f));
      float shift = scrollDp * scale;
      drawTiled(canvas, plateTiles, plateTileRects, plateBitmapLeftPx, plateBitmapTopPx - shift,
          solidPaint);
      solidPaint.setAlpha(255);
    }

    drawTiles(canvas);

    float chromeAlpha = clamp01(1f - scrollDp / (SCROLL_FADE * 1.15f));
    if (chromeAlpha > 0.02f) {
      drawClock(canvas, chromeAlpha);
    }
    drawChromeIcons(canvas);

    if (items.isEmpty()) {
      messagePaint.setAlpha(150);
      canvas.drawText(
          getContext().getString(loadingApps ? R.string.loading_apps : R.string.no_apps), dx(56f),
          dy(300f), messagePaint);
      messagePaint.setAlpha(255);
    }

    canvas.save();
    canvas.translate(offX, offY);
    canvas.scale(scale, scale);
    ui.draw(canvas);
    canvas.restore();
  }

  private void drawTiles(Canvas canvas) {
    for (int pass = 0; pass < 2; pass++)
      for (int i = 0; i < items.size(); i++) {
        boolean selected = i == focusIndex;
        if ((pass == 0 && selected) || (pass == 1 && !selected))
          continue;
        AppEntry entry = items.get(i);
        if (entry.art == null)
          continue;
        float x = MARGIN + (TILE_W + GAP) * (i % COLS), y = TOP_ROW_Y + PITCH_Y * (i / COLS);
        float[] from = oldPositions.get(entry.packageName);
        if (from != null && positionProgress < 1f) {
          x = from[0] + (x - from[0]) * positionProgress;
          y = from[1] + (y - from[1]) * positionProgress;
        }
        float left = dx(x), top = dy(y - scrollDp);
        if (top > height || top + tileHpx < 0)
          continue;
        Float f = focusWeights.get(entry.packageName);
        float weight = f == null ? (selected && !ui.chromeFocused ? 1f : 0f) : f;
        float k = 1f + .15f * weight;
        if (selected)
          k *= pressScale;
        float w = Math.round(tileWpx * k), h = Math.round(tileHpx * k);
        float cx = left + tileWpx / 2f + (selected ? d(edgeX) : 0),
              cy = top + tileHpx / 2f + (selected ? d(edgeY) : 0);
        canvas.drawBitmap(
            entry.art, null, rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), bitmapPaint);
        drawTileEdge(canvas, cx - w / 2, cy - h / 2, w, h, tileRadiusPx * k);
        if (ui.contrast() && weight > .01f && ui.mode == DesktopUi.NONE && !ui.chromeFocused) {
          strokePaint.setStrokeWidth(d(3f));
          strokePaint.setColor(withAlpha(weight, 255, 255, 255));
          canvas.drawRoundRect(
              rect(cx - w / 2 - d(5), cy - h / 2 - d(5), cx + w / 2 + d(5), cy + h / 2 + d(5)),
              tileRadiusPx * k + d(5), tileRadiusPx * k + d(5), strokePaint);
        }
        if (ui.showName(selected && !ui.chromeFocused)) {
          float baseline = cy + h / 2 + labelGapPx + labelSizePx * .92f;
          float tw = labelPaint.measureText(entry.displayLabel);
          labelPaint.setColor(selected ? 0xFFFFFFFF : 0xBBFFFFFF);
          canvas.drawText(entry.displayLabel, cx - tw / 2, baseline, labelPaint);
        }
      }
  }

  /** A 7% white hairline drawn crisp at screen resolution, so it never blurs into a halo. */
  private void drawTileEdge(Canvas canvas, float left, float top, float w, float h, float radius) {
    strokePaint.setStyle(Paint.Style.STROKE);
    strokePaint.setStrokeWidth(Math.max(1f, d(0.9f)));
    strokePaint.setColor(0x16FFFFFF);
    float inset = strokePaint.getStrokeWidth() / 2f;
    canvas.drawRoundRect(rect(left + inset, top + inset, left + w - inset, top + h - inset), radius,
        radius, strokePaint);
  }

  private void drawClock(Canvas canvas, float alpha) {
    if (clockText.length() == 0) {
      return;
    }
    float x = clockXpx;
    float baseline = ui.largeClock() ? d(225f) : clockBaselinePx;
    clockPaint.setTextSize(d(ui.largeClock() ? 104f : CLOCK_SIZE));
    clockPaint.setColor(withAlpha(alpha, 0xFF, 0xFF, 0xFF));
    canvas.drawText(clockText, x, baseline, clockPaint);
    float clockWidth = clockPaint.measureText(clockText);

    if (dateText.length() > 0) {
      float dateX = ui.largeClock() ? x + d(4f) : x + clockWidth + d(20f);
      float dateBaseline = ui.largeClock() ? baseline + d(36f) : baseline;
      datePaint.setColor(withAlpha(alpha * 0.72f, 0xFF, 0xFF, 0xFF));
      canvas.drawText(dateText, dateX, dateBaseline, datePaint);
    }
  }

  private void drawChromeIcons(Canvas canvas) {
    if (ui.chromeFocused && ui.mode == DesktopUi.NONE) {
      solidPaint.setColor(0xFFF5F5F7);
      canvas.drawRoundRect(
          rect(chromeWifiCx - d(22), chromeCy - d(20), chromeCcCx + d(22), chromeCy + d(20)), d(22),
          d(22), solidPaint);
    }
    iconPaint.setStyle(Paint.Style.FILL);
    iconPaint.setColor(ui.chromeFocused && ui.mode == DesktopUi.NONE ? 0xFF1D1D1F : 0xE6FFFFFF);
    AerialGlyphs.draw(canvas, NetworkStatus.glyph(ui.networkKind), chromeWifiCx - chromeIconSizePx / 2f, chromeCy - chromeIconSizePx / 2f,
        chromeIconSizePx, iconPaint);
    solidPaint.setStyle(Paint.Style.FILL);
    solidPaint.setColor(ui.chromeFocused && ui.mode == DesktopUi.NONE ? 0xFF1D1D1F : 0xE6FFFFFF);
    Glyphs.drawSwitches(canvas, chromeCcCx - chromeIconSizePx / 2f,
        chromeCy - chromeIconSizePx / 2f, chromeIconSizePx, solidPaint);
  }

  private static String ellipsize(String text, TextPaint paint, float maxWidth) {
    if (paint.measureText(text) <= maxWidth) {
      return text;
    }
    return TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString();
  }

  private static int withAlpha(float alpha, int r, int g, int b) {
    int a = Math.round(clamp01(alpha) * 255f);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static float clamp01(float value) {
    return value < 0f ? 0f : (value > 1f ? 1f : value);
  }

  // ---- input ----

  /**
   * Handles one key event from {@link MainActivity#dispatchKeyEvent}. Returns true when the
   * desktop consumed it. BACK is always swallowed: a HOME screen that exits leaves a black TV.
   */
  boolean handleKeyEvent(KeyEvent event) {
    int code = event.getKeyCode(), action = event.getAction();
    if (event.isCanceled()) {
      okArmed = false;
      downKey = -1;
      handler.removeCallbacks(longPress);
      animatePress(1f);
      return true;
    }
    if (ui.key(event))
      return true;
    if (action == KeyEvent.ACTION_DOWN) {
      if (code >= 19 && code <= 22) {
        moveFocus(code == 21 ? -1 : code == 22 ? 1 : 0, code == 19 ? -1 : code == 20 ? 1 : 0);
        return true;
      }
      if (code == 23 || code == 66) {
        if (event.getRepeatCount() == 0) {
          okArmed = true;
          okLongPressed = false;
          downKey = code;
          animatePress(.97f);
          handler.removeCallbacks(longPress);
          handler.postDelayed(longPress, LONG_PRESS_MS);
        }
        return true;
      }
      if (code == 82 && event.getRepeatCount() == 0) {
        handler.removeCallbacks(longPress);
        okArmed = false;
        animatePress(1f);
        ui.appMenu();
        return true;
      }
      if (code == 4 && event.getRepeatCount() == 0) {
        if (focusIndex / COLS > 0)
          firstRow();
        else
          ui.showSaverFromKey(code);
        return true;
      }
    } else if (action == KeyEvent.ACTION_UP && (code == 23 || code == 66)) {
      handler.removeCallbacks(longPress);
      animatePress(1f);
      if (okArmed && !okLongPressed && downKey == code) {
        okArmed = false;
        launchFocused();
      }
      downKey = -1;
      return true;
    }
    return code == 4 || code == 82 || (code >= 19 && code <= 23) || code == 66;
  }
  private void moveFocus(int dirX, int dirY) {
    if (items.isEmpty()) {
      if (dirY < 0) {
        ui.chromeFocused = true;
        animateSelection();
      }
      return;
    }
    int row = focusIndex / COLS, col = focusIndex % COLS, target = focusIndex;
    if (dirY < 0 && row == 0) {
      ui.chromeFocused = true;
      animateSelection();
      invalidate();
      return;
    }
    if (dirX < 0 && col > 0)
      target--;
    else if (dirX > 0 && col < COLS - 1 && target + 1 < items.size())
      target++;
    else if (dirY < 0 && row > 0)
      target -= COLS;
    else if (dirY > 0 && (row + 1) * COLS < items.size())
      target = Math.min(items.size() - 1, target + COLS);
    if (target != focusIndex) {
      focusIndex = target;
      animateSelection();
      animateScrollTo(targetScrollFor(focusIndex / COLS));
      if (ui.prefs.getBoolean("sounds", false))
        playSoundEffect(android.view.SoundEffectConstants.NAVIGATION_DOWN);
    } else
      edgeNudge(dirX, dirY);
    invalidate();
  }

  private float targetScrollFor(int row) {
    if (row <= 0) {
      return 0f;
    }
    return Math.max(0f, TOP_ROW_Y + PITCH_Y * row - REST_Y);
  }

  private void animateScrollTo(float targetDp) {
    if (scrollAnimator != null) {
      scrollAnimator.cancel();
      scrollAnimator = null;
    }
    if (Math.abs(targetDp - scrollDp) < 0.5f) {
      scrollDp = targetDp;
      return;
    }
    scrollAnimator = ValueAnimator.ofFloat(scrollDp, targetDp);
    scrollAnimator.setDuration(SCROLL_MS);
    scrollAnimator.setInterpolator(new DecelerateInterpolator(1.7f));
    scrollAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator animation) {
        scrollDp = (Float) animation.getAnimatedValue();
        invalidate();
      }
    });
    scrollAnimator.start();
  }

  // ---- launching ----

  private void launchFocused() {
    AppEntry entry = focusedItem();
    if (entry == null) {
      return;
    }
    if (!ui.launchSelection(entry))
      launch(entry);
  }

  private void launch(AppEntry entry) {
    Intent intent = new Intent(Intent.ACTION_MAIN);
    intent.addCategory(Intent.CATEGORY_LAUNCHER);
    intent.setComponent(new ComponentName(entry.packageName, entry.activityName));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
    if (startSafely(intent)) {
      return;
    }
    Intent fallback = null;
    try {
      fallback = getContext().getPackageManager().getLaunchIntentForPackage(entry.packageName);
    } catch (RuntimeException ignored) {
      // PackageManager may be mid-scan.
    }
    if (fallback != null) {
      fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (startSafely(fallback)) {
        return;
      }
    }
    AppFonts.toast(getContext(), R.string.error_launch, Toast.LENGTH_SHORT).show();
  }

  private boolean startSafely(Intent intent) {
    try {
      getContext().startActivity(intent);
      return true;
    } catch (RuntimeException failure) {
      Log.w(TAG, "cannot start " + intent, failure);
      return false;
    }
  }

  private void openAppInfo(String packageName) {
    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
    intent.setData(Uri.parse("package:" + packageName));
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (!startSafely(intent)) {
      AppFonts.toast(getContext(), R.string.error_launch, Toast.LENGTH_SHORT).show();
    }
  }

  AppEntry selectedItem() {
    return focusedItem();
  }
  int focusedIndex() {
    return focusIndex;
  }
  void firstRow() {
    focusIndex = Math.min(focusIndex % COLS, Math.max(0, items.size() - 1));
    animateSelection();
    animateScrollTo(0);
    invalidate();
  }
  RectF selectedBounds() {
    int i = focusIndex;
    float x = MARGIN + (i % COLS) * (TILE_W + GAP), y = TOP_ROW_Y + (i / COLS) * PITCH_Y - scrollDp;
    return new RectF(x, y, x + TILE_W, y + TILE_H);
  }
  boolean removable(String pkg) {
    try {
      return (getContext().getPackageManager().getApplicationInfo(pkg, 0).flags
                 & ApplicationInfo.FLAG_SYSTEM)
          == 0;
    } catch (Exception e) {
      return false;
    }
  }
  void launchEntry(AppEntry e) {
    launch(e);
  }
  void appInfo(String pkg) {
    openAppInfo(pkg);
  }
  void startIntent(Intent i) {
    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    startSafely(i);
  }
  void openPackages(String[] packages, String action) {
    if (Settings.ACTION_WIFI_SETTINGS.equals(action)) {
      Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      if (startSafely(i))
        return;
    }
    for (String p : packages) {
      Intent i = getContext().getPackageManager().getLaunchIntentForPackage(p);
      if (i != null && startSafely(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        return;
    }
    startIntent(new Intent(action));
  }
  boolean isDefaultHome() {
    Intent i = new Intent(Intent.ACTION_MAIN)
                   .addCategory(Intent.CATEGORY_HOME)
                   .addCategory(Intent.CATEGORY_DEFAULT);
    ResolveInfo r =
        getContext().getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);
    return r != null && r.activityInfo != null
        && getContext().getPackageName().equals(r.activityInfo.packageName);
  }
  void changeWallpaper() {
    wallpaperVersion++;
    prepared = false;
    preparing = false;
    startPrepare();
  }
  Bitmap folderArt(DesktopLayout.Entry folder, Map<String, AppEntry> catalog) {
    int w = Math.max(2, Math.round(tileWpx)), h = Math.max(2, Math.round(tileHpx));
    Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(out);
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    Path path = new Path();
    path.addRoundRect(new RectF(0, 0, w, h), tileRadiusPx, tileRadiusPx, Path.Direction.CW);
    c.clipPath(path);
    p.setColor(0xFF4A4B63);
    c.drawRect(0, 0, w, h, p);
    float aw = w * .38f, ah = h * .38f;
    for (int i = 0; i < Math.min(4, folder.members.size()); i++) {
      AppEntry a = catalog.get(folder.members.get(i));
      if (a == null || a.art == null)
        continue;
      float x = w * .08f + (i % 2) * w * .46f, y = h * .08f + (i / 2) * h * .46f;
      c.drawBitmap(a.art, null, new RectF(x, y, x + aw, y + ah), p);
    }
    return out;
  }
  void applyLayout(List<AppEntry> projected, String focusKey) {
    Map<String, float[]> previous = new HashMap<String, float[]>(oldPositions);
    oldPositions.clear();
    for (int i = 0; i < items.size(); i++) {
      float x = MARGIN + (i % COLS) * (TILE_W + GAP);
      float y = TOP_ROW_Y + (i / COLS) * PITCH_Y;
      float[] from = previous.get(items.get(i).packageName);
      if (from != null && positionProgress < 1f) {
        x = from[0] + (x - from[0]) * positionProgress;
        y = from[1] + (y - from[1]) * positionProgress;
      }
      oldPositions.put(items.get(i).packageName, new float[] {x, y});
    }
    items.clear();
    items.addAll(projected);
    fitLabels();
    if (focusKey != null)
      for (int i = 0; i < items.size(); i++)
        if (focusKey.equals(items.get(i).packageName)) {
          focusIndex = i;
          break;
        }
    clampFocus();
    animateSelection();
    animateScrollTo(targetScrollFor(focusIndex / COLS));
    if (positionAnimator != null)
      positionAnimator.cancel();
    positionProgress = 0;
    positionAnimator = ValueAnimator.ofFloat(0, 1);
    positionAnimator.setDuration(300);
    positionAnimator.setInterpolator(new DecelerateInterpolator(2));
    positionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator a) {
        positionProgress = (Float) a.getAnimatedValue();
        invalidate();
      }
    });
    positionAnimator.start();
  }
  void animateSelection() {
    if (focusAnimator != null)
      focusAnimator.cancel();
    final Map<String, Float> from = new HashMap<String, Float>(focusWeights);
    final String selected =
        ui.chromeFocused ? null : (focusedItem() == null ? null : focusedItem().packageName);
    focusAnimator = ValueAnimator.ofFloat(0, 1);
    focusAnimator.setDuration(360);
    focusAnimator.setInterpolator(new android.animation.TimeInterpolator() {
      @Override
      public float getInterpolation(float t) {
        double u = t * 9;
        return (float) ((1 - (1 + u) * Math.exp(-u)) / (1 - 10 * Math.exp(-9)));
      }
    });
    focusAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator a) {
        float t = (Float) a.getAnimatedValue();
        for (AppEntry e : items) {
          Float old = from.get(e.packageName);
          float v = old == null ? 0 : old;
          float target = e.packageName.equals(selected) ? 1 : 0;
          focusWeights.put(e.packageName, v + (target - v) * t);
        }
        invalidate();
      }
    });
    focusAnimator.start();
  }
  private void animatePress(float target) {
    if (pressAnimator != null)
      pressAnimator.cancel();
    pressAnimator = ValueAnimator.ofFloat(pressScale, target);
    pressAnimator.setDuration(110);
    pressAnimator.setInterpolator(new DecelerateInterpolator(2));
    pressAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator a) {
        pressScale = (Float) a.getAnimatedValue();
        invalidate();
      }
    });
    pressAnimator.start();
  }
  private void edgeNudge(final int x, final int y) {
    if (edgeAnimator != null)
      edgeAnimator.cancel();
    edgeAnimator = ValueAnimator.ofFloat(0, 1, 0);
    edgeAnimator.setDuration(190);
    edgeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator a) {
        float v = (Float) a.getAnimatedValue();
        edgeX = x * 4 * v;
        edgeY = y * 4 * v;
        invalidate();
      }
    });
    edgeAnimator.start();
  }

  // ---- clock ----

  private void updateClock() {
    Date now = new Date();
    clockText = new java.text.SimpleDateFormat("HH:mm", Locale.US).format(now);
    try {
      DateFormat dateFormat = DateFormat.getDateInstance(DateFormat.FULL, Locale.getDefault());
      dateText = dateFormat.format(now);
    } catch (RuntimeException ignored) {
      dateText = "";
    }
    handler.removeCallbacks(tick);
    long delay = 60000L - (System.currentTimeMillis() % 60000L) + 250L;
    if (shown)
      handler.postDelayed(tick, delay);
    invalidate();
  }

  // ---- model ----

  static final class AppEntry {
    final String packageName;
    final String activityName;
    final String label;
    String displayLabel;
    final Bitmap art;

    AppEntry(String packageName, String activityName, String label, Bitmap art) {
      this.packageName = packageName;
      this.activityName = activityName;
      this.label = label;
      this.displayLabel = label;
      this.art = art;
    }
  }
}
