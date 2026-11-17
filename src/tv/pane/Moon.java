package tv.pane;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/** Tonight's moon: its phase from the synodic month, and a picture of it rendered from NASA's surface map. */
final class Moon {
    private Moon() {}
    static final double SYNODIC = 29.530588853;

    /** Moon age in days since new moon (synodic month from a known new moon). */
    static double age(long utcMillis) {
        double ref = 947182440000.0; // 2000-01-06 18:14 UTC
        return (((utcMillis - ref) / 86400000.0) % SYNODIC + SYNODIC) % SYNODIC;
    }

    static String name(double age) {
        String[] n = {"New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent"};
        return n[(int) Math.floor(age / SYNODIC * 8 + 0.5) % 8];
    }

    /**
     * The moon at its current phase, from NASA's LRO surface map (public domain): near side
     * facing us, lit by a sun whose angle follows the synodic month, faint earthshine on the dark side.
     */
    static Bitmap render(Context ctx, int size, double age) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Bitmap map;
        // The map is 2048 x 1024; the disc shows half its width. Skip texels the disc is too small to show.
        BitmapFactory.Options o = new BitmapFactory.Options();
        int sample = 1;
        while (sample < 8 && 1024 / (sample * 2) >= size) sample *= 2;
        o.inSampleSize = sample;
        try { map = BitmapFactory.decodeStream(ctx.getAssets().open("moon.jpg"), null, o); } catch (Exception e) { return b; }
        int mw = map.getWidth(), mh = map.getHeight();
        int[] tex = new int[mw * mh];
        map.getPixels(tex, 0, mw, 0, 0, mw, mh);
        map.recycle();
        double phi = 2 * Math.PI * age / SYNODIC;
        double sx = Math.sin(phi), sz = -Math.cos(phi);       // first quarter: sun from the right
        float C = size / 2f, R = size / 2f - 0.5f;
        int[] out = new int[size * size];
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double nx = (x + 0.5 - C) / R, ny = (y + 0.5 - C) / R, rr = nx * nx + ny * ny;
                if (rr > 1) continue;
                double nz = Math.sqrt(1 - rr);
                double lat = Math.asin(-ny), lon = Math.atan2(nx, nz);
                int u = (int) ((lon / (2 * Math.PI) + 0.5) * mw) % mw, v = Math.min(mh - 1, (int) ((0.5 - lat / Math.PI) * mh));
                double t = (tex[v * mw + u] & 0xff) / 255.0;
                t = Math.min(1, Math.max(0, (t - 0.12) * 1.35));  // a little more relief
                double lambert = nx * sx + nz * sz;
                double lit = Math.max(0, Math.min(1, lambert * 9 + 0.15)) * (0.35 + 0.65 * Math.max(0, lambert));
                double val = t * (0.18 + 0.95 * lit) * (lit > 0.01 ? 1 : 0) + t * 0.07 * (1 - Math.min(1, lit * 4)); // earthshine
                double edge = rr > 0.985 ? (1 - rr) / 0.015 : 1;
                int r = (int) Math.min(255, 244 * val), g = (int) Math.min(255, 240 * val), bl = (int) Math.min(255, 230 * val);
                out[y * size + x] = ((int) (255 * edge) << 24) | (r << 16) | (g << 8) | bl;
            }
        b.setPixels(out, 0, size, 0, 0, size, size);
        return b;
    }

    /** Fraction of the disc that is lit. */
    static double lit(double age) { return (1 - Math.cos(2 * Math.PI * age / SYNODIC)) / 2; }
}
