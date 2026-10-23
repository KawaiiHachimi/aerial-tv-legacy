package app.aerial.legacy;

import android.content.Context;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

/**
 * One app-wide font family. Official Android static TTFs work with API18's old Skia loader.
 * Missing CJK glyphs use the system's normal DroidSansFallback chain; no system fonts are replaced.
 */
final class AppFonts {
  private static Typeface regular, medium;
  static synchronized Typeface regular(Context context) {
    if (regular == null)
      regular = Typeface.createFromAsset(context.getAssets(), "fonts/GoogleSans-Regular.ttf");
    return regular;
  }
  static synchronized Typeface medium(Context context) {
    if (medium == null)
      medium = Typeface.createFromAsset(context.getAssets(), "fonts/GoogleSans-Medium.ttf");
    return medium;
  }
  static void apply(View view) {
    if (view instanceof TextView) {
      TextView text = (TextView) view;
      boolean emphasized =
          view instanceof Button || (text.getTypeface() != null && text.getTypeface().isBold());
      text.setTypeface(emphasized ? medium(view.getContext()) : regular(view.getContext()));
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) apply(group.getChildAt(i));
    }
  }
  static Toast toast(Context context, int message, int duration) {
    Toast toast = Toast.makeText(context, message, duration);
    apply(toast.getView());
    return toast;
  }
}
