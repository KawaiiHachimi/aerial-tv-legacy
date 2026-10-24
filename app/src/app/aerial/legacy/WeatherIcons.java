package app.aerial.legacy;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Aerial's Material weather symbols; no background plate, animation or shadow. */
final class WeatherIcons {
  static void draw(Canvas canvas, WeatherData.Reading reading, float x, float y, float size, float alpha, Paint paint) {
    int white = (Math.round(alpha * 255) << 24) | 0xFFFFFF;
    paint.setColor(white);
    String path = AerialGlyphs.CLOUD;
    switch (reading.kind()) {
      case CLEAR:
        path = reading.day ? AerialGlyphs.LIGHT_MODE : AerialGlyphs.BEDTIME;
        if (reading.day) paint.setColor((Math.round(alpha * 255) << 24) | 0xFFD60A);
        break;
      case PARTLY_CLOUDY:
        paint.setColor(reading.day ? (Math.round(alpha * 255) << 24) | 0xFFD60A : white);
        AerialGlyphs.draw(canvas, reading.day ? AerialGlyphs.LIGHT_MODE : AerialGlyphs.BEDTIME,
            x - size * .03f, y - size * .04f, size * .78f, paint);
        paint.setColor(white);
        AerialGlyphs.draw(canvas, AerialGlyphs.CLOUD, x + size * .17f, y + size * .19f, size * .86f, paint);
        return;
      case FOG: path = AerialGlyphs.FOGGY; break;
      case RAIN: path = AerialGlyphs.RAINY; break;
      case SNOW: path = AerialGlyphs.WEATHER_SNOWY; break;
      case THUNDER: path = AerialGlyphs.THUNDERSTORM; break;
      default: break;
    }
    AerialGlyphs.draw(canvas, path, x, y, size, paint);
  }
}
