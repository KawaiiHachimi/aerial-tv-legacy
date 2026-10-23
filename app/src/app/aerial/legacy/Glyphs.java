package app.aerial.legacy;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Aerial's chrome glyphs, ported to plain Android drawing for API 18.
 *
 * <p>The upstream app draws Material Symbols Rounded glyphs (see
 * {@code aerial-tv/.../ui/theme/Glyphs.kt} and {@code GlyphPaths.kt}): the Wi-Fi indicator is
 * Material's {@code wifi} symbol, and Control Center is SF Symbols' {@code switch.2} geometry
 * hand-built because Material has no close match. The Wi-Fi path is parsed once from the same
 * path data the upstream app uses; the switches are rebuilt from the same rounded-rect/circle
 * measurements, using an even-odd path for the track with the cut-out knob.
 */
final class Glyphs {

    private Glyphs() {
    }

    /** Material Symbols Rounded "wifi" (the glyph Aerial uses for the signal indicator). */
    private static final String WIFI_PATH =
            "M480-127q-36 0-63-27t-27-63q0-36 27-63t63-27q36 0 63 27t27 63q0 36-27 63t-63 27Z"
            + "m0-433q78 0 146 27.5T757-451q14 12 15 30.5T759-388q-14 14-32 13t-33-13q-53-44-106.5-63T480-470q-54 0-107.5 19T266-388q-15 12-33 13t-32-13q-14-14-13-32.5t15-30.5q63-54 131-81.5T480-560Z"
            + "m0-240q128 0 241.5 48.5T926-622q14 13 15.5 31.5T929-558q-14 14-33.5 13.5T861-558q-83-70-178-111t-203-41q-108 0-203 41T99-558q-15 13-34 13.5T32-558q-14-14-13-32.5T34-622q91-81 204.5-129.5T480-800Z";

    private static Path wifi;

    private static synchronized Path wifiPath() {
        if (wifi == null) {
            wifi = parsePath(WIFI_PATH);
        }
        return wifi;
    }

    /**
     * Draws the Wi-Fi glyph inside the square from ({@code x}, {@code y}) to
     * ({@code x + size}, {@code y + size}). The glyph's own coordinate box runs y=-960..0.
     */
    static void drawWifi(Canvas canvas, float x, float y, float size, Paint paint) {
        Path path = wifiPath();
        int save = canvas.save();
        canvas.translate(x, y + size);
        float k = size / 960f;
        canvas.scale(k, k);
        canvas.drawPath(path, paint);
        canvas.restoreToCount(save);
    }

    /**
     * Draws the Control Center glyph (SF Symbols' {@code switch.2}): an outlined track with a
     * solid knob on the left over a solid track whose knob is cut out on the right.
     */
    static void drawSwitches(Canvas canvas, float x, float y, float size, Paint paint) {
        float line = Math.max(1f, Math.round(size / 16f));
        float h = Math.max(2f, Math.round(size * 0.36f));
        float gap = Math.max(1f, Math.round(size * 0.12f));
        float left = Math.round(size * 0.04f);
        float right = size - left;
        float top = Math.round((size - 2f * h - gap) / 2f);
        float bottom = top + h + gap;
        float r = h / 2f;

        Paint.Style style = paint.getStyle();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(line);
        canvas.drawRoundRect(new RectF(x + left, y + top, x + right, y + top + h), r, r, paint);
        paint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(x + left + r, y + top + r, r, paint);

        Path lower = new Path();
        lower.setFillType(Path.FillType.EVEN_ODD);
        lower.addRoundRect(new RectF(x + left, y + bottom, x + right, y + bottom + h),
                r, r, Path.Direction.CW);
        lower.addCircle(x + right - r, y + bottom + r, r - line, Path.Direction.CW);
        canvas.drawPath(lower, paint);
        paint.setStyle(style);
    }

    // ---- a minimal SVG path parser, enough for Material Symbols' filled glyphs ----

    static Path parsePath(String data) {
        Path path = new Path();
        int i = 0;
        int n = data.length();
        char command = 0;
        float cx = 0f;
        float cy = 0f;
        float startX = 0f;
        float startY = 0f;
        float lastCtrlX = 0f;
        float lastCtrlY = 0f;
        float lastQCtrlX = 0f;
        float lastQCtrlY = 0f;

        while (i < n) {
            char ch = data.charAt(i);
            if (Character.isLetter(ch)) {
                command = ch;
                i++;
                if (ch == 'Z' || ch == 'z') {
                    path.close();
                    cx = startX;
                    cy = startY;
                }
                continue;
            }
            if (command == 0) {
                i++;
                continue;
            }
            switch (command) {
                case 'M':
                    cx = readNumber(data, i)[0];
                    i = (int) readNumber(data, i)[1];
                    cy = readNumber(data, i)[0];
                    i = (int) readNumber(data, i)[1];
                    path.moveTo(cx, cy);
                    startX = cx;
                    startY = cy;
                    command = 'L';
                    break;
                case 'm':
                    cx += readNumber(data, i)[0];
                    i = (int) readNumber(data, i)[1];
                    cy += readNumber(data, i)[0];
                    i = (int) readNumber(data, i)[1];
                    path.moveTo(cx, cy);
                    startX = cx;
                    startY = cy;
                    command = 'l';
                    break;
                case 'L': {
                    float[] v = pair(data, i);
                    cx = v[0];
                    cy = v[1];
                    i = (int) v[2];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'l': {
                    float[] v = pair(data, i);
                    cx += v[0];
                    cy += v[1];
                    i = (int) v[2];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'H': {
                    float[] v = readNumber(data, i);
                    cx = v[0];
                    i = (int) v[1];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'h': {
                    float[] v = readNumber(data, i);
                    cx += v[0];
                    i = (int) v[1];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'V': {
                    float[] v = readNumber(data, i);
                    cy = v[0];
                    i = (int) v[1];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'v': {
                    float[] v = readNumber(data, i);
                    cy += v[0];
                    i = (int) v[1];
                    path.lineTo(cx, cy);
                    break;
                }
                case 'Q': {
                    float[] v = quad(data, i);
                    path.quadTo(v[0], v[1], v[2], v[3]);
                    lastQCtrlX = v[0];
                    lastQCtrlY = v[1];
                    cx = v[2];
                    cy = v[3];
                    i = (int) v[4];
                    break;
                }
                case 'q': {
                    float[] v = quad(data, i);
                    float qx = cx + v[0];
                    float qy = cy + v[1];
                    float nx = cx + v[2];
                    float ny = cy + v[3];
                    path.quadTo(qx, qy, nx, ny);
                    lastQCtrlX = qx;
                    lastQCtrlY = qy;
                    cx = nx;
                    cy = ny;
                    i = (int) v[4];
                    break;
                }
                case 'T': {
                    float[] v = pair(data, i);
                    float qx = 2f * cx - lastQCtrlX;
                    float qy = 2f * cy - lastQCtrlY;
                    path.quadTo(qx, qy, v[0], v[1]);
                    lastQCtrlX = qx;
                    lastQCtrlY = qy;
                    cx = v[0];
                    cy = v[1];
                    i = (int) v[2];
                    break;
                }
                case 't': {
                    float[] v = pair(data, i);
                    float qx = 2f * cx - lastQCtrlX;
                    float qy = 2f * cy - lastQCtrlY;
                    float nx = cx + v[0];
                    float ny = cy + v[1];
                    path.quadTo(qx, qy, nx, ny);
                    lastQCtrlX = qx;
                    lastQCtrlY = qy;
                    cx = nx;
                    cy = ny;
                    i = (int) v[2];
                    break;
                }
                case 'C': {
                    float[] v = six(data, i);
                    path.cubicTo(v[0], v[1], v[2], v[3], v[4], v[5]);
                    lastCtrlX = v[2];
                    lastCtrlY = v[3];
                    cx = v[4];
                    cy = v[5];
                    i = (int) v[6];
                    break;
                }
                case 'c': {
                    float[] v = six(data, i);
                    path.cubicTo(cx + v[0], cy + v[1], cx + v[2], cy + v[3], cx + v[4], cy + v[5]);
                    lastCtrlX = cx + v[2];
                    lastCtrlY = cy + v[3];
                    cx += v[4];
                    cy += v[5];
                    i = (int) v[6];
                    break;
                }
                case 'S': {
                    float[] v = six(data, i);
                    float c1x = 2f * cx - lastCtrlX;
                    float c1y = 2f * cy - lastCtrlY;
                    path.cubicTo(c1x, c1y, v[0], v[1], v[2], v[3]);
                    lastCtrlX = v[0];
                    lastCtrlY = v[1];
                    cx = v[2];
                    cy = v[3];
                    i = (int) v[4];
                    break;
                }
                case 's': {
                    float[] v = six(data, i);
                    float c1x = 2f * cx - lastCtrlX;
                    float c1y = 2f * cy - lastCtrlY;
                    path.cubicTo(c1x, c1y, cx + v[0], cy + v[1], cx + v[2], cy + v[3]);
                    lastCtrlX = cx + v[0];
                    lastCtrlY = cy + v[1];
                    cx += v[2];
                    cy += v[3];
                    i = (int) v[4];
                    break;
                }
                default:
                    i++;
                    break;
            }
        }
        return path;
    }

    private static float[] pair(String data, int i) {
        float[] a = readNumber(data, i);
        float[] b = readNumber(data, (int) a[1]);
        return new float[]{a[0], b[0], b[1]};
    }

    private static float[] quad(String data, int i) {
        float[] a = readNumber(data, i);
        float[] b = readNumber(data, (int) a[1]);
        float[] c = readNumber(data, (int) b[1]);
        float[] d = readNumber(data, (int) c[1]);
        return new float[]{a[0], b[0], c[0], d[0], d[1]};
    }

    private static float[] six(String data, int i) {
        float[] a = readNumber(data, i);
        float[] b = readNumber(data, (int) a[1]);
        float[] c = readNumber(data, (int) b[1]);
        float[] d = readNumber(data, (int) c[1]);
        float[] e = readNumber(data, (int) d[1]);
        float[] f = readNumber(data, (int) e[1]);
        return new float[]{a[0], b[0], c[0], d[0], e[0], f[0], f[1]};
    }

    /** Reads one number starting at {@code i}; returns {value, indexAfter}. */
    private static float[] readNumber(String data, int i) {
        int n = data.length();
        while (i < n) {
            char c = data.charAt(i);
            if (c == ' ' || c == ',' || c == '\n' || c == '\t' || c == '\r') {
                i++;
            } else {
                break;
            }
        }
        int start = i;
        if (i < n && (data.charAt(i) == '-' || data.charAt(i) == '+')) {
            i++;
        }
        while (i < n && Character.isDigit(data.charAt(i))) {
            i++;
        }
        if (i < n && data.charAt(i) == '.') {
            i++;
            while (i < n && Character.isDigit(data.charAt(i))) {
                i++;
            }
        }
        if (i < n && (data.charAt(i) == 'e' || data.charAt(i) == 'E')) {
            i++;
            if (i < n && (data.charAt(i) == '-' || data.charAt(i) == '+')) {
                i++;
            }
            while (i < n && Character.isDigit(data.charAt(i))) {
                i++;
            }
        }
        float value = 0f;
        if (i > start) {
            try {
                value = Float.parseFloat(data.substring(start, i));
            } catch (NumberFormatException ignored) {
                value = 0f;
            }
        }
        return new float[]{value, i == start ? start + 1 : i};
    }
}
