package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/**
 * The environment: a full-resolution still behind everything, and "frost" — a small, heavily
 * blurred copy that glass samples at its own place on screen, so every panel shows what is
 * really behind it. Frost is tone-mapped once here: it takes on the colour of the scene's
 * light and its luminance is capped, which is what guarantees text contrast on any glass.
 * See DESIGN.md, "Materials".
 */
final class Env {
    static final String[] IDS = {"nocturne", "tide", "ember", "dune", "orchid", "graphite", "earthset", "sunrise", "aurora", "marble"};
    static final String[] NAMES = {"Nocturne", "Tide", "Ember", "Dune", "Orchid", "Graphite", "Earthset", "Orbital sunrise", "Aurora", "Blue Marble"};
    static final String[] CREDITS = {"Original artwork", "Original artwork", "Original artwork", "Original artwork", "Original artwork", "Original artwork",
            "Photograph: Artemis II, NASA", "Photograph: International Space Station, NASA", "Photograph: International Space Station, NASA", "Photograph: NASA"};

    static Bitmap photo, frost;   // frost is small; glass scales it up to screen size
    static int index = -1;
    static final int FROST_SCALE = 8;
    /** Highest relative luminance frost may reach; sheen and grain add about 0.02 on top. Measured contrast is in DESIGN.md. */
    static final float GLASS_CAP = 0.068f;

    static int indexOf(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    /** Load environment i at screen size, and build its frost. */
    static void load(Context c, int i, int w, int h) {
        if (i == index && photo != null) return;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap src = BitmapFactory.decodeStream(c.getAssets().open("env/" + IDS[i] + ".jpg"), null, o);
            Bitmap scaled = src.getWidth() == w && src.getHeight() == h ? src : Bitmap.createScaledBitmap(src, w, h, true);
            if (scaled != src) src.recycle();
            Bitmap small = Bitmap.createScaledBitmap(scaled, Math.max(8, w / FROST_SCALE), Math.max(8, h / FROST_SCALE), true);
            Bitmap f = tone(blur(small, 7));
            small.recycle();
            Bitmap oldPhoto = photo, oldFrost = frost;
            photo = scaled;
            frost = f;
            index = i;
            if (oldPhoto != null) oldPhoto.recycle();
            if (oldFrost != null) oldFrost.recycle();
        } catch (Exception ignored) { }
    }

    private static final float[] LIN = new float[256];
    static { for (int i = 0; i < 256; i++) { float c = i / 255f; LIN[i] = c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4); } }
    private static int enc(float l) { l = Math.max(0, Math.min(1, l)); return Math.round(255 * (l <= 0.0031308f ? l * 12.92f : 1.055f * (float) Math.pow(l, 1 / 2.4) - 0.055f)); }

    /**
     * Glass tone: more saturated than the scene, lifted, lit a little everywhere by the colour of
     * the scene's light (so glass over a black corner is still coloured, never grey), then
     * luminance soft-capped at GLASS_CAP with hue preserved.
     */
    private static Bitmap tone(Bitmap b) {
        int w = b.getWidth(), h = b.getHeight(), n = w * h;
        int[] px = new int[n];
        b.getPixels(px, 0, w, 0, 0, w, h);
        double kr = 0, kg = 0, kb = 0, ks = 0;
        for (int p : px) {
            float r = LIN[(p >> 16) & 255], g = LIN[(p >> 8) & 255], bl = LIN[p & 255], l = 0.2126f * r + 0.7152f * g + 0.0722f * bl;
            kr += r * l; kg += g * l; kb += bl * l; ks += l * l;   // weighted by brightness: the colour of the light
        }
        float key = (float) (0.2126 * kr + 0.7152 * kg + 0.0722 * kb) + 1e-6f;
        float fr = (float) kr / key, fg = (float) kg / key, fb = (float) kb / key;      // light colour at luminance 1
        fr = Math.max(0.05f, 1 + (fr - 1) * 1.5f); fg = Math.max(0.05f, 1 + (fg - 1) * 1.5f); fb = Math.max(0.05f, 1 + (fb - 1) * 1.5f); // away from grey
        final float FLOOR = 0.028f, LIFT = 1.7f, SAT = 1.35f;
        for (int i = 0; i < n; i++) {
            int p = px[i];
            float r = LIN[(p >> 16) & 255], g = LIN[(p >> 8) & 255], bl = LIN[p & 255], l = 0.2126f * r + 0.7152f * g + 0.0722f * bl;
            r = Math.max(0, l + (r - l) * SAT) * LIFT + fr * FLOOR;
            g = Math.max(0, l + (g - l) * SAT) * LIFT + fg * FLOOR;
            bl = Math.max(0, l + (bl - l) * SAT) * LIFT + fb * FLOOR;
            l = 0.2126f * r + 0.7152f * g + 0.0722f * bl + 1e-6f;
            float k = GLASS_CAP * (float) Math.tanh(l / GLASS_CAP) / l;
            px[i] = 0xff000000 | (enc(r * k) << 16) | (enc(g * k) << 8) | enc(bl * k);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
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
