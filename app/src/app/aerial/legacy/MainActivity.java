package app.aerial.legacy;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * The single Activity of the Aerial desktop for Android 4.3.
 *
 * <p>The high-priority HOME; the optional bridge handles lower-priority package-scoped vendor
 * HOME requests. Keys are dispatched here instead of relying on focusable
 * child views, which is far more reliable with MStar IR remotes.
 */
public final class MainActivity extends Activity {
  private HomeView home;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    hideSystemUi();
    LegacyDesktop.initialize(this, true);
    home = new HomeView(this);
    setContentView(home);
  }

  @Override
  protected void onResume() {
    super.onResume();
    LegacyDesktop.initialize(this, true);
    hideSystemUi();
    if (home != null) {
      home.onShown();
    }
  }

  @Override
  protected void onPause() {
    LegacyDesktop.onHidden();
    if (home != null)
      home.onHidden();
    super.onPause();
  }

  @Override
  protected void onNewIntent(android.content.Intent intent) {
    super.onNewIntent(intent);
    setIntent(intent);
    if (home != null)
      home.resetHome();
  }

  @Override
  public void onWindowFocusChanged(boolean hasFocus) {
    super.onWindowFocusChanged(hasFocus);
    if (hasFocus) {
      hideSystemUi();
    }
  }

  @SuppressWarnings("deprecation")
  private void hideSystemUi() {
    View decor = getWindow().getDecorView();
    decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
  }

  @Override
  public boolean dispatchKeyEvent(KeyEvent event) {
    if (home != null && home.handleKeyEvent(event)) {
      return true;
    }
    return super.dispatchKeyEvent(event);
  }

  @Override
  public void onBackPressed() {
    // BACK never leaves the home screen: otherwise a TV remote drops the user on a black feed.
  }

  @Override
  protected void onDestroy() {
    if (home != null) {
      home.release();
      home = null;
    }
    super.onDestroy();
  }
}
