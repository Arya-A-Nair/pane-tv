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
        // atmosphere: a single hairline at the limb
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1f, size / 900f));
        p.setColor(0x1aa4b8ee);
        c.drawCircle(C, C, R + 2 * dot, p);
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

    /** The moon at its current phase, as seen from the northern hemisphere. */
    static Bitmap moon(int size, double age) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        double syn = 29.530588853, f = age / syn;           // 0 new, .5 full
        double lit = (1 - Math.cos(2 * Math.PI * f)) / 2;   // illuminated fraction
        boolean waxing = f < 0.5;
        float C = size / 2f, R = size / 2f - 1;
        java.util.Random rnd = new java.util.Random(7);
        float[][] craters = new float[60][3];
        for (float[] cr : craters) { cr[0] = (rnd.nextFloat() * 2 - 1) * R; cr[1] = (rnd.nextFloat() * 2 - 1) * R; cr[2] = R * (rnd.nextFloat() < .15f ? .12f + rnd.nextFloat() * .14f : .02f + rnd.nextFloat() * .05f); }
        double xt = 1 - 2 * lit;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                double nx = (x - C) / R, ny = (y - C) / R, rr = nx * nx + ny * ny;
                if (rr > 1) continue;
                double sx = waxing ? nx : -nx, edge = xt * Math.sqrt(Math.max(0, 1 - ny * ny));
                double l = Math.max(0, Math.min(1, (sx - edge) * size / 14 + 0.5));
                double tex = 0;
                for (float[] cr : craters) {
                    double dx = (x - C) - cr[0], dy = (y - C) - cr[1], d = dx * dx + dy * dy;
                    if (d < cr[2] * cr[2]) tex = Math.max(tex, 0.35 * (1 - d / (cr[2] * cr[2])));
                }
                double shade = 0.55 + 0.45 * Math.sqrt(1 - rr), v = (0.88 - tex) * shade * l + 0.04 * (1 - l);
                int a = rr < 0.97 ? 255 : (int) (255 * (1 - rr) / 0.03);
                b.setPixel(x, y, (a << 24) | ((int) (240 * v) << 16) | ((int) (236 * v) << 8) | (int) (226 * v));
            }
        return b;
    }
}
