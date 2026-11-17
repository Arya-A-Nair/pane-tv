package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/**
 * The environment: a full-resolution photograph behind everything, and a blurred copy of it
 * that glass surfaces sample from at their own position. Because the scene is known, every
 * panel shows exactly what is behind it, frosted — real glass, not a tinted box.
 */
final class Env {
    static final String[] FILES = {"env/dusk.jpg", "env/lagoon.jpg", "env/bloom.jpg", "env/ember.jpg", "env/graphite.jpg",
            "env/earthset.jpg", "env/sunrise.jpg", "env/aurora.jpg", "env/marble.jpg"};
    static final String[] NAMES = {"Dusk", "Lagoon", "Bloom", "Ember", "Graphite", "Earthset", "Sunrise", "Aurora", "Blue Marble"};
    static final String[] CREDITS = {"Colour", "Colour", "Colour", "Colour", "Colour",
            "Artemis II, NASA", "From the ISS, NASA", "From the ISS, NASA", "NASA"};

    static Bitmap photo, frost;   // frost is small; glass scales it up to screen size
    static int index = -1;
    static final int FROST_SCALE = 8;

    /** Load environment i at screen size, and build its frosted copy. */
    static void load(Context c, int i, int w, int h) {
        if (i == index && photo != null) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeStream(c.getAssets().open(FILES[i]), null, o);
            Bitmap scaled = src.getWidth() == w && src.getHeight() == h ? src : Bitmap.createScaledBitmap(src, w, h, true);
            if (scaled != src) src.recycle();
            Bitmap small = Bitmap.createScaledBitmap(scaled, Math.max(8, w / FROST_SCALE), Math.max(8, h / FROST_SCALE), true);
            Bitmap blurred = blur(small, 7);
            small.recycle();
            // glass lifts what is behind it: a little brighter, a little more saturated
            Bitmap lifted = Bitmap.createBitmap(blurred.getWidth(), blurred.getHeight(), Bitmap.Config.ARGB_8888);
            ColorMatrix m = new ColorMatrix();
            m.setSaturation(1.35f);
            ColorMatrix lift = new ColorMatrix(new float[]{1.05f,0,0,0,14, 0,1.05f,0,0,14, 0,0,1.05f,0,16, 0,0,0,1,0});
            m.postConcat(lift);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(m));
            new Canvas(lifted).drawBitmap(blurred, 0, 0, p);
            blurred.recycle();
            if (photo != null) photo.recycle();
            if (frost != null) frost.recycle();
            photo = scaled;
            frost = lifted;
            index = i;
        } catch (Exception ignored) { }
    }

    /** Separable box blur run three times (close to Gaussian); fine on a small bitmap. */
    static Bitmap blur(Bitmap in, int r) {
        int w = in.getWidth(), h = in.getHeight();
        int[] a = new int[w * h], b = new int[w * h];
        in.getPixels(a, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) { box(a, b, w, h, r, true); box(b, a, w, h, r, false); }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(a, 0, w, 0, 0, w, h);
        return out;
    }

    private static void box(int[] src, int[] dst, int w, int h, int r, boolean horizontal) {
        int n = horizontal ? w : h, lines = horizontal ? h : w;
        for (int line = 0; line < lines; line++) {
            int sr = 0, sg = 0, sb = 0;
            for (int k = -r; k <= r; k++) {
                int c = src[idx(Math.max(0, Math.min(n - 1, k)), line, w, horizontal)];
                sr += (c >> 16) & 255; sg += (c >> 8) & 255; sb += c & 255;
            }
            int div = 2 * r + 1;
            for (int i = 0; i < n; i++) {
                dst[idx(i, line, w, horizontal)] = 0xff000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int out = src[idx(Math.max(0, i - r), line, w, horizontal)], in = src[idx(Math.min(n - 1, i + r + 1), line, w, horizontal)];
                sr += ((in >> 16) & 255) - ((out >> 16) & 255);
                sg += ((in >> 8) & 255) - ((out >> 8) & 255);
                sb += (in & 255) - (out & 255);
            }
        }
    }

    private static int idx(int i, int line, int w, boolean horizontal) { return horizontal ? line * w + i : i * w + line; }
}
