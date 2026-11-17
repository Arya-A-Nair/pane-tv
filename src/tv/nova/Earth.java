package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * The Earth as fine points, lit by the sun's real position at a given moment.
 * Land from Natural Earth (public domain), city lights from its populated places.
 * Geometry is computed once; a render only relights and redraws (~1 s on the stick,
 * done off the UI thread every few minutes, so the terminator creeps across in real time).
 */
final class Earth {
    // view: tipped south so India sits high on the disc, above the screen edge the planet rises from
    static final double VIEW_LON = 74, VIEW_LAT = -22;
    private static final int N = 64000;

    private final float[] px, py, pz;        // unit vectors of visible-hemisphere points
    private final boolean[] land;
    private final float[] cityNear;          // 0..1, how close to a populated place
    private final int count;

    Earth(Context ctx) {
        Bitmap mask = null;
        List<double[]> cities = new ArrayList<>();
        try {
            mask = BitmapFactory.decodeStream(ctx.getAssets().open("earth/land.png"));
            BufferedReader r = new BufferedReader(new InputStreamReader(ctx.getAssets().open("earth/cities.txt")));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split(",");
                cities.add(vec(Double.parseDouble(p[0]), Double.parseDouble(p[1])));
            }
        } catch (Exception ignored) { }
        int mw = mask == null ? 1 : mask.getWidth(), mh = mask == null ? 1 : mask.getHeight();
        int[] pix = new int[mw * mh];
        if (mask != null) { mask.getPixels(pix, 0, mw, 0, 0, mw, mh); mask.recycle(); }

        float[] tx = new float[N], ty = new float[N], tz = new float[N], tc = new float[N];
        boolean[] tl = new boolean[N];
        double ga = Math.PI * (3 - Math.sqrt(5)), cosCity = Math.cos(Math.toRadians(2.2));
        int n = 0;
        for (int i = 0; i < N; i++) {
            double z = 1 - 2 * (i + .5) / N, rr = Math.sqrt(1 - z * z), th = ga * i;
            double x = rr * Math.cos(th), y = rr * Math.sin(th);
            if (depth(x, y, z) <= 0) continue; // far side never shows from this view
            double lat = Math.toDegrees(Math.asin(z)), lon = Math.toDegrees(Math.atan2(y, x));
            int mx = (int) ((lon + 180) / 360 * mw) % mw, my = Math.min(mh - 1, Math.max(0, (int) ((90 - lat) / 180 * mh)));
            boolean isLand = (pix[my * mw + mx] & 0xff) > 127;
            if (!isLand && i % 3 != 0) continue; // ocean only sparsely, to show the sphere
            float near = 0;
            if (isLand) for (double[] c : cities) {
                double d = x * c[0] + y * c[1] + z * c[2];
                if (d > cosCity) near = Math.max(near, (float) ((d - cosCity) / (1 - cosCity)));
            }
            tx[n] = (float) x; ty[n] = (float) y; tz[n] = (float) z; tl[n] = isLand; tc[n] = near;
            n++;
        }
        count = n;
        px = tx; py = ty; pz = tz; land = tl; cityNear = tc;
    }

    static double[] vec(double lat, double lon) {
        double la = Math.toRadians(lat), lo = Math.toRadians(lon);
        return new double[]{Math.cos(la) * Math.cos(lo), Math.cos(la) * Math.sin(lo), Math.sin(la)};
    }

    // rotate world -> view; returns {depth toward viewer, screen x, screen y} in unit sphere coords
    private static final double CL = Math.toRadians(VIEW_LON), CA = Math.toRadians(VIEW_LAT);
    static double depth(double x, double y, double z) {
        double x1 = x * Math.cos(-CL) - y * Math.sin(-CL);
        return x1 * Math.cos(CA) + z * Math.sin(CA);
    }
    static double[] project(double x, double y, double z) {
        double x1 = x * Math.cos(-CL) - y * Math.sin(-CL), y1 = x * Math.sin(-CL) + y * Math.cos(-CL);
        double x2 = x1 * Math.cos(CA) + z * Math.sin(CA), z2 = -x1 * Math.sin(CA) + z * Math.cos(CA);
        return new double[]{x2, y1, z2};
    }

    /** Where a place lands on a rendered bitmap of the given size; null if on the far side. */
    static float[] locate(double lat, double lon, int size) {
        double[] v = vec(lat, lon), p = project(v[0], v[1], v[2]);
        if (p[0] <= 0) return null;
        float R = size / 2f * 0.93f, C = size / 2f;
        return new float[]{(float) (C + p[1] * R), (float) (C - p[2] * R)};
    }

    /** Sub-solar point (lat, lon) for a UTC instant, from the standard low-precision solar formulae. */
    static double[] subSolar(long utcMillis) {
        double d = utcMillis / 86400000.0 + 2440587.5 - 2451545.0;
        double g = Math.toRadians((357.529 + 0.98560028 * d) % 360), q = (280.459 + 0.98564736 * d) % 360;
        double L = Math.toRadians(q + 1.915 * Math.sin(g) + 0.020 * Math.sin(2 * g)), e = Math.toRadians(23.439 - 0.00000036 * d);
        double ra = Math.toDegrees(Math.atan2(Math.cos(e) * Math.sin(L), Math.cos(L)));
        double dec = Math.toDegrees(Math.asin(Math.sin(e) * Math.sin(L)));
        double eqt = ((q - ra) % 360 + 540) % 360 - 180; // degrees
        double utcH = (utcMillis % 86400000L) / 3600000.0;
        double lon = 15 * (12 - utcH) - eqt;
        lon = ((lon + 540) % 360) - 180;
        return new double[]{dec, lon};
    }

    /** Draw the lit Earth into a square bitmap. dimNight < 1 darkens the night side further (ambient). */
    Bitmap render(int size, long utcMillis) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        double[] ss = subSolar(utcMillis), sun = vec(ss[0], ss[1]);
        float R = size / 2f * 0.93f, C = size / 2f, dot = Math.max(1f, size / 1210f);
        // the night-side body: opaque, so the starfield never shows through the planet
        p.setColor(0xff07080b);
        c.drawCircle(C, C, R + dot, p);
        for (int i = 0; i < count; i++) {
            double[] pr = project(px[i], py[i], pz[i]);
            double dep = pr[0];
            double light = px[i] * sun[0] + py[i] * sun[1] + pz[i] * sun[2];
            float day = (float) Math.max(0, Math.min(1, (light + 0.08) / 0.22));
            float limb = (float) (0.35 + 0.65 * dep);
            float X = (float) (C + pr[1] * R), Y = (float) (C - pr[2] * R);
            float a, rad;
            int rgb;
            if (land[i]) {
                a = (0.16f + 0.84f * day) * limb;
                rgb = 0xe9ecf2;
                if (day < 0.5f && cityNear[i] > 0) {
                    float glow = cityNear[i] * 0.95f * (1 - day);
                    if (glow > a) { a = glow; rgb = 0xe8c48c; } // city lights on the night side
                }
                rad = dot * 1.3f * (0.6f + 0.4f * (float) dep);
            } else {
                a = (0.035f + 0.07f * day) * limb;
                rgb = 0xa4b8ee;
                rad = dot * 0.8f * (0.6f + 0.4f * (float) dep);
            }
            p.setColor(((int) (Math.min(1f, a) * 255) << 24) | rgb);
            c.drawCircle(X, Y, rad, p);
        }
        // atmosphere: lit by the real sun. Cool white along the daylit edge, amber where it meets night.
        double[] sv = project(sun[0], sun[1], sun[2]);              // {toward viewer, screen x, screen up}
        double sl = Math.sqrt(sv[1] * sv[1] + sv[2] * sv[2]) + 1e-6;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.BUTT);
        android.graphics.RectF ring = new android.graphics.RectF();
        for (int deg = 0; deg < 360; deg++) {
            double t = Math.toRadians(deg);
            double facing = (Math.cos(t) * sv[1] + Math.sin(t) * sv[2]) / sl; // 1 = edge toward the sun
            double lit = Math.max(0, facing) * (0.55 + 0.45 * Math.max(0, -sv[0] + 0.6)); // brighter when backlit
            if (lit < 0.02) continue;
            double warm = Math.max(0, 1 - Math.abs(facing - 0.15) / 0.35);  // near the terminator
            int col = mix(0xbcd0ff, 0xf0a060, (float) Math.min(1, warm));
            float start = -deg - 0.6f; // canvas angles run clockwise from +x; ours counter-clockwise
            for (int k = 0; k < 3; k++) {
                float rr = R + dot * (1.5f + k * 3.2f), wdt = dot * (1.6f + k * 3f);
                ring.set(C - rr, C - rr, C + rr, C + rr);
                p.setStrokeWidth(wdt);
                p.setColor(((int) (255 * Math.min(1, lit * (k == 0 ? 0.95 : k == 1 ? 0.38 : 0.14))) << 24) | col);
                c.drawArc(ring, start, 1.2f, false, p);
            }
        }
        return b;
    }

    private static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** A fixed, faint field of stars for the sky around the planet. */
    static Bitmap stars(int w, int h, float dp) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        java.util.Random r = new java.util.Random(1977);
        for (int i = 0; i < 260; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h;
            float m = r.nextFloat();
            float size = dp * (m > 0.97f ? 1.3f : m > 0.85f ? 0.9f : 0.55f);
            int a = (int) (255 * (0.12f + 0.5f * m * m));
            int col = r.nextFloat() < 0.12f ? 0xffd2b0 : r.nextFloat() < 0.2f ? 0xc8d6ff : 0xe9ecf2;
            p.setColor((a << 24) | col);
            c.drawCircle(x, y, size, p);
        }
        return b;
    }

    /** Moon age in days since new moon (synodic month from a known new moon). */
    static double moonAge(long utcMillis) {
        double ref = 947182440000.0; // 2000-01-06 18:14 UTC
        double syn = 29.530588853;
        return (((utcMillis - ref) / 86400000.0) % syn + syn) % syn;
    }

    static String moonName(double age) {
        String[] n = {"New moon", "Waxing crescent", "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter", "Waning crescent"};
        return n[(int) Math.floor(age / 29.530588853 * 8 + 0.5) % 8];
    }

    /**
     * The moon at its current phase, from NASA's LRO surface map (public domain): near side
     * facing us, lit by a sun whose angle follows the synodic month, faint earthshine on the dark side.
     */
    static Bitmap moon(Context ctx, int size, double age) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Bitmap map;
        try { map = BitmapFactory.decodeStream(ctx.getAssets().open("earth/moon.jpg")); } catch (Exception e) { return b; }
        int mw = map.getWidth(), mh = map.getHeight();
        int[] tex = new int[mw * mh];
        map.getPixels(tex, 0, mw, 0, 0, mw, mh);
        map.recycle();
        double phi = 2 * Math.PI * age / 29.530588853;
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
    static double moonLit(double age) { return (1 - Math.cos(2 * Math.PI * age / 29.530588853)) / 2; }
}
