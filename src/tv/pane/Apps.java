package tv.pane;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Installed apps: a round icon and an accent colour for each, the mark from its TV banner, the favourites shown on Home, and when each was last opened. */
final class Apps {
    static final class App {
        String pkg, label;
        Intent launch;
        Bitmap round;       // circular icon, from the app's highest-resolution icon
        int accent;         // dominant vivid colour of the banner
        // Filled by loadMark, on first need:
        Bitmap logo;        // the mark alone, lifted off the banner's background; null when the banner is not a clean mark
        float logoSharp;    // widest the logo can be shown (px) before it is being enlarged
        private ResolveInfo info;
        private boolean markLoaded;
    }

    // Shown first on a fresh install, in this order, if present.
    static final List<String> DEFAULT_FAVOURITES = Arrays.asList(
            "com.netflix.ninja", "com.amazon.firebat", "in.startv.hotstar", "com.amazon.firetv.youtube",
            "com.zee5.amazon", "com.onemainstream.sonyliv.android", "com.jio.media.stb.ondemand",
            "com.mxtech.videoplayer.television");
    // Never shown: ourselves and launcher plumbing.
    static final List<String> HIDDEN = Arrays.asList("tv.pane",
            "tv.projectivy.plugin.wallpaperprovider.overflight", "com.amazon.tv.launcher", "com.amazon.ftv.profilepicker",
            "com.amazon.ftv.screensaver", "com.amazon.hedwig", "com.amazon.ssm", "com.amazon.vizzini.ftvcds",
            "com.android.documentsui", "com.android.providers.downloads.ui");

    /** The largest an icon is ever drawn, in dp: the app icon flying to the centre when an app opens (Pane.launch). */
    static final float MAX_ICON_DP = 112;
    private final int iconPx, markW, markH, density;   // icon and banner sizes follow the display; see the constructor
    static final int HOME_SLOTS = 6; // favourites shown on Home; the rest stay one page away

    final List<App> all = new ArrayList<>();
    final Map<String, App> byPkg = new HashMap<>();
    private final SharedPreferences prefs;
    private final PackageManager pm;

    Apps(Context ctx) {
        prefs = ctx.getSharedPreferences("pane", Context.MODE_PRIVATE);
        android.util.DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        density = Math.max(android.util.DisplayMetrics.DENSITY_XXXHIGH, dm.densityDpi);   // never decode below xxxhdpi, more on a denser screen
        iconPx = Math.max(192, Math.min(512, Math.round(MAX_ICON_DP * dm.density * 1.1f)));   // 246 px at 1080p, 493 px at 4K
        markW = Math.max(960, Math.round(480 * dm.density));   // banners are lifted at 960 x 540 at 1080p, 1920 x 1080 at 4K
        markH = markW * 9 / 16;
        pm = ctx.getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (byPkg.containsKey(pkg) || HIDDEN.contains(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm)).split(":| [-–|] | by ")[0].trim();   // "Romance Movies & TV by Fawesome" -> "Romance Movies & TV"
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) continue;
            a.launch = li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            a.info = ri;
            Bitmap small = banner(ri, 96, 54, null);
            a.accent = accent(small);
            small.recycle();
            a.round = round(ri, a);
            all.add(a);
            byPkg.put(pkg, a);
        }
        Collections.sort(all, new Comparator<App>() {
            public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
    }

    /**
     * Lift the app's mark off its TV banner, for the hero on Home. This takes about 170 ms on the
     * stick, so it is not done for every app at start: Home warms its own row in the background.
     */
    void loadMark(App a) {
        synchronized (a) {
            if (a.markLoaded) return;
            float[] detail = new float[1];
            Bitmap banner = banner(a.info, markW, markH, detail);
            Bitmap logo = logo(banner);
            banner.recycle();
            if (logo != null) {
                // A real mark fills a fair part of its own box. A box held open by stray specks does not,
                // and one that is square or filled edge to edge is the app's tile, not its mark.
                Bitmap t = Bitmap.createScaledBitmap(logo, 48, 24, true);
                int solid = 0;
                for (int y = 0; y < 24; y++) for (int x = 0; x < 48; x++) if ((t.getPixel(x, y) >>> 24) > 128) solid++;
                t.recycle();
                float filled = solid / (48f * 24f);
                boolean tile = filled > 0.7f || logo.getWidth() < logo.getHeight() * 1.1f;
                // A mark lifted cleanly is either there or not. Lifted off a photograph, most of it is half there.
                int[] px = new int[logo.getWidth() * logo.getHeight()];
                logo.getPixels(px, 0, logo.getWidth(), 0, 0, logo.getWidth(), logo.getHeight());
                int seen = 0, faint = 0;
                for (int i = 0; i < px.length; i += 3) { int alpha = px[i] >>> 24; if (alpha > 30) { seen++; if (alpha < 220) faint++; } }
                boolean ragged = faint > seen * 0.25f;
                a.logoSharp = filled < 0.14f || tile || ragged ? 0 : logo.getWidth() * detail[0] * 1.2f;
                a.logo = logo;
            }
            a.markLoaded = true;
        }
    }

    /** Load the marks of these apps off the main thread, so they are ready before focus reaches them. */
    void warmMarks(final List<App> apps) {
        new Thread(() -> { for (App a : apps) loadMark(a); }, "marks").start();
    }

    /**
     * The app's TV banner at the highest density it ships (else its icon on a plate), drawn at w x h.
     * detail[0], if asked for, receives how much real detail that holds: 1 when the source is at least that big.
     */
    private Bitmap banner(ResolveInfo ri, int w, int h, float[] detail) {
        ApplicationInfo ai = ri.activityInfo.applicationInfo;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Drawable d = null;
        try {
            int res = ri.activityInfo.banner != 0 ? ri.activityInfo.banner : ai.banner;
            if (res != 0) d = pm.getResourcesForApplication(ai).getDrawableForDensity(res, density);
        } catch (Exception ignored) { }   // no such density, or the package went away: fall back below
        if (d == null) d = ri.activityInfo.loadBanner(pm);
        if (d == null) d = pm.getApplicationBanner(ai);
        if (d != null) {
            int sourceWidth = d instanceof android.graphics.drawable.BitmapDrawable && ((android.graphics.drawable.BitmapDrawable) d).getBitmap() != null
                    ? ((android.graphics.drawable.BitmapDrawable) d).getBitmap().getWidth() : w;
            if (detail != null) detail[0] = Math.min(1f, sourceWidth / (float) w);
            d.setBounds(0, 0, w, h);
            d.draw(c);
            return b;
        }
        Drawable icon = ri.loadIcon(pm);
        c.drawColor(0xff20242c);
        int s = h / 2;
        icon.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        icon.draw(c);
        return b;
    }

    /** Most vivid colour of the banner, weighted by saturation x value, normalised for use as light. */
    static int accent(Bitmap b) {
        Bitmap s = Bitmap.createScaledBitmap(b, 24, 14, true);
        float[] hsv = new float[3];
        double wx = 0, wy = 0, wsum = 0, vsum = 0;
        for (int y = 0; y < s.getHeight(); y++)
            for (int x = 0; x < s.getWidth(); x++) {
                Color.colorToHSV(s.getPixel(x, y), hsv);
                double w = hsv[1] * hsv[1] * hsv[2];
                double h = Math.toRadians(hsv[0]);
                wx += Math.cos(h) * w; wy += Math.sin(h) * w; wsum += w; vsum += hsv[1] * w;
            }
        s.recycle();
        if (wsum < 0.5) return 0xffa4b8ee; // monochrome banner: Glacier
        float hue = (float) ((Math.toDegrees(Math.atan2(wy, wx)) + 360) % 360);
        float sat = (float) Math.min(0.85, Math.max(0.45, vsum / wsum));
        return Color.HSVToColor(new float[]{hue, sat, 1f});
    }

    /**
     * The app's own icon at the highest density it ships, cropped to a circle. If the icon is
     * small or transparent-edged (a mark on nothing), it sits on a disc of the app's colour
     * with its banner mark instead, so every icon is a full, rich circle.
     */
    private Bitmap round(ResolveInfo ri, App a) {
        final int SIZE = iconPx;
        Bitmap out = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.Path circle = new android.graphics.Path();
        circle.addCircle(SIZE / 2f, SIZE / 2f, SIZE / 2f, android.graphics.Path.Direction.CW);
        c.clipPath(circle);
        Drawable icon = null;
        try {
            int res = ri.activityInfo.getIconResource();
            if (res == 0) res = ri.activityInfo.applicationInfo.icon;
            icon = pm.getResourcesForApplication(ri.activityInfo.applicationInfo).getDrawableForDensity(res, density);
        } catch (Exception ignored) { }
        if (icon == null) icon = ri.loadIcon(pm);
        Bitmap ib = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, SIZE, SIZE);
        icon.draw(new Canvas(ib));
        // does the icon fill its square (an opaque tile) or float on transparency?
        int corners = 0;
        int[] probe = {ib.getPixel(SIZE / 8, SIZE / 2), ib.getPixel(SIZE / 2, SIZE / 8), ib.getPixel(SIZE * 7 / 8, SIZE / 2), ib.getPixel(SIZE / 2, SIZE * 7 / 8)};
        for (int px : probe) if ((px >>> 24) > 200) corners++;
        if (corners >= 3) {
            // opaque tile: find the tile's own edges (icons often ship with a margin)
            int[] px = new int[SIZE * SIZE];
            ib.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE);
            int x0 = SIZE, y0 = SIZE, x1 = 0, y1 = 0;
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if ((px[y * SIZE + x] >>> 24) > 200) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
            }
            float bw = Math.max(1, x1 - x0), bh = Math.max(1, y1 - y0);
            float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
            float cover = SIZE / Math.min(bw, bh) * 1.16f, sc = cover;  // past the tile's rounded corners
            // If the tile is a mark on a plain colour, fill the disc with that colour and scale so the
            // whole mark fits inside the circle, instead of cropping whatever falls outside it.
            long sr = 0, sg = 0, sb = 0; int n = 0, in = 4;
            for (int t = (int) (bw * 0.2f); t < bw * 0.8f; t += 2) for (int y : new int[]{y0 + in, y1 - in}) { int q = px[y * SIZE + x0 + t]; sr += (q >> 16) & 255; sg += (q >> 8) & 255; sb += q & 255; n++; }
            for (int t = (int) (bh * 0.2f); t < bh * 0.8f; t += 2) for (int x : new int[]{x0 + in, x1 - in}) { int q = px[(y0 + t) * SIZE + x]; sr += (q >> 16) & 255; sg += (q >> 8) & 255; sb += q & 255; n++; }
            int mr = (int) (sr / n), mg = (int) (sg / n), mb = (int) (sb / n), near = 0;
            for (int t = (int) (bw * 0.2f); t < bw * 0.8f; t += 2) for (int y : new int[]{y0 + in, y1 - in}) if (far(px[y * SIZE + x0 + t], mr, mg, mb) < 20) near++;
            for (int t = (int) (bh * 0.2f); t < bh * 0.8f; t += 2) for (int x : new int[]{x0 + in, x1 - in}) if (far(px[(y0 + t) * SIZE + x], mr, mg, mb) < 20) near++;
            if (near > n * 0.97f) {
                double reach = 0;
                for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) if (far(px[y * SIZE + x], mr, mg, mb) > 56) reach = Math.max(reach, Math.hypot(x - cx, y - cy));
                if (reach > 0) sc = (float) Math.min(cover, Math.max(SIZE / Math.min(bw, bh) * 0.82f, SIZE * 0.44f / reach));
                c.drawColor(0xff000000 | (mr << 16) | (mg << 8) | mb);
            }
            c.save();
            c.translate(SIZE / 2f, SIZE / 2f);
            c.scale(sc, sc);
            c.translate(-cx, -cy);
            c.drawBitmap(ib, 0, 0, p);
            c.restore();
        } else {
            int base = a.accent;
            float[] hsv = new float[3];
            Color.colorToHSV(base, hsv);
            hsv[2] = 0.32f; hsv[1] = Math.min(hsv[1], 0.6f);
            int deep = Color.HSVToColor(hsv);
            p.setShader(new android.graphics.LinearGradient(0, 0, 0, SIZE, Color.HSVToColor(new float[]{hsv[0], hsv[1], 0.46f}), deep, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, SIZE, SIZE, p);
            p.setShader(null);
            loadMark(a);
            Bitmap mark = a.logo != null ? a.logo : ib;
            float s = Math.min(SIZE * 0.62f / mark.getWidth(), SIZE * 0.42f / mark.getHeight());
            if (mark == ib) s = 0.7f;
            float mw = mark.getWidth() * s, mh = mark.getHeight() * s;
            c.drawBitmap(mark, null, new android.graphics.RectF((SIZE - mw) / 2, (SIZE - mh) / 2, (SIZE + mw) / 2, (SIZE + mh) / 2), p);
        }
        ib.recycle();
        return out;
    }

    private static int far(int q, int r, int g, int b) { return Math.abs(((q >> 16) & 255) - r) + Math.abs(((q >> 8) & 255) - g) + Math.abs((q & 255) - b); }

    /**
     * Lift the mark off its banner: the background colour (read from the border) becomes
     * transparent, near-black lettering turns porcelain so it reads on a dark tile, and the
     * result is cropped to the mark. Busy banners (photos, gradients) are kept whole.
     */
    static Bitmap logo(Bitmap banner) {
        int w = banner.getWidth(), h = banner.getHeight();
        int[] px = new int[w * h];
        banner.getPixels(px, 0, w, 0, 0, w, h);
        // background estimated per pixel from the four edges, so gradients drop out too
        int e = 6;
        int[] out = new int[w * h];
        int minX = w, minY = h, maxX = -1, maxY = -1, kept = 0;
        for (int y = 0; y < h; y++) {
            int lc = px[y * w + e], rc = px[y * w + w - 1 - e];
            for (int x = 0; x < w; x++) {
                int tc = px[e * w + x], bc = px[(h - 1 - e) * w + x];
                float fx = x / (float) (w - 1), fy = y / (float) (h - 1);
                float br = (((lc >> 16) & 255) * (1 - fx) + ((rc >> 16) & 255) * fx + ((tc >> 16) & 255) * (1 - fy) + ((bc >> 16) & 255) * fy) / 2;
                float bg = (((lc >> 8) & 255) * (1 - fx) + ((rc >> 8) & 255) * fx + ((tc >> 8) & 255) * (1 - fy) + ((bc >> 8) & 255) * fy) / 2;
                float bb = ((lc & 255) * (1 - fx) + (rc & 255) * fx + (tc & 255) * (1 - fy) + (bc & 255) * fy) / 2;
                int c = px[y * w + x], cr = (c >> 16) & 255, cg = (c >> 8) & 255, cb = c & 255;
                double d = Math.sqrt((cr - br) * (cr - br) + (cg - bg) * (cg - bg) + (cb - bb) * (cb - bb));
                double a = Math.max(0, Math.min(1, (d - 40) / 60.0));
                if (a <= 0.02) continue;
                int mx = Math.max(cr, Math.max(cg, cb)), mn = Math.min(cr, Math.min(cg, cb));
                if (mx < 90 && mx - mn < 40) { cr = 233; cg = 236; cb = 242; } // dark lettering -> porcelain
                out[y * w + x] = ((int) (255 * a) << 24) | (cr << 16) | (cg << 8) | cb;
                kept++;
                if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y;
            }
        }
        if (kept < 160 || kept > px.length * 0.5) return null; // not a clean mark
        Bitmap all = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
        int pad = 8;
        minX = Math.max(0, minX - pad); minY = Math.max(0, minY - pad); maxX = Math.min(w - 1, maxX + pad); maxY = Math.min(h - 1, maxY + pad);
        Bitmap crop = Bitmap.createBitmap(all, minX, minY, maxX - minX + 1, maxY - minY + 1);
        if (crop != all) all.recycle();
        return crop;
    }

    // ---- favourites & history ----

    List<App> favourites() {
        String saved = prefs.getString("favs", null);
        List<String> order = saved == null ? DEFAULT_FAVOURITES : Arrays.asList(saved.split(","));
        List<App> out = new ArrayList<>();
        for (String p : order) { App a = byPkg.get(p); if (a != null && !out.contains(a)) out.add(a); }
        if (out.isEmpty()) out.addAll(all.subList(0, Math.min(8, all.size())));
        return out;
    }

    boolean isFavourite(App a) { return favourites().contains(a); }

    /** True if the app has one of the places on Home. */
    boolean onHome(App a) { int i = favourites().indexOf(a); return i >= 0 && i < HOME_SLOTS; }

    /** Every app: the ones on Home first, in Home order, then the rest by name. */
    List<App> ordered() {
        List<App> out = new ArrayList<>(favourites());
        for (App a : all) if (!out.contains(a)) out.add(a);
        return out;
    }

    void toggleFavourite(App a) {
        List<App> f = new ArrayList<>(favourites());
        boolean removed = f.remove(a);
        if (!removed) f.add(Math.min(HOME_SLOTS - 1, f.size()), a);
        markUnpinned(a, removed);   // an app the family took off Home is not auto-added again
        save(f);
    }

    private void save(List<App> f) {
        StringBuilder sb = new StringBuilder();
        for (App x : f) { if (sb.length() > 0) sb.append(','); sb.append(x.pkg); }
        prefs.edit().putString("favs", sb.toString()).apply();
    }

    /** A newly installed app joins Home by itself, unless the family took it off before. */
    void autoPin(String pkg) {
        App a = byPkg.get(pkg);
        if (a == null || isFavourite(a) || prefs.getBoolean("unpinned:" + pkg, false)) return;
        List<App> f = new ArrayList<>(favourites());
        f.add(Math.min(HOME_SLOTS - 1, f.size()), a);
        save(f);
    }

    void markUnpinned(App a, boolean unpinned) { prefs.edit().putBoolean("unpinned:" + a.pkg, unpinned).apply(); }

    void opened(App a) { prefs.edit().putLong("last:" + a.pkg, System.currentTimeMillis()).apply(); }
    long lastOpened(App a) { return prefs.getLong("last:" + a.pkg, 0); }

    /** Up to n most recently opened apps that are not already favourites. */
    List<App> recents(int n) {
        List<App> r = new ArrayList<>();
        for (App a : all) if (lastOpened(a) > 0) r.add(a);
        Collections.sort(r, new Comparator<App>() {
            public int compare(App x, App y) { return Long.compare(lastOpened(y), lastOpened(x)); }
        });
        return r.subList(0, Math.min(n, r.size()));
    }
}
