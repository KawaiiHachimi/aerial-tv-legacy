package app.aerial.legacy;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.Toast;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** API18 overlay state, persisted organization and remote interactions, using upstream geometry. */
final class DesktopUi {
  static final int NONE = 0, APP = 1, CONTROL = 2, PERSONAL = 3, WALLPAPERS = 4, HIDDEN = 5,
                   TO_FOLDER = 6, FOLDER = 7, SAVER = 8;
  final HomeView home;
  final DesktopLayout layout = new DesktopLayout();
  final SharedPreferences prefs;
  final WallpaperStore wallpapers;
  final PhoneSetup phone;
  final Map<String, HomeView.AppEntry> catalog = new LinkedHashMap<String, HomeView.AppEntry>();
  final Handler handler = new Handler(Looper.getMainLooper());
  int mode, index, column;
  int networkKind;
  private boolean networkRegistered;
  private final android.content.BroadcastReceiver networkReceiver = new android.content.BroadcastReceiver() {
    @Override public void onReceive(Context context, Intent intent) { refreshNetwork(); }
  };
  void refreshNetwork() {
    networkKind = NetworkStatus.read(home.getContext());
    home.invalidate();
  }

  boolean chromeFocused, moving, closed, closing, active;
  String movingSnapshot, openFolder, moveKey;
  private HomeView.AppEntry menuEntry;
  private RectF menuAnchor;
  private int returnMode;
  private float progress;
  private ValueAnimator panelAnimator;
  private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
  private final RectF bounds = new RectF();
  private final Bitmap[] thumbs = new Bitmap[Gradients.COUNT];
  private Bitmap frost, dailyThumb;
  private boolean previewsLoading, previewsAgain;
  private boolean armed;
  private int armedCode = -1, wakeCode = -1, saverEntryKey = -1;
  private long wakeUntil;
  private final Runnable folderLongPress = new Runnable() {
    @Override
    public void run() {
      if (armed && mode == FOLDER) {
        armed = false;
        appMenu();
      }
    }
  };
  private final Runnable idle = new Runnable() {
    @Override
    public void run() {
      if (mode == NONE && !moving)
        showSaver();
    }
  };

  DesktopUi(HomeView owner) {
    home = owner;
    prefs = home.getContext().getSharedPreferences("desktop-v3", Context.MODE_PRIVATE);
    wallpapers = new WallpaperStore(home.getContext(), prefs, handler, new Runnable() {
      @Override public void run() {
        if (!closed) {
          home.changeWallpaper();
          if (mode == WALLPAPERS) loadThumbs();
        }
      }
    });
    phone = new PhoneSetup(this);
    layout.decode(prefs.getString("layout", ""));
    text.setTypeface(AppFonts.regular(home.getContext()));
  }
  String s(int id) {
    return home.getContext().getString(id);
  }
  int preset() {
    return prefs.getInt("wallpaper", Gradients.DEFAULT_PRESET);
  }
  int nameMode() {
    int value = prefs.getInt("name_mode", prefs.getBoolean("names", false) ? 0 : 1);
    return Math.max(0, Math.min(2, value));
  }
  boolean names() { return nameMode() == 0; }
  boolean showName(boolean selected) { return nameMode() == 0 || (nameMode() == 1 && selected); }
  boolean contrast() {
    return prefs.getBoolean("contrast", true);
  }
  boolean plate() {
    return prefs.getBoolean("plate", true);
  }
  boolean largeClock() {
    return prefs.getBoolean("large_clock", false);
  }
  void save() {
    prefs.edit().putString("layout", layout.encode()).apply();
  }
  void onShown() {
    closed = false;
    active = true;
    refreshNetwork();
    if (wallpapers.source().equals("bing")) wallpapers.refresh(false);
    if (!networkRegistered) {
      home.getContext().registerReceiver(networkReceiver,
          new android.content.IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
      networkRegistered = true;
    }
    resetIdle();
  }
  void onHidden() {
    active = false;
    phone.stop();
    if (networkRegistered) {
      home.getContext().unregisterReceiver(networkReceiver);
      networkRegistered = false;
    }
    handler.removeCallbacks(idle);
    armed = false;
    armedCode = -1;
  }
  void release() {
    onHidden();
    closed = true;
    wallpapers.close();
    handler.removeCallbacksAndMessages(null);
    if (panelAnimator != null)
      panelAnimator.cancel();
  }
  void setFrost(Bitmap bitmap) {
    frost = bitmap;
  }
  void resetIdle() {
    handler.removeCallbacks(idle);
    int minutes = prefs.getInt("idle", 2);
    if (minutes > 0 && !closed && active)
      handler.postDelayed(idle, minutes * 60000L);
  }
  ArrayList<HomeView.AppEntry> project(List<HomeView.AppEntry> source) {
    catalog.clear();
    ArrayList<String> packages = new ArrayList<String>();
    for (HomeView.AppEntry e : source) {
      catalog.put(e.packageName, e);
      packages.add(e.packageName);
    }
    layout.reconcile(packages);
    save();
    return projected();
  }
  ArrayList<HomeView.AppEntry> projected() {
    ArrayList<HomeView.AppEntry> result = new ArrayList<HomeView.AppEntry>();
    for (DesktopLayout.Entry e : layout.entries) {
      if (!e.folder()) {
        HomeView.AppEntry a = catalog.get(e.key);
        if (a != null)
          result.add(a);
      } else
        result.add(new HomeView.AppEntry(e.key, "", e.name, home.folderArt(e, catalog)));
    }
    return result;
  }
  void refresh(String key) {
    home.applyLayout(projected(), key);
    save();
    home.invalidate();
  }
  HomeView.AppEntry selected() {
    return mode == FOLDER ? folderItem() : home.selectedItem();
  }
  private HomeView.AppEntry folderItem() {
    DesktopLayout.Entry f = layout.find(openFolder);
    if (f == null || f.members.isEmpty())
      return null;
    index = Math.max(0, Math.min(index, f.members.size() - 1));
    return catalog.get(f.members.get(index));
  }
  boolean launchSelection(HomeView.AppEntry e) {
    if (e == null)
      return true;
    if (e.packageName.startsWith("folder-")) {
      openFolder = e.packageName;
      open(FOLDER, 0);
      return true;
    }
    return false;
  }
  private void open(int target, int at) {
    if (panelAnimator != null)
      panelAnimator.cancel();
    mode = target;
    if (target == CONTROL) refreshNetwork();
    index = at;
    closing = false;
    armed = false;
    armedCode = -1;
    progress = 0f;
    animatePanel(1f);
    handler.removeCallbacks(idle);
    home.invalidate();
    android.util.Log.i("AerialUi", "open " + mode);
  }
  void close() {
    if (mode == NONE)
      return;
    closing = true;
    armed = false;
    armedCode = -1;
    animatePanel(0f);
  }
  private void animatePanel(float end) {
    if (panelAnimator != null)
      panelAnimator.cancel();
    panelAnimator = ValueAnimator.ofFloat(progress, end);
    panelAnimator.setDuration(end == 1 ? 220 : 120);
    panelAnimator.setInterpolator(new DecelerateInterpolator(2f));
    panelAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
      @Override
      public void onAnimationUpdate(ValueAnimator a) {
        progress = (Float) a.getAnimatedValue();
        home.invalidate();
      }
    });
    panelAnimator.addListener(new AnimatorListenerAdapter() {
      @Override
      public void onAnimationEnd(Animator a) {
        if (closing && progress < .01f) {
          mode = NONE;
          closing = false;
          home.invalidate();
          resetIdle();
        }
      }
    });
    panelAnimator.start();
  }
  void control() {
    open(CONTROL, 0);
  }
  void appMenu() {
    HomeView.AppEntry e = selected();
    if (e == null)
      return;
    returnMode = mode == FOLDER ? FOLDER : NONE;
    menuAnchor = mode == FOLDER ? folderAnchor() : home.selectedBounds();
    menuEntry = e;
    open(APP, 0);
  }
  void showSaver() {
    chromeFocused = false;
    open(SAVER, 0);
  }
  void showSaverFromKey(int key) {
    saverEntryKey = key;
    showSaver();
  }
  void resetHome() {
    if (panelAnimator != null)
      panelAnimator.cancel();
    mode = NONE;
    closing = false;
    chromeFocused = false;
    saverEntryKey = -1;
    wakeCode = -1;
    wakeUntil = 0;
    if (moving) {
      layout.decode(movingSnapshot);
      moving = false;
      refresh(moveKey);
    }
    home.firstRow();
    resetIdle();
  }
  private ArrayList<HomeView.AppEntry> hidden() {
    ArrayList<HomeView.AppEntry> result = new ArrayList<HomeView.AppEntry>();
    for (String p : layout.hidden) {
      HomeView.AppEntry a = catalog.get(p);
      if (a != null)
        result.add(a);
    }
    return result;
  }
  private ArrayList<DesktopLayout.Entry> folders() {
    ArrayList<DesktopLayout.Entry> fs = new ArrayList<DesktopLayout.Entry>();
    for (DesktopLayout.Entry e : layout.entries)
      if (e.folder() && !(returnMode == FOLDER && e.key.equals(openFolder)))
        fs.add(e);
    return fs;
  }
  private String[] appRows() {
    boolean folder = menuEntry != null && menuEntry.packageName.startsWith("folder-");
    if (folder)
      return new String[] {
          s(R.string.menu_move), s(R.string.menu_rename_folder), s(R.string.menu_ungroup)};
    ArrayList<String> rows = new ArrayList<String>();
    rows.add(s(returnMode == FOLDER ? R.string.menu_out_of_folder : R.string.menu_move));
    rows.add(s(R.string.menu_to_folder));
    rows.add(s(R.string.menu_hide));
    rows.add(s(R.string.action_app_info));
    if (menuEntry != null && home.removable(menuEntry.packageName))
      rows.add(s(R.string.menu_uninstall));
    return rows.toArray(new String[rows.size()]);
  }
  /** Returns true only for events owned by the overlay/chrome/move mode. */
  boolean key(KeyEvent event) {
    int code = event.getKeyCode(), action = event.getAction();
    if (saverEntryKey == code) {
      if (action == KeyEvent.ACTION_UP)
        saverEntryKey = -1;
      return true;
    }
    if (mode == SAVER || wakeCode == code || android.os.SystemClock.uptimeMillis() < wakeUntil) {
      if (mode == SAVER) {
        mode = NONE;
        progress = 0;
        wakeCode = code;
        wakeUntil = android.os.SystemClock.uptimeMillis() + 300;
        home.invalidate();
        resetIdle();
      }
      if (action == KeyEvent.ACTION_UP)
        wakeCode = -1;
      return true;
    }
    resetIdle();
    if (moving) {
      if (action == KeyEvent.ACTION_DOWN) {
        if (code == KeyEvent.KEYCODE_BACK) {
          layout.decode(movingSnapshot);
          moving = false;
          refresh(moveKey);
        } else if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
          int from = home.focusedIndex(),
              to = from
              + (code == 21        ? -1
                      : code == 22 ? 1
                      : code == 19 ? -5
                                   : 5);
          if (to >= 0 && to < layout.entries.size()
              && !((code == 21 || code == 22) && from / 5 != to / 5)) {
            layout.move(from, to);
            refresh(moveKey);
          }
        } else if ((code == 23 || code == 66) && event.getRepeatCount() == 0) {
          armed = true;
          armedCode = code;
        }
      } else if (action == KeyEvent.ACTION_UP && (code == 23 || code == 66) && armed
          && armedCode == code) {
        moving = false;
        armed = false;
        save();
        home.invalidate();
      }
      return isNavigation(code);
    }
    if (mode == NONE) {
      if (chromeFocused) {
        if (action == KeyEvent.ACTION_DOWN && (code == 20 || code == 4)) {
          chromeFocused = false;
          home.animateSelection();
          home.invalidate();
        } else if (action == KeyEvent.ACTION_DOWN && (code == 23 || code == 66)
            && event.getRepeatCount() == 0) {
          armed = true;
          armedCode = code;
        } else if (action == KeyEvent.ACTION_UP && (code == 23 || code == 66) && armed
            && armedCode == code) {
          armed = false;
          control();
        }
        return isNavigation(code);
      }
      return false;
    }
    if (closing)
      return isNavigation(code);
    if (action == KeyEvent.ACTION_DOWN) {
      if (code == 82 && mode == FOLDER && event.getRepeatCount() == 0) {
        appMenu();
        return true;
      }
      if (code == 4 || code == 82) {
        if (mode == TO_FOLDER) {
          open(APP, 1);
          return true;
        }
        if (mode == APP && returnMode == FOLDER) {
          open(FOLDER, 0);
          return true;
        }
        if (mode == WALLPAPERS || mode == HIDDEN) {
          open(PERSONAL, mode == WALLPAPERS ? 0 : 5);
          return true;
        }
        if (mode == PERSONAL) {
          open(CONTROL, 5);
          return true;
        }
        close();
        return true;
      }
      if (code == 23 || code == 66) {
        if (event.getRepeatCount() == 0) {
          armed = true;
          armedCode = code;
          if (mode == FOLDER) {
            handler.removeCallbacks(folderLongPress);
            handler.postDelayed(folderLongPress, 650);
          }
        }
        return true;
      }
      if (code >= 19 && code <= 22) {
        navigate(code);
        home.invalidate();
        return true;
      }
    } else if (action == KeyEvent.ACTION_UP && (code == 23 || code == 66)) {
      handler.removeCallbacks(folderLongPress);
      if (armed && armedCode == code) {
        armed = false;
        armedCode = -1;
        activate();
      }
      return true;
    }
    return isNavigation(code);
  }
  private static boolean isNavigation(int c) {
    return c == 4 || c == 82 || (c >= 19 && c <= 23) || c == 66;
  }
  private void navigate(int c) {
    if (mode == CONTROL) {
      if (index < 3) {
        if (c == 21)
          index = Math.max(0, index - 1);
        else if (c == 22)
          index = Math.min(2, index + 1);
        else if (c == 20)
          index = 3;
      } else if (c == 19)
        index = index == 3 ? 0 : index - 1;
      else if (c == 20)
        index = Math.min(6, index + 1);
      else if (index == 3 && (c == 21 || c == 22))
        changeVolume(c == 22 ? 1 : -1);
      return;
    }
    int count = mode == APP  ? appRows().length
        : mode == PERSONAL   ? 11
        : mode == WALLPAPERS ? Gradients.COUNT + 2
        : mode == HIDDEN     ? hidden().size()
        : mode == TO_FOLDER  ? folders().size() + 1
                             : folderCount();
    if (count == 0)
      return;
    int columns = mode == WALLPAPERS ? 5 : mode == FOLDER || mode == HIDDEN ? 4 : 1;
    int target = index;
    if (c == 19)
      target = index - columns;
    else if (c == 20)
      target = index + columns;
    else if (c == 21 && columns > 1 && index % columns > 0)
      target = index - 1;
    else if (c == 22 && columns > 1 && index % columns < columns - 1)
      target = index + 1;
    if (mode == PERSONAL && (c == 21 || c == 22)) {
      adjustPersonal(c == 22 ? 1 : -1);
      return;
    }
    if (mode == WALLPAPERS && c == KeyEvent.KEYCODE_DPAD_DOWN && index / columns < (count - 1) / columns)
      target = Math.min(target, count - 1);
    if (target >= 0 && target < count)
      index = target;
  }
  private int folderCount() {
    DesktopLayout.Entry f = layout.find(openFolder);
    return f == null ? 0 : f.members.size();
  }
  private void activate() {
    if (mode == APP) {
      activateApp();
      return;
    }
    if (mode == CONTROL) {
      switch (index) {
        case 0:
          if (!LegacyInputs.open(home.getContext())) {
          home.openPackages(new String[] {"mstar.tvsetting.ui"}, Settings.ACTION_SETTINGS);
        }
          close();
          break;
        case 1:
          home.openPackages(
              new String[] {"com.stv.t2.globalsetting"}, Settings.ACTION_WIFI_SETTINGS);
          close();
          break;
        case 2:
        case 4:
          home.openPackages(new String[] {"com.stv.t2.globalsetting", "mstar.tvsetting.ui"},
              Settings.ACTION_SETTINGS);
          close();
          break;
        case 3:
          changeVolume(1);
          break;
        case 5:
          open(PERSONAL, 0);
          break;
        case 6:
          showSaver();
          break;
      }
      return;
    }
    if (mode == PERSONAL) {
      adjustPersonal(1);
      return;
    }
    if (mode == WALLPAPERS) {
      if (index == Gradients.COUNT + 1) { phone.show(); return; }
      wallpapers.select(index == Gradients.COUNT ? "bing" : "builtin",
          index < Gradients.COUNT ? index : preset());
      open(PERSONAL, 0);
      return;
    }
    if (mode == HIDDEN) {
      ArrayList<HomeView.AppEntry> list = hidden();
      if (!list.isEmpty()) {
        String p = list.get(Math.min(index, list.size() - 1)).packageName;
        layout.show(p);
        refresh(p);
        index = Math.max(0, Math.min(index, hidden().size() - 1));
      }
      home.invalidate();
      return;
    }
    if (mode == TO_FOLDER) {
      ArrayList<DesktopLayout.Entry> fs = folders();
      String target = index == 0 ? null : fs.get(index - 1).key;
      String f = layout.folder(menuEntry.packageName, target, s(R.string.folder_default));
      save();
      refresh(f);
      openFolder = f;
      open(FOLDER, 0);
      return;
    }
    if (mode == FOLDER) {
      HomeView.AppEntry e = folderItem();
      if (e != null)
        home.launchEntry(e);
      return;
    }
  }
  private void activateApp() {
    if (menuEntry == null)
      return;
    String key = menuEntry.packageName;
    if (key.startsWith("folder-")) {
      if (index == 0)
        beginMove(key);
      else if (index == 1)
        renameFolder(key);
      else {
        layout.ungroup(key);
        refresh(null);
        close();
      }
      return;
    }
    if (index == 0) {
      if (returnMode == FOLDER) {
        layout.takeOut(key);
        refresh(key);
        close();
      } else
        beginMove(key);
    } else if (index == 1)
      open(TO_FOLDER, 0);
    else if (index == 2) {
      layout.hide(key);
      refresh(null);
      close();
    } else if (index == 3) {
      home.appInfo(key);
      close();
    } else {
      home.startIntent(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + key)));
      close();
    }
  }
  private void beginMove(String key) {
    movingSnapshot = layout.encode();
    moveKey = key;
    moving = true;
    mode = NONE;
    closing = false;
    if (panelAnimator != null)
      panelAnimator.cancel();
    home.invalidate();
  }
  private void renameFolder(final String key) {
    final DesktopLayout.Entry e = layout.find(key);
    if (e == null)
      return;
    final EditText input = new EditText(home.getContext());
    input.setTypeface(AppFonts.regular(home.getContext()));
    input.setSingleLine(true);
    input.setText(e.name);
    input.selectAll();
    AlertDialog dialog = new AlertDialog.Builder(home.getContext())
                             .setTitle(R.string.menu_rename_folder)
                             .setView(input)
                             .setPositiveButton(android.R.string.ok,
                                 new DialogInterface.OnClickListener() {
                                   @Override
                                   public void onClick(DialogInterface d, int w) {
                                     String name = input.getText().toString().trim();
                                     if (!name.isEmpty()) {
                                       e.name = name;
                                       refresh(key);
                                     }
                                   }
                                 })
                             .setNegativeButton(android.R.string.cancel, null)
                             .create();
    dialog.show();
    if (dialog.getWindow() != null)
      AppFonts.apply(dialog.getWindow().getDecorView());
    close();
  }
  private void adjustPersonal(int direction) {
    switch (index) {
      case 0:
        open(WALLPAPERS, preset());
        loadThumbs();
        return;
      case 1:
        toggle("large_clock", false);
        break;
      case 2:
        toggle("contrast", true);
        break;
      case 3:
        prefs.edit().putInt("name_mode", (nameMode() + 3 + direction) % 3).apply();
        break;
      case 4:
        toggle("plate", true);
        break;
      case 5:
        open(HIDDEN, 0);
        return;
      case 6:
        int[] values = {0, 1, 2, 5, 10};
        int now = prefs.getInt("idle", 2), at = 0;
        for (int i = 0; i < values.length; i++)
          if (values[i] == now)
            at = i;
        prefs.edit()
            .putInt("idle", values[(at + values.length + direction) % values.length])
            .apply();
        break;
      case 7:
        showSaver();
        return;
      case 8:
        toggle("sounds", false);
        break;
      case 9:
        phone.show();
        return;
      case 10:
        AppFonts.toast(home.getContext(), R.string.compatibility_note, Toast.LENGTH_LONG).show();
        break;
    }
    home.invalidate();
  }
  private void toggle(String key, boolean fallback) {
    prefs.edit().putBoolean(key, !prefs.getBoolean(key, fallback)).apply();
  }
  private AudioManager audio() {
    return (AudioManager) home.getContext().getSystemService(Context.AUDIO_SERVICE);
  }
  private void changeVolume(int delta) {
    AudioManager a = audio();
    if (a != null)
      a.adjustStreamVolume(AudioManager.STREAM_MUSIC, delta > 0 ? 1 : -1, 0);
    home.invalidate();
  }
  private String network() { return s(NetworkStatus.label(networkKind)); }
  private void loadThumbs() {
    if (closed) return;
    if (previewsLoading) { previewsAgain = true; return; }
    previewsLoading = true;
    wallpapers.refresh(false);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
        for (int i = 0; i < thumbs.length; i++) {
          if (closed)
            return;
          if (thumbs[i] == null) {
            final int at = i;
            final Bitmap b = Gradients.render(i, 192, 108);
            handler.post(new Runnable() {
              @Override
              public void run() {
                if (!closed) {
                  thumbs[at] = b;
                  home.invalidate();
                }
              }
            });
          }
        }
        final Bitmap daily = wallpapers.preview("bing");
        handler.post(new Runnable() { @Override public void run() {
          if (!closed) { dailyThumb = daily; home.invalidate(); }
          else if (daily != null) daily.recycle();
        }});
        } finally { handler.post(new Runnable() { @Override public void run() { previewsLoading = false; if (previewsAgain) { previewsAgain = false; loadThumbs(); } } }); }
      }
    }, "aerial-previews").start();
  }

  void draw(Canvas canvas) {
    if (mode == NONE) {
      if (moving)
        banner(canvas, s(R.string.move_hint));
      return;
    }
    if (mode == SAVER) {
      drawSaver(canvas);
      return;
    }
    if (mode == APP || mode == TO_FOLDER)
      drawAppMenu(canvas);
    else if (mode == CONTROL)
      drawControl(canvas);
    else if (mode == PERSONAL || mode == WALLPAPERS)
      drawPersonal(canvas);
    else
      drawCollection(canvas);
  }
  private void beginPanel(Canvas c, RectF b, boolean bottom) {
    c.save();
    float p = progress;
    if (bottom)
      c.translate(0, (1 - p) * 90);
    else {
      float k = .92f + .08f * p;
      float pivotX = b.left + 30, pivotY = b.bottom;
      c.scale(k, k, pivotX, pivotY);
    }
    paint.setAlpha(Math.round(p * 255));
    c.save();
    Path path = new Path();
    path.addRoundRect(b, 20, 20, Path.Direction.CW);
    c.clipPath(path);
    if (frost != null)
      c.drawBitmap(frost, null, new RectF(0, 0, 960, 540), paint);
    paint.setColor(Color.argb(Math.round(p * 218), 30, 30, 36));
    c.drawRect(b, paint);
    c.restore();
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeWidth(1);
    paint.setColor(Color.argb(Math.round(p * 45), 255, 255, 255));
    c.drawRoundRect(b, 20, 20, paint);
    paint.setStyle(Paint.Style.FILL);
    paint.setAlpha(255);
    text.setAlpha(Math.round(p * 255));
  }
  private void endPanel(Canvas c) {
    paint.setAlpha(255);
    text.setAlpha(255);
    c.restore();
  }
  private void line(Canvas c, String value, float x, float baseline, float size, int color) {
    text.setTypeface(
        size >= 19 ? AppFonts.medium(home.getContext()) : AppFonts.regular(home.getContext()));
    text.setTextSize(size);
    text.setColor(color);
    text.setAlpha(Math.round(progress * 255));
    c.drawText(value, x, baseline, text);
  }
  private void row(Canvas c, String label, int glyph, float x, float y, float w, boolean focused,
      boolean danger, String trailing) {
    if (focused) {
      paint.setColor(0xFFF5F5F7);
      c.drawRoundRect(new RectF(x, y, x + w, y + 44), 12, 12, paint);
    }
    int color = focused ? 0xFF1D1D1F : danger ? 0xFFFF453A : 0xFFFFFFFF;
    icon(c, glyph, x + 15, y + 14, 16, color);
    text.setTextSize(15);
    String shown = TextUtils.ellipsize(label, text, w - 70, TextUtils.TruncateAt.END).toString();
    line(c, shown, x + 43, y + 28, 15, color);
    if (trailing != null)
      line(c, trailing, x + w - 23, y + 28, 17, color);
  }
  private void drawAppMenu(Canvas c) {
    String[] rows;
    String title;
    if (mode == APP) {
      rows = appRows();
      title = menuEntry == null ? "" : menuEntry.label;
    } else {
      ArrayList<DesktopLayout.Entry> fs = folders();
      rows = new String[fs.size() + 1];
      rows[0] = s(R.string.menu_new_folder);
      for (int i = 0; i < fs.size(); i++) rows[i + 1] = fs.get(i).name;
      title = s(R.string.menu_to_folder);
    }
    int start = Math.max(0, index - 5), visible = Math.min(6, rows.length - start);
    float h = 42 + visible * 44 + 8;
    RectF anchor = menuAnchor == null ? home.selectedBounds() : menuAnchor;
    float x = Math.max(24, Math.min(anchor.left, 960 - 250 - 24));
    float y =
        anchor.bottom + 18 + h <= 516 ? anchor.bottom + 18 : Math.max(24, anchor.top - 18 - h);
    bounds.set(x, y, x + 250, y + h);
    beginPanel(c, bounds, false);
    text.setTextSize(15);
    title = TextUtils.ellipsize(title, text, 210, TextUtils.TruncateAt.END).toString();
    line(c, title, x + 22, y + 29, 15, 0xFFCCCCD1);
    for (int i = 0; i < visible; i++) {
      int n = i + start;
      boolean danger = mode == APP && rows[n].equals(s(R.string.menu_uninstall));
      int glyph = mode == TO_FOLDER ? 1 : n == 0 ? 0 : n == 1 ? 1 : n == 2 ? 2 : n == 3 ? 3 : 4;
      row(c, rows[n], glyph, x + 8, y + 42 + i * 44, 234, n == index, danger,
          mode == APP && n == 1 ? "›" : null);
    }
    endPanel(c);
  }
  private void drawControl(Canvas c) {
    bounds.set(568, 24, 928, 488);
    beginPanel(c, bounds, false);
    String date = DateFormat.getDateInstance(DateFormat.FULL).format(new Date());
    // Align the header with the visible left edge of the controls below (row inset + icon inset).
    float contentLeft = 590f + 15f;
    line(c, date, contentLeft, 61, 15, 0xFFFFFFFF);
    line(c, network(), contentLeft, 85, 12, 0xFFB8B8C0);
    int[] labels = {R.string.cc_inputs, R.string.cc_network, R.string.cc_settings};
    for (int i = 0; i < 3; i++) {
      float x = contentLeft + 29f + i * 110;
      boolean f = index == i;
      paint.setColor(f ? 0xFFF5F5F7 : 0xFF4A4A52);
      c.drawCircle(x, 136, 29, paint);
      icon(c, 5 + i, x - 12, 124, 24, f ? 0xFF1D1D1F : 0xFFFFFFFF);
      text.setTextSize(12);
      String value = s(labels[i]);
      line(c, value, x - text.measureText(value) / 2, 186, 12, 0xFFFFFFFF);
    }
    AudioManager a = audio();
    int volume = a == null ? 0 : a.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = a == null ? 1 : a.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
    row(c, s(R.string.cc_volume), 8, 590, 209, 316, index == 3, false, null);
    paint.setColor(index == 3 ? 0xFFCCCCD1 : 0xFF55555D);
    c.drawRoundRect(new RectF(731, 228, 878, 234), 3, 3, paint);
    paint.setColor(index == 3 ? 0xFF1D1D1F : 0xFFFFFFFF);
    c.drawRoundRect(new RectF(731, 228, 731 + 147f * volume / Math.max(1, max), 234), 3, 3, paint);
    if (index == 3) line(c, "‹  " + volume + " / " + max + "  ›", 731, 250, 10, 0xFFB8B8C0);
    row(c, s(R.string.cc_picture), 9, 590, 261, 316, index == 4, false, "›");
    row(c, s(R.string.cc_personalize), 10, 590, 313, 316, index == 5, false, "›");
    row(c, s(R.string.saver_preview), 11, 590, 365, 316, index == 6, false, null);
    paint.setColor(0xFF30D158);
    c.drawCircle(605, 447, 3, paint);
    line(c, s(home.isDefaultHome() ? R.string.home_default : R.string.home_available), 616, 452, 11,
        0xFFB8B8C0);
    line(c, "Aerial 1.4.1", 832, 452, 11, 0xFF9999A3);
    endPanel(c);
  }
  private String[] personalLabels() {
    return new String[] {s(R.string.pref_wallpaper), s(R.string.pref_clock), s(R.string.pref_focus),
        s(R.string.pref_names), s(R.string.pref_plate), s(R.string.pref_hidden),
        s(R.string.pref_idle), s(R.string.saver_preview), s(R.string.pref_sounds),
        s(R.string.phone_setup), s(R.string.pref_about)};
  }
  private String personalValue(int at) {
    switch (at) {
      case 0:
        return wallpapers.source().equals("builtin") ? s(R.string.wallpaper_names).split("\\|")[preset()]
            : s(wallpapers.source().equals("bing") ? R.string.bing_daily : R.string.phone_photo);
      case 1:
        return s(largeClock() ? R.string.clock_large : R.string.clock_corner);
      case 2:
        return s(contrast() ? R.string.focus_contrast : R.string.focus_scale);
      case 3:
        return s(nameMode() == 0 ? R.string.names_all : nameMode() == 1 ? R.string.names_selected : R.string.names_none);
      case 4:
        return s(plate() ? R.string.option_on : R.string.option_off);
      case 5:
        return String.valueOf(layout.hidden.size());
      case 6:
        int m = prefs.getInt("idle", 2);
        return m == 0 ? s(R.string.option_never) : String.valueOf(m) + s(R.string.minutes);
      case 8:
        return s(prefs.getBoolean("sounds", false) ? R.string.option_on : R.string.option_off);
      default:
        return "›";
    }
  }
  private void drawPersonal(Canvas c) {
    bounds.set(0, 180, 960, 560);
    beginPanel(c, bounds, true);
    line(c, s(mode == WALLPAPERS ? R.string.pref_wallpaper : R.string.cc_personalize), 40, 220, 23,
        0xFFFFFFFF);
    if (mode == WALLPAPERS) {
      String[] labels = s(R.string.wallpaper_names).split("\\|");
      int first = Math.max(0, index / 5 - 2) * 5;
      for (int i = first; i < Math.min(first + 15, Gradients.COUNT + 2); i++) {
        float x = 40 + (i % 5) * 179, y = 239 + ((i - first) / 5) * 96;
        Bitmap preview = i < Gradients.COUNT ? thumbs[i] : i == Gradients.COUNT ? dailyThumb : null;
        if (preview != null) {
          c.save();
          Path path = new Path();
          path.addRoundRect(new RectF(x, y, x + 155, y + 70), 9, 9, Path.Direction.CW);
          c.clipPath(path);
          int cropHeight = Math.min(preview.getHeight(), Math.round(preview.getWidth() * 70f / 155f));
          int top = (preview.getHeight() - cropHeight) / 2;
          c.drawBitmap(preview, new android.graphics.Rect(0, top, preview.getWidth(), top + cropHeight),
              new RectF(x, y, x + 155, y + 70), paint);
          c.restore();
        } else {
          paint.setColor(0xFF43435B);
          c.drawRoundRect(new RectF(x, y, x + 155, y + 70), 9, 9, paint);
        }
        if (i == index) {
          paint.setColor(0xFFFFFFFF);
          paint.setStyle(Paint.Style.STROKE);
          paint.setStrokeWidth(2);
          c.drawRoundRect(new RectF(x - 4, y - 4, x + 159, y + 74), 12, 12, paint);
          paint.setStyle(Paint.Style.FILL);
        }
        boolean selectedSource = i < Gradients.COUNT
            ? wallpapers.source().equals("builtin") && i == preset()
            : i == Gradients.COUNT && wallpapers.source().equals("bing");
        if (selectedSource) {
          paint.setColor(0xFFF5F5F7);
          c.drawCircle(x + 141, y + 13, 8, paint);
          icon(c, 12, x + 136, y + 8, 10, 0xFF22222A);
        }
        if (i >= Gradients.COUNT && preview == null) icon(c, i == Gradients.COUNT ? 10 : 7, x+61, y+19, 32, 0xFFFFFFFF);
        line(c, i < Gradients.COUNT ? labels[i] : s(i == Gradients.COUNT ? R.string.bing_daily : R.string.phone_setup),
            x, y + 88, 12, 0xFFFFFFFF);
      }
    } else {
      String[] labels = personalLabels();
      int first = Math.max(0, Math.min(index - 4, labels.length - 6));
      for (int i = first; i < Math.min(first + 6, labels.length); i++) {
        float y = 236 + (i - first) * 47;
        boolean selected = i == index;
        if (selected) {
          paint.setColor(0xFFF5F5F7);
          c.drawRoundRect(new RectF(40, y, 643, y + 43), 11, 11, paint);
        }
        int col = selected ? 0xFF1D1D1F : 0xFFFFFFFF;
        line(c, labels[i], 58, y + 28, 15, col);
        text.setTextSize(13);
        String value = personalValue(i);
        line(c, value, 624 - text.measureText(value), y + 27, 13, col);
      }
      line(c, s(R.string.remote_hint), 685, 267, 15, 0xFFFFFFFF);
      String[] hints = s(R.string.personal_hint).split("\\|");
      for (int i = 0; i < hints.length; i++) line(c, hints[i], 685, 302 + i * 25, 13, 0xFFC8C8D0);
      line(c, s(R.string.compatibility_short), 685, 474, 11, 0xFF9999A3);
    }
    endPanel(c);
  }
  private RectF folderAnchor() {
    int n = Math.max(0, index);
    int firstRow = Math.max(0, n / 4 - 1);
    float x = 187 + (n % 4) * 151, y = 200 + (n / 4 - firstRow) * 114;
    return new RectF(x, y, x + 132, y + 74);
  }
  private void drawCollection(Canvas c) {
    ArrayList<HomeView.AppEntry> list = new ArrayList<HomeView.AppEntry>();
    String title;
    if (mode == HIDDEN) {
      list = hidden();
      title = s(R.string.pref_hidden);
    } else {
      DesktopLayout.Entry e = layout.find(openFolder);
      title = e == null ? "" : e.name;
      if (e != null)
        for (String p : e.members) {
          HomeView.AppEntry a = catalog.get(p);
          if (a != null)
            list.add(a);
        }
    }
    bounds.set(150, 112, 810, 482);
    beginPanel(c, bounds, false);
    line(c, title, 178, 151, 22, 0xFFFFFFFF);
    line(c, s(mode == HIDDEN ? R.string.hidden_hint : R.string.folder_hint), 178, 178, 12,
        0xFFB8B8C0);
    if (list.isEmpty())
      line(c, s(R.string.empty_collection), 178, 258, 17, 0xFFB8B8C0);
    int firstRow = Math.max(0, index / 4 - 1), first = firstRow * 4;
    for (int n = first; n < Math.min(list.size(), first + 8); n++) {
      int slot = n - first;
      float x = 187 + (slot % 4) * 151, y = 200 + (slot / 4) * 114;
      HomeView.AppEntry a = list.get(n);
      if (a.art != null)
        c.drawBitmap(a.art, null, new RectF(x, y, x + 132, y + 74), paint);
      if (n == index) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);
        paint.setColor(0xFFFFFFFF);
        c.drawRoundRect(new RectF(x - 4, y - 4, x + 136, y + 78), 13, 13, paint);
        paint.setStyle(Paint.Style.FILL);
      }
      text.setTextSize(12);
      String label = TextUtils.ellipsize(a.label, text, 132, TextUtils.TruncateAt.END).toString();
      line(c, label, x, y + 96, 12, 0xFFFFFFFF);
    }
    endPanel(c);
  }
  private void drawSaver(Canvas c) {
    paint.setColor(0xCC000000);
    c.drawRect(0, 0, 960, 540, paint);
    long minute = System.currentTimeMillis() / 60000;
    float x = 112 + (minute % 5) * 78, y = 170 + (minute % 3) * 85;
    String clock =
        new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new Date());
    line(c, clock, x, y, 92, 0xFFFFFFFF);
    line(c, DateFormat.getDateInstance(DateFormat.FULL).format(new Date()), x + 4, y + 43, 18,
        0xFFD7D7DF);
  }
  private void banner(Canvas c, String value) {
    paint.setColor(0xE628282F);
    c.drawRoundRect(new RectF(180, 24, 780, 70), 20, 20, paint);
    text.setTextSize(15);
    text.setColor(0xFFFFFFFF);
    text.setAlpha(255);
    c.drawText(value, 480 - text.measureText(value) / 2, 53, text);
  }
  /**
   * The upstream filled Material symbols; keep their exact silhouette rather than approximating.
   */
  private void icon(Canvas c, int kind, float x, float y, float size, int color) {
    String[] paths = {AerialGlyphs.OPEN_WITH, AerialGlyphs.FOLDER, AerialGlyphs.VISIBILITY_OFF,
        AerialGlyphs.INFO, AerialGlyphs.DELETE, AerialGlyphs.INPUT, NetworkStatus.glyph(networkKind),
        AerialGlyphs.SETTINGS, AerialGlyphs.VOLUME_UP, AerialGlyphs.LIGHT_MODE, AerialGlyphs.IMAGE,
        AerialGlyphs.PLAY_CIRCLE, AerialGlyphs.CHECK};
    paint.setColor(color);
    paint.setAlpha(255);
    AerialGlyphs.draw(c, paths[Math.max(0, Math.min(kind, paths.length - 1))], x, y, size, paint);
  }
}
