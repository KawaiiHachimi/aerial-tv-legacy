package app.aerial.legacy;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
/** Upstream SystemTile colors with API18 firmware package mappings found in the analyzed image. */
final class SystemTiles {
  static boolean draw(Canvas c, String pkg, int w, int h) {
    int top, bottom;
    String glyph;
    if (pkg.equals("com.android.settings") || pkg.equals("com.stv.t2.globalsetting")
        || pkg.equals("mstar.tvsetting.ui")) {
      top = 0xFF8E8E93;
      bottom = 0xFF48484A;
      glyph = AerialGlyphs.SETTINGS;
    } else if (pkg.equals("com.android.browser")) {
      top = 0xFF64D2FF;
      bottom = 0xFF0A7BEA;
      glyph = AerialGlyphs.LANGUAGE;
    } else if (pkg.equals("com.android.gallery3d")) {
      top = 0xFFFFB340;
      bottom = 0xFFFF5E3A;
      glyph = AerialGlyphs.IMAGE;
    } else if (pkg.equals("com.android.calendar")) {
      top = 0xFFFF6B5E;
      bottom = 0xFFD02A1E;
      glyph = AerialGlyphs.CALENDAR_MONTH;
    } else if (pkg.equals("com.stv.videoplayer")) {
      top = 0xFF3A3A3C;
      bottom = 0xFF111113;
      glyph = AerialGlyphs.PLAY_CIRCLE;
    } else if (pkg.equals("com.stv.filemanager")) {
      top = 0xFF40B0FF;
      bottom = 0xFF0A5FD8;
      glyph = AerialGlyphs.FOLDER;
    } else if (pkg.equals("com.stv.smartControl")) {
      top = 0xFF8E8CFF;
      bottom = 0xFF4744D0;
      glyph = AerialGlyphs.CAST;
    } else if (pkg.equals("com.stv.signalsourcemanager")) {
      top = 0xFF477EEB;
      bottom = 0xFF24479F;
      glyph = AerialGlyphs.INPUT;
    } else
      return false;
    Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    p.setShader(new LinearGradient(0, 0, w, h, top, bottom, Shader.TileMode.CLAMP));
    c.drawRect(0, 0, w, h, p);
    p.setShader(null);
    p.setColor(0xFFFFFFFF);
    float size = h * .58f;
    AerialGlyphs.draw(c, glyph, (w - size) / 2, (h - size) / 2, size, p);
    return true;
  }
}
