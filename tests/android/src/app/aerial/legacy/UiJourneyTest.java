package app.aerial.legacy;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.test.InstrumentationTestCase;
import android.view.KeyEvent;
import java.lang.reflect.Field;
import java.util.Map;
/** Native API18 instrumentation; uses actual Activity key dispatch, not mocked handlers. */
public final class UiJourneyTest extends InstrumentationTestCase {
  private static MainActivity runningActivity;
  MainActivity activity;
  HomeView home;
  private void onUi(Runnable r) {
    getInstrumentation().runOnMainSync(r);
  }
  private void launch() throws Exception {
    Intent i = new Intent(getInstrumentation().getTargetContext(), MainActivity.class)
                   .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    if (runningActivity == null || runningActivity.isFinishing()) {
      runningActivity = (MainActivity) getInstrumentation().startActivitySync(i);
    }
    activity = runningActivity;
    Field f = MainActivity.class.getDeclaredField("home");
    f.setAccessible(true);
    home = (HomeView) f.get(activity);
    for (int n = 0; n < 150 && home.selectedItem() == null; n++) SystemClock.sleep(200);
    assertNotNull("catalog loaded", home.selectedItem());
    Field prepared = HomeView.class.getDeclaredField("prepared");
    prepared.setAccessible(true);
    for (int n = 0; n < 100 && !prepared.getBoolean(home); n++) SystemClock.sleep(100);
    assertTrue("wallpaper and glass ready for presentation", prepared.getBoolean(home));
    getInstrumentation().waitForIdleSync();
  }
  private void press(final int key) {
    onUi(new Runnable() {
      @Override
      public void run() {
        long t = SystemClock.uptimeMillis();
        activity.dispatchKeyEvent(new KeyEvent(t, t, 0, key, 0));
        activity.dispatchKeyEvent(new KeyEvent(t, t + 1, 1, key, 0));
      }
    });
    SystemClock.sleep(290);
    getInstrumentation().waitForIdleSync();
  }
  private void repeat(int key, int times) {
    for (int n = 0; n < times; n++) press(key);
  }
  private void reset() {
    onUi(new Runnable() {
      @Override
      public void run() {
        home.resetHome();
      }
    });
    SystemClock.sleep(400);
  }
  private void menu() {
    press(82);
    assertEquals(DesktopUi.APP, home.ui.mode);
  }
  public void testOrganizationControlsAndLifecycle() throws Exception {
    launch();
    final Map<String, ?> before = home.ui.prefs.getAll();
    try {
      onUi(new Runnable() {
        @Override
        public void run() {
          home.ui.prefs.edit().putInt("idle", 0).apply();
        }
      });
      reset();
      String original = home.ui.layout.encode();
      String selected = home.selectedItem().packageName;
      menu();
      press(23);
      assertTrue("move mode", home.ui.moving);
      press(22);
      press(4);
      assertEquals("move cancel", original, home.ui.layout.encode());
      menu();
      press(23);
      press(22);
      press(23);
      assertFalse(home.ui.moving);
      assertFalse("move persisted", original.equals(home.ui.prefs.getString("layout", "")));
      menu();
      press(20);
      press(23);
      assertEquals(DesktopUi.TO_FOLDER, home.ui.mode);
      press(23);
      assertEquals(DesktopUi.FOLDER, home.ui.mode);
      String folder = home.ui.openFolder;
      assertTrue(home.ui.layout.find(folder).members.contains(selected));
      press(82);
      assertEquals("folder menu", DesktopUi.APP, home.ui.mode);
      press(23);
      assertNotNull("take out", home.ui.layout.find(selected));
      reset();
      String hidden = home.selectedItem().packageName;
      menu();
      repeat(20, 2);
      press(23);
      assertTrue("hidden", home.ui.layout.hidden.contains(hidden));
      reset();
      press(19);
      assertTrue("status reachable", home.ui.chromeFocused);
      press(23);
      assertEquals(DesktopUi.CONTROL, home.ui.mode);
      press(20);
      press(22);
      press(21);
      repeat(20, 2);
      press(23);
      assertEquals(DesktopUi.PERSONAL, home.ui.mode);
      repeat(20, 5);
      press(23);
      assertEquals(DesktopUi.HIDDEN, home.ui.mode);
      press(23);
      assertFalse("restore hidden", home.ui.layout.hidden.contains(hidden));
      press(4);
      assertEquals(DesktopUi.PERSONAL, home.ui.mode);
      repeat(19, 4);
      boolean clock = home.ui.largeClock();
      press(22);
      assertEquals(!clock, home.ui.largeClock());
      press(19);
      press(23);
      assertEquals(DesktopUi.WALLPAPERS, home.ui.mode);
      int oldPreset = home.ui.preset();
      press(21);
      int chosen = home.ui.index;
      press(23);
      assertEquals(chosen, home.ui.preset());
      assertTrue("wallpaper navigation", chosen == oldPreset - 1 || chosen == oldPreset);
      reset();
      press(4);
      assertEquals(DesktopUi.SAVER, home.ui.mode);
      int focus = home.focusedIndex();
      press(22);
      assertEquals(DesktopUi.NONE, home.ui.mode);
      assertEquals("wake does not launch or move", focus, home.focusedIndex());
      SystemClock.sleep(350);
      final long down = SystemClock.uptimeMillis();
      onUi(new Runnable() {
        @Override
        public void run() {
          activity.dispatchKeyEvent(new KeyEvent(down, down, 0, 23, 0));
        }
      });
      SystemClock.sleep(850);
      assertEquals("long press opens", DesktopUi.APP, home.ui.mode);
      onUi(new Runnable() {
        @Override
        public void run() {
          activity.dispatchKeyEvent(new KeyEvent(down, SystemClock.uptimeMillis(), 1, 23, 0));
        }
      });
      SystemClock.sleep(300);
      assertEquals("release never activates menu row", DesktopUi.APP, home.ui.mode);
      press(4);
      final String saved = home.ui.layout.encode();
      final HomeView[] restoredView = new HomeView[1];
      onUi(new Runnable() {
        @Override
        public void run() {
          restoredView[0] = new HomeView(activity);
        }
      });
      assertEquals(
          "layout restores in a new home instance", saved, restoredView[0].ui.layout.encode());
      assertEquals(
          "wallpaper restores in a new home instance", chosen, restoredView[0].ui.preset());
      onUi(new Runnable() {
        @Override
        public void run() {
          restoredView[0].release();
        }
      });
      onUi(new Runnable() {
        @Override
        public void run() {
          home.onHidden();
        }
      });
      assertFalse("hidden has no idle timer", home.ui.active);
      onUi(new Runnable() {
        @Override
        public void run() {
          home.onShown();
        }
      });
      assertTrue(home.ui.active);
    } finally {
      onUi(new Runnable() {
        @Override
        public void run() {
          SharedPreferences.Editor e = home.ui.prefs.edit().clear();
          for (Map.Entry<String, ?> v : before.entrySet()) {
            Object o = v.getValue();
            if (o instanceof String)
              e.putString(v.getKey(), (String) o);
            else if (o instanceof Boolean)
              e.putBoolean(v.getKey(), (Boolean) o);
            else if (o instanceof Integer)
              e.putInt(v.getKey(), (Integer) o);
          }
          e.commit();
          home.ui.layout.decode(home.ui.prefs.getString("layout", ""));
          home.ui.refresh(null);
          home.resetHome();
          home.changeWallpaper();
        }
      });
    }
  }

  public void testMotionSettlesWithoutIdleAnimation() throws Exception {
    launch();
    reset();
    press(22);
    final Field weights = HomeView.class.getDeclaredField("focusWeights");
    weights.setAccessible(true);
    final Field focus = HomeView.class.getDeclaredField("focusAnimator");
    focus.setAccessible(true);
    SystemClock.sleep(450);
    onUi(new Runnable() {
      @Override
      public void run() {
        try {
          android.animation.ValueAnimator a = (android.animation.ValueAnimator) focus.get(home);
          assertNotNull(a);
          assertFalse("focus spring stops when settled", a.isRunning());
            Map<?,?> values=(Map<?,?>)weights.get(home);
            Float selected = (Float) values.get(home.selectedItem().packageName);
            assertEquals("selected focus reaches full scale", 1f, selected, 0.001f);
        } catch (IllegalAccessException e) {
          throw new RuntimeException(e);
        }
      }
    });
    menu();
    final Field panel = DesktopUi.class.getDeclaredField("panelAnimator");
    panel.setAccessible(true);
    SystemClock.sleep(350);
    onUi(new Runnable() {
      @Override
      public void run() {
        try {
          assertFalse("panel animation stops",
              ((android.animation.ValueAnimator) panel.get(home.ui)).isRunning());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
    press(4);
    assertEquals(DesktopUi.NONE, home.ui.mode);
    reset();
  }

  public void testGoogleSansTypographyAndChineseFallback() throws Exception {
    launch();
    reset();
    final android.content.res.Resources resources = activity.getResources();
    final android.content.res.Configuration before =
        new android.content.res.Configuration(resources.getConfiguration());
    final java.util.Locale locale = java.util.Locale.getDefault();
    try {
      onUi(new Runnable() {
        @Override
        public void run() {
          assertSame(AppFonts.regular(activity), AppFonts.regular(activity));
          assertSame(AppFonts.medium(activity), AppFonts.medium(activity));
          try {
            Field clock = HomeView.class.getDeclaredField("clockPaint");
            clock.setAccessible(true);
            assertSame("clock uses Google Sans", AppFonts.regular(activity),
                ((android.graphics.Paint) clock.get(home)).getTypeface());
            Field label = HomeView.class.getDeclaredField("labelPaint");
            label.setAccessible(true);
            assertSame("app labels use Google Sans Medium", AppFonts.medium(activity),
                ((android.graphics.Paint) label.get(home)).getTypeface());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
          android.widget.TextView nativeText = new android.widget.TextView(activity);
          nativeText.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
          AppFonts.apply(nativeText);
          assertSame("native dialog emphasis", AppFonts.medium(activity), nativeText.getTypeface());
          android.content.res.Configuration zh = new android.content.res.Configuration(before);
          zh.locale = java.util.Locale.SIMPLIFIED_CHINESE;
          java.util.Locale.setDefault(zh.locale);
          resources.updateConfiguration(zh, resources.getDisplayMetrics());
          home.onShown();
          home.ui.control();
        }
      });
      SystemClock.sleep(900);
      getInstrumentation().waitForIdleSync();
      renderPreview("google-sans-chinese.png");
      onUi(new Runnable() {
        @Override
        public void run() {
          home.resetHome();
        }
      });
      SystemClock.sleep(450);
      renderPreview("home-preview.png");
      onUi(new Runnable() {
        @Override
        public void run() {
          home.ui.appMenu();
        }
      });
      SystemClock.sleep(350);
      renderPreview("menu-preview.png");
    } finally {
      onUi(new Runnable() {
        @Override
        public void run() {
          java.util.Locale.setDefault(locale);
          resources.updateConfiguration(before, resources.getDisplayMetrics());
          home.resetHome();
          home.onShown();
        }
      });
    }
  }

  private void renderPreview(String name) throws Exception {
    final android.graphics.Bitmap[] rendered = new android.graphics.Bitmap[1];
    onUi(new Runnable() {
      @Override
      public void run() {
        rendered[0] = android.graphics.Bitmap.createBitmap(
            home.getWidth(), home.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        home.draw(new android.graphics.Canvas(rendered[0]));
      }
    });
    android.graphics.Bitmap bitmap = rendered[0];
    assertNotNull("API18 view rendered", bitmap);
    java.io.FileOutputStream out = activity.openFileOutput(name, 0);
    try {
      assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out));
    } finally {
      out.close();
      bitmap.recycle();
    }
  }

  public void testLegacyInputRouteAndBridgeContract() throws Exception {
    launch();
    android.content.Intent route = LegacyInputs.letvIntent();
    assertEquals("com.stv.signalsourcemanager", route.getComponent().getPackageName());
    assertEquals("com.stv.signalsourcemanager.MainActivity", route.getComponent().getClassName());
    android.content.Intent service = new android.content.Intent().setClassName(
        "com.stv.launcher", "com.stv.launcher.service.ShowTitleService");
    assertNotNull(
        "compatibility endpoint resolves", activity.getPackageManager().resolveService(service, 0));
    assertNotNull("vendor package launch lookup resolves",
        activity.getPackageManager().getLaunchIntentForPackage("com.stv.launcher"));
    final java.util.concurrent.CountDownLatch connected =
        new java.util.concurrent.CountDownLatch(1);
    final android.os.IBinder[] binder = new android.os.IBinder[1];
    final android.content.ServiceConnection connection = new android.content.ServiceConnection() {
      @Override
      public void onServiceConnected(
          android.content.ComponentName name, android.os.IBinder service) {
        binder[0] = service;
        connected.countDown();
      }
      @Override
      public void onServiceDisconnected(android.content.ComponentName name) {}
    };
    boolean bound =
        activity.bindService(service, connection, android.content.Context.BIND_AUTO_CREATE);
    assertTrue("bind succeeds", bound);
    try {
      assertTrue("Messenger connected", connected.await(10, java.util.concurrent.TimeUnit.SECONDS));
      android.os.Messenger messenger = new android.os.Messenger(binder[0]);
      messenger.send(android.os.Message.obtain(null, 1, 1, 0));
      messenger.send(android.os.Message.obtain(null, 2, 0, 0));
    } finally {
      activity.unbindService(connection);
    }
  }
}
