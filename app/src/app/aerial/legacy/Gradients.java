package app.aerial.legacy;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import java.util.Random;

/**
 * Aerial's own wallpapers, redrawn on a plain {@link Canvas} so the same look runs on Android 4.3.
 *
 * <p>Direct Java port of {@code aerial-tv/app/src/main/java/app/orchard/tv/wallpaper/Gradients.kt}
 * (the upstream Kotlin/Compose project). Nothing is shipped as a picture: every preset is drawn
 * once at the panel's real size, then kept as a {@link Bitmap}, so a weak MStar GPU only ever
 * blits a single full-screen layer. All shapes are laid out on fractions of the picture.
 */
final class Gradients {

    private Gradients() {
    }

    /** Number of presets; keep in sync with {@link #render}. */
    static final int COUNT = 15;

    /**
     * The default wallpaper: a soft blue-lavender dusk with a warm peach glow and dark purple
     * hills, matched against {@code aerial-tv/site/img/home.jpg} (Aerial's own home screen).
     */
    static final int DEFAULT_PRESET = 14;

    /** A stable wallpaper per calendar day, like Aerial's "follow the day" rotation. */
    static int forDayOfYear(int dayOfYear) {
        return ((dayOfYear % COUNT) + COUNT) % COUNT;
    }

    /** Renders preset {@code index} at {@code width} x {@code height}, dithered so long gradients do not band. */
    static Bitmap render(int index, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float w = width;
        float h = height;
        switch (index) {
            case 0: // hills_dawn
                hills(c, w, h, sky(0xFF7B8FD6, 0xFFE9B7C9, 0xFFFFD9B8), 0.7f, 0.6f, 0xFFFFD6AA, 0.7f,
                        layers(0xFFB7A3D8, 0xFFC9B2D9, 0xFF8F7FC4, 0xFFA38FC8,
                                0xFF6A5AA8, 0xFF7B66AE, 0xFF433878, 0xFF3A2F66));
                break;
            case 1: // hills_dusk
                hills(c, w, h, sky(0xFF16244F, 0xFFC25B6B, 0xFFF59B5B), 0.35f, 0.62f, 0xFFFFA05A, 0.65f,
                        layers(0xFF7D3F63, 0xFF8A4762, 0xFF552D5A, 0xFF5B2F55,
                                0xFF33204A, 0xFF2C1C40, 0xFF170F2A, 0xFF120B20));
                break;
            case 2: // hills_mist
                hills(c, w, h, sky(0xFFA9C9D8, 0xFFDBE8E3, 0xFFF3EFE2), 0.3f, 0.55f, 0xFFFFFAEB, 0.7f,
                        layers(0xFFA8C2BB, 0xFFB9D0C6, 0xFF7EA79F, 0xFF8FB3A7,
                                0xFF4F8178, 0xFF5D8B7D, 0xFF2A5249, 0xFF24463D));
                break;
            case 3: // hills_night  -- Aerial's signature dark wallpaper
                hills(c, w, h, sky(0xFF04081C, 0xFF14285A, 0xFF2F4D86), 0.62f, 0.58f, 0xFF78AAFF, 0.35f,
                        layers(0xFF1F3566, 0xFF253C70, 0xFF162851, 0xFF182B55,
                                0xFF0D1B3A, 0xFF0B1733, 0xFF050C1F, 0xFF040917));
                break;
            case 4: // dunes_gold
                dunes(c, w, h, sky(0xFF4F7FBF, 0xFFA7C3DD, 0xFFF5D3A0), 0.8f, 0.45f, 0xFFFFE6B4, 0.5f,
                        layers(0xFFF3C58A, 0xFFE7A96A, 0xFFEEB06C, 0xFFD98E4F,
                                0xFFE39A52, 0xFFC9733A, 0xFFD4843F, 0xFFB25D2B),
                        new int[]{0x73A05028, 0x808C411E, 0x8C783419, 0x99642814});
                break;
            case 5: // dunes_moon
                dunes(c, w, h, sky(0xFF0B0F2E, 0xFF2B2F6B, 0xFF6C5A9E), 0.2f, 0.4f, 0xFFB4AAFF, 0.3f,
                        layers(0xFF6D63A8, 0xFF5A4F93, 0xFF5A4F93, 0xFF463C7C,
                                0xFF463C7C, 0xFF352C66, 0xFF352C66, 0xFF251E4F),
                        new int[]{0x730A0828, 0x800A0828, 0x8C0A0828, 0x990A0828});
                break;
            case 6: // sea_sunset
                sea(c, w, h, sky(0xFF2A2F6E, 0xFFC75A7A, 0xFFFFB070), 0xFFFFAA6E, 0.6f,
                        sky(0xFF6A3D6D, 0xFF2A1F48, 0xFF120C26), 0xFFFFF0D2, 0xFFFFC896);
                break;
            case 7: // sea_clear
                sea(c, w, h, sky(0xFF2F6FD6, 0xFF78AEE8, 0xFFD9ECF7), 0xFFFFFFFF, 0.55f,
                        sky(0xFF2D7FC2, 0xFF155A9A, 0xFF0A3566), 0xFFFFFFFA, 0xFFFFFFFF);
                break;
            case 8: // waves_blue
                waves(c, w, h, 0xFF0A1A4A, 0xFF123A8A,
                        layers(0xFF1F5FD0, 0xFF0D2F7A, 0xFF2E86E8, 0xFF1446A8,
                                0xFF3FB2F0, 0xFF1A64C4, 0xFF7FD3FF, 0xFF2B85DC));
                break;
            case 9: // waves_coral
                waves(c, w, h, 0xFF3A0F2E, 0xFF7A1F45,
                        layers(0xFFC23A6B, 0xFF7A1F4A, 0xFFEC5F6A, 0xFFB0334F,
                                0xFFFF8A5C, 0xFFD9534A, 0xFFFFC078, 0xFFF08A52));
                break;
            case 10: // waves_violet
                waves(c, w, h, 0xFF120A3A, 0xFF2A1670,
                        layers(0xFF5B3FD0, 0xFF2C1A85, 0xFF8A5CF0, 0xFF4F30B8,
                                0xFFC27BF5, 0xFF7B45D8, 0xFFF2A6E8, 0xFFB56AD8));
                break;
            case 11: // glow_pink
                glows(c, w, h, 0xFFF6D6E0,
                        new Blob(0.2f, 0.3f, 0.35f, 0xFFFFB3C7), new Blob(0.75f, 0.25f, 0.3f, 0xFFC9B6FF),
                        new Blob(0.55f, 0.85f, 0.4f, 0xFFFFD2A8), new Blob(0.9f, 0.8f, 0.25f, 0xFFA8D8FF));
                break;
            case 12: // aurora
                glows(c, w, h, 0xFF071029,
                        new Blob(0.2f, 0.35f, 0.3f, 0xFF18C79B), new Blob(0.65f, 0.2f, 0.35f, 0xFF5B3FD9),
                        new Blob(0.5f, 0.9f, 0.4f, 0xFF0E6FFF), new Blob(0.95f, 0.6f, 0.2f, 0xFFC14BD8));
                break;
            case 13: // glow_lime
                glows(c, w, h, 0xFFE3F4EA,
                        new Blob(0.25f, 0.3f, 0.3f, 0xFF9EE6B8), new Blob(0.75f, 0.3f, 0.3f, 0xFFBFE9FF),
                        new Blob(0.5f, 0.85f, 0.4f, 0xFFF2F0A0), new Blob(0.05f, 0.85f, 0.25f, 0xFF7FD6C8));
                break;
            case 14: // the Aerial home look
            default:
                aerialDusk(c, w, h);
                break;
        }
        dither(out);
        return out;
    }

    /**
     * Aerial's home wallpaper: a calm lavender-blue sky warmed toward the middle of the frame,
     * a faint cool glow up to the right, then five soft ridges falling to near-black at the
     * bottom, so the glass shelf and the app tiles are what the eye lands on.
     */
    private static void aerialDusk(Canvas c, float w, float h) {
        c.drawRect(0f, 0f, w, h * 0.70f,
                fill(vertical(0f, h * 0.70f, sky(0xFF3E4268, 0xFF72759E, 0xFFC09E9A))));
        // A darker corner top-left, so the clock reads as it does in Aerial.
        glowAt(c, w, h, 0f, 0f, w * 0.65f, 0xFF000000, 0.22f);
        // Warm bloom: Aerial's light sits right of centre and reaches down to the horizon.
        glowAt(c, w, h, w * 0.58f, h * 0.48f, w * 0.60f, 0xFFFFD6B8, 0.42f);
        glowAt(c, w, h, w * 0.45f, h * 0.62f, w * 0.50f, 0xFFFFC9A8, 0.30f);
        // A blue-violet glow high on the right, as in Aerial's dusk sky.
        glowAt(c, w, h, w * 0.86f, h * 0.10f, w * 0.55f, 0xFFA6B4F0, 0.42f);

        int[][] ridges = layers(
                0xFF7A6BA8, 0xFF887AB8, // lightest, nearest the light
                0xFF655387, 0xFF706095,
                0xFF4E4070, 0xFF58487C,
                0xFF382F55, 0xFF40365E,
                0xFF201A30, 0xFF27203B);
        float[] base = {0.530f, 0.600f, 0.670f, 0.745f, 0.825f};
        float[] amp = {0.070f, 0.065f, 0.058f, 0.052f, 0.046f};
        for (int i = 0; i < ridges.length; i++) {
            float ridgeBase = h * base[i];
            Path layer = ridge(w, h, ridgeBase, h * amp[i], 7 + i * 13, false);
            c.drawPath(layer, fill(vertical(ridgeBase - h * 0.14f, h, ridges[i])));
        }
        // Hills fall away into a dark floor; the shelf lives down here.
        Paint floor = new Paint(Paint.ANTI_ALIAS_FLAG);
        floor.setShader(new LinearGradient(0f, h * 0.62f, 0f, h,
                new int[]{0x00000000, 0x55000000, 0xB0000000},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0f, h * 0.62f, w, h, floor);
    }

    // ---- helpers ----

    private static final class Blob {
        final float x;
        final float y;
        final float r;
        final int color;

        Blob(float x, float y, float r, int color) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.color = color;
        }
    }

    private static int[] sky(int... colors) {
        return colors;
    }

    /** Pairs of top/bottom colours: {@code {top0, bottom0, top1, bottom1, ...}}. */
    private static int[][] layers(int... colors) {
        int[][] out = new int[colors.length / 2][2];
        for (int i = 0; i < out.length; i++) {
            out[i][0] = colors[i * 2];
            out[i][1] = colors[i * 2 + 1];
        }
        return out;
    }

    private static Paint fill(Shader shader) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(shader);
        p.setDither(true);
        return p;
    }

    private static LinearGradient vertical(float y0, float y1, int[] colors) {
        return new LinearGradient(0f, y0, 0f, y1, colors, null, Shader.TileMode.CLAMP);
    }

    private static void glowAt(Canvas c, float w, float h, float x, float y, float r, int color, float alpha) {
        int a = ((int) (alpha * 255f)) << 24;
        int rgb = color & 0xFFFFFF;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new RadialGradient(x, y, r, rgb | a, rgb, Shader.TileMode.CLAMP));
        c.drawRect(0f, 0f, w, h, p);
    }

    /**
     * A ridge: three sines, seeded so each layer has its own, the same at every size. Closed down
     * to the bottom of the picture, or with {@code crestOnly} just the line along the top.
     */
    private static Path ridge(float w, float h, float base, float amp, int seed, boolean crestOnly) {
        long[] state = {seed};
        float[] ph = new float[3];
        float[] f = new float[3];
        for (int i = 0; i < 3; i++) {
            ph[i] = nextRandom(state) * 6f;
        }
        f[0] = 1.3f + nextRandom(state);
        f[1] = 3f + nextRandom(state) * 2f;
        f[2] = 7f + nextRandom(state) * 3f;

        Path path = new Path();
        int steps = 240;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float y = base + amp * (float) (Math.sin(t * f[0] + ph[0]) * 0.6f
                    + Math.sin(t * f[1] + ph[1]) * 0.28f
                    + Math.sin(t * f[2] + ph[2]) * 0.12f);
            if (i == 0) {
                if (crestOnly) {
                    path.moveTo(0f, y);
                } else {
                    path.moveTo(0f, h);
                    path.lineTo(0f, y);
                }
            } else {
                path.lineTo(t * w, y);
            }
        }
        if (!crestOnly) {
            path.lineTo(w, h);
            path.close();
        }
        return path;
    }

    private static float nextRandom(long[] state) {
        state[0] = (state[0] * 16807L) % 2147483647L;
        return state[0] / 2147483647f;
    }

    private static void hills(Canvas c, float w, float h, int[] sky, float sunX, float sunY,
                              int glow, float glowA, int[][] layers) {
        c.drawRect(0f, 0f, w, h, fill(vertical(0f, h * 0.75f, sky)));
        glowAt(c, w, h, w * sunX, h * sunY, w * 0.45f, glow, glowA);
        for (int i = 0; i < layers.length; i++) {
            float base = h * (0.45f + i * 0.11f);
            float amp = h * (0.09f - i * 0.006f);
            Path layer = ridge(w, h, base, amp, 7 + i * 13, false);
            c.drawPath(layer, fill(vertical(base - h * 0.1f, h, layers[i])));
        }
    }

    private static void dunes(Canvas c, float w, float h, int[] sky, float sunX, float sunY,
                              int glow, float glowA, int[][] lit, int[] shade) {
        c.drawRect(0f, 0f, w, h, fill(vertical(0f, h * 0.6f, sky)));
        glowAt(c, w, h, w * sunX, h * sunY, w * 0.4f, glow, glowA);
        for (int i = 0; i < 4; i++) {
            float base = h * (0.5f + i * 0.12f);
            float amp = h * (0.06f - i * 0.008f);
            Path dune = ridge(w, h, base, amp, 41 + i * 17, false);
            c.drawPath(dune, fill(vertical(base - h * 0.08f, h, lit[i])));
            // Light from the sun's side, the far side falling into shadow: a sideways gradient
            // across the dune, so the turn from lit to shaded is soft, as on real sand.
            int color = shade[i];
            int rgb = color & 0xFFFFFF;
            float fromX = sunX > 0.5f ? w : 0f;
            Paint side = new Paint(Paint.ANTI_ALIAS_FLAG);
            side.setShader(new LinearGradient(fromX, 0f, w - fromX, 0f,
                    new int[]{rgb, rgb, color}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
            c.drawPath(dune, side);
            // A bright crest line where the light grazes the top.
            c.save();
            c.translate(0f, h * 0.004f);
            Path crest = ridge(w, h, base, amp, 41 + i * 17, true);
            Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(h * 0.003f);
            stroke.setColor(0x33FFFFFF);
            c.drawPath(crest, stroke);
            c.restore();
        }
    }

    private static void sea(Canvas c, float w, float h, int[] sky, int glow, float glowA,
                            int[] water, int sun, int glint) {
        float horizon = h * 0.58f;
        c.drawRect(0f, 0f, w, horizon, fill(vertical(0f, horizon, sky)));
        c.save();
        c.clipRect(0f, 0f, w, horizon);
        glowAt(c, w, h, w * 0.5f, horizon, w * 0.5f, glow, glowA);
        glowAt(c, w, h, w * 0.5f, horizon, w * 0.06f, sun, 1f);
        c.restore();
        c.drawRect(0f, horizon, w, h, fill(vertical(horizon, h, water)));
        // The sun's path on the water: lines that widen and fade toward the viewer.
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float unit = h / 270f;
        for (int i = 0; i < 26; i++) {
            float y = horizon + (3f + i * i * 0.18f) * unit;
            float half = w * (0.05f + i * 0.008f) / 2f;
            int alpha = (int) ((0.55f - i * 0.02f) * 255f);
            p.setColor((glint & 0xFFFFFF) | (alpha << 24));
            c.drawRect(w * 0.5f - half, y, w * 0.5f + half, y + 1.2f * unit, p);
        }
    }

    private static void waves(Canvas c, float w, float h, int bgTop, int bgBottom, int[][] bands) {
        c.drawRect(0f, 0f, w, h, fill(vertical(0f, h, new int[]{bgTop, bgBottom})));
        float unit = w / 480f;
        float edge = w * 0.04f;
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x59000000);
        shadow.setMaskFilter(new BlurMaskFilter(24f * unit, BlurMaskFilter.Blur.NORMAL));
        for (int i = 0; i < bands.length; i++) {
            float y0 = h * (0.15f + i * 0.2f);
            Path path = new Path();
            path.moveTo(-edge, h + edge);
            path.lineTo(-edge, y0 + h * 0.12f);
            path.cubicTo(w * 0.3f, y0 - h * 0.18f, w * 0.55f, y0 + h * 0.32f, w + edge, y0 - h * 0.02f);
            path.lineTo(w + edge, h + edge);
            path.close();
            // Each band casts a soft shadow up onto the one behind it.
            c.save();
            c.translate(0f, -6f * unit);
            c.drawPath(path, shadow);
            c.restore();
            c.drawPath(path, fill(vertical(y0 - h * 0.1f, y0 + h * 0.5f, bands[i])));
        }
    }

    /** Large soft blobs of colour: drawn small, blurred until no edge is left, then scaled up. */
    private static void glows(Canvas c, float w, float h, int base, Blob... blobs) {
        int sw = 160;
        int sh = 90;
        Bitmap small = Bitmap.createBitmap(sw, sh, Bitmap.Config.ARGB_8888);
        Canvas sc = new Canvas(small);
        sc.drawColor(base);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        for (Blob b : blobs) {
            p.setColor(b.color);
            float rx = sw * b.r;
            sc.drawOval(new RectF(sw * b.x - rx, sh * b.y - rx * 0.7f,
                    sw * b.x + rx, sh * b.y + rx * 0.7f), p);
        }
        int[] px = new int[sw * sh];
        small.getPixels(px, 0, sw, 0, 0, sw, sh);
        for (int i = 0; i < 4; i++) {
            boxBlur(px, sw, sh, 12);
        }
        small.setPixels(px, 0, sw, 0, 0, sw, sh);
        Paint scaled = new Paint(Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(small, null, new RectF(0f, 0f, w, h), scaled);
        small.recycle();
    }

    static void boxBlur(int[] px, int w, int h, int r) {
        int[] tmp = new int[px.length];
        pass(px, tmp, w, h, r, true);
        pass(tmp, px, w, h, r, false);
    }

    private static void pass(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w;
        int len = horizontal ? w : h;
        for (int l = 0; l < lines; l++) {
            for (int i = 0; i < len; i++) {
                int a = 0;
                int rr = 0;
                int g = 0;
                int b = 0;
                int n = 0;
                for (int k = -r; k <= r; k++) {
                    int j = i + k;
                    if (j < 0) {
                        j = 0;
                    } else if (j > len - 1) {
                        j = len - 1;
                    }
                    int color = horizontal ? src[l * w + j] : src[j * w + l];
                    a += color >>> 24;
                    rr += (color >> 16) & 0xFF;
                    g += (color >> 8) & 0xFF;
                    b += color & 0xFF;
                    n++;
                }
                int value = ((a / n) << 24) | ((rr / n) << 16) | ((g / n) << 8) | (b / n);
                dst[horizontal ? l * w + i : i * w + l] = value;
            }
        }
    }

    /** Fine noise, so long gradients do not band on 8-bit panels. */
    private static void dither(Bitmap b) {
        int w = b.getWidth();
        int h = b.getHeight();
        int[] row = new int[w];
        Random rnd = new Random(7);
        for (int y = 0; y < h; y++) {
            b.getPixels(row, 0, w, 0, y, w, 1);
            for (int x = 0; x < w; x++) {
                int color = row[x];
                int noise = rnd.nextInt(5) - 2;
                int r = clamp255(((color >> 16) & 0xFF) + noise);
                int g = clamp255(((color >> 8) & 0xFF) + noise);
                int bl = clamp255((color & 0xFF) + noise);
                row[x] = (color & 0xFF000000) | (r << 16) | (g << 8) | bl;
            }
            b.setPixels(row, 0, w, 0, y, w, 1);
        }
    }

    private static int clamp255(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
