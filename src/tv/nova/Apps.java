package tv.nova;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
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

/** Installed apps with real banners, an accent colour sampled from each banner, and usage history. */
final class Apps {
    static final class App {
        String pkg, label, version;
        Intent launch;
        Bitmap banner;      // 16:9, decoded once at card size
        Bitmap logo;        // the mark alone, lifted off its banner background, for uniform dark tiles
        Bitmap round;       // visionOS-style circular icon, from the app's highest-resolution icon
        int world;          // WATCH, LISTEN or EXPLORE
        int accent;         // dominant vivid colour of the banner
        long installed;
    }

    // Shown first on a fresh install, in this order, if present.
    static final List<String> DEFAULT_FAVOURITES = Arrays.asList(
            "com.netflix.ninja", "com.amazon.firebat", "in.startv.hotstar", "com.amazon.firetv.youtube",
            "com.zee5.amazon", "com.onemainstream.sonyliv.android", "com.jio.media.stb.ondemand",
            "com.mxtech.videoplayer.television");
    // Never shown: ourselves and launcher plumbing.
    static final List<String> HIDDEN = Arrays.asList("tv.nova", "tv.homeredirect",
            "tv.projectivy.plugin.wallpaperprovider.overflight", "com.amazon.tv.launcher", "com.amazon.ftv.profilepicker",
            "com.amazon.ftv.screensaver", "com.amazon.hedwig", "com.amazon.ssm", "com.amazon.vizzini.ftvcds",
            "com.android.documentsui", "com.android.providers.downloads.ui");

    static final int BANNER_W = 480, BANNER_H = 270;

    final List<App> all = new ArrayList<>();
    final Map<String, App> byPkg = new HashMap<>();
    private final SharedPreferences prefs;

    Apps(Context ctx) {
        prefs = ctx.getSharedPreferences("nova", Context.MODE_PRIVATE);
        PackageManager pm = ctx.getPackageManager();
        List<ResolveInfo> found = new ArrayList<>();
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        found.addAll(pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (byPkg.containsKey(pkg) || HIDDEN.contains(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm)).trim();
            Intent li = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (li == null) li = pm.getLaunchIntentForPackage(pkg);
            if (li == null) continue;
            a.launch = li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            try {
                PackageInfo pi = pm.getPackageInfo(pkg, 0);
                a.version = pi.versionName != null && pi.versionName.matches("^\\d.*") ? pi.versionName.split("[ -]")[0] : null;
                a.installed = pi.firstInstallTime > 1420070400000L ? pi.firstInstallTime : 0; // ignore system-image dates
            } catch (PackageManager.NameNotFoundException ignored) { }
            a.banner = banner(pm, ri.activityInfo.applicationInfo, ri);
            a.accent = accent(a.banner);
            a.logo = logo(a.banner);
            a.round = round(pm, ri, a);
            a.world = worldOf(pkg, a.label);
            all.add(a);
            byPkg.put(pkg, a);
        }
        Collections.sort(all, new Comparator<App>() {
            public int compare(App x, App y) { return x.label.compareToIgnoreCase(y.label); }
        });
    }

    /** Real TV banner where the app ships one; otherwise its icon centred on a tinted plate. */
    private static Bitmap banner(PackageManager pm, ApplicationInfo ai, ResolveInfo ri) {
        Bitmap b = Bitmap.createBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Drawable d = ri.activityInfo.loadBanner(pm);
        if (d == null) d = pm.getApplicationBanner(ai);
        if (d != null) {
            d.setBounds(0, 0, BANNER_W, BANNER_H);
            d.draw(c);
            return b;
        }
        Drawable icon = ri.loadIcon(pm);
        c.drawColor(0xff20242c);
        int s = (int) (BANNER_H * 0.5f);
        icon.setBounds((BANNER_W - s) / 2, (BANNER_H - s) / 2, (BANNER_W + s) / 2, (BANNER_H + s) / 2);
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

    static final int ROUND = 256; // px

    /**
     * The app's own icon at the highest density it ships, cropped to a circle. If the icon is
     * small or transparent-edged (a mark on nothing), it sits on a disc of the app's colour
     * with its banner mark instead, so every icon is a full, rich circle.
     */
    static Bitmap round(PackageManager pm, ResolveInfo ri, App a) {
        Bitmap out = Bitmap.createBitmap(ROUND, ROUND, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG | android.graphics.Paint.FILTER_BITMAP_FLAG);
        android.graphics.Path circle = new android.graphics.Path();
        circle.addCircle(ROUND / 2f, ROUND / 2f, ROUND / 2f, android.graphics.Path.Direction.CW);
        c.clipPath(circle);
        Drawable icon = null;
        try {
            int res = ri.activityInfo.getIconResource();
            if (res == 0) res = ri.activityInfo.applicationInfo.icon;
            icon = pm.getResourcesForApplication(ri.activityInfo.applicationInfo).getDrawableForDensity(res, android.util.DisplayMetrics.DENSITY_XXXHIGH);
        } catch (Exception ignored) { }
        if (icon == null) icon = ri.loadIcon(pm);
        Bitmap ib = Bitmap.createBitmap(ROUND, ROUND, Bitmap.Config.ARGB_8888);
        icon.setBounds(0, 0, ROUND, ROUND);
        icon.draw(new Canvas(ib));
        // does the icon fill its square (an opaque tile) or float on transparency?
        int corners = 0;
        int[] probe = {ib.getPixel(ROUND / 8, ROUND / 2), ib.getPixel(ROUND / 2, ROUND / 8), ib.getPixel(ROUND * 7 / 8, ROUND / 2), ib.getPixel(ROUND / 2, ROUND * 7 / 8)};
        for (int px : probe) if ((px >>> 24) > 200) corners++;
        if (corners >= 3) {
            // opaque tile: find the tile's own edges (icons often ship with a margin) and fill the circle
            // with the inside of the tile, so no square corners show
            int[] px = new int[ROUND * ROUND];
            ib.getPixels(px, 0, ROUND, 0, 0, ROUND, ROUND);
            int x0 = ROUND, y0 = ROUND, x1 = 0, y1 = 0;
            for (int y = 0; y < ROUND; y++) for (int x = 0; x < ROUND; x++) if ((px[y * ROUND + x] >>> 24) > 200) {
                if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y;
            }
            float bw = Math.max(1, x1 - x0), bh = Math.max(1, y1 - y0);
            float sc = ROUND / Math.min(bw, bh) * 1.16f;  // past the tile's rounded corners
            float cx = (x0 + x1) / 2f, cy = (y0 + y1) / 2f;
            c.save();
            c.translate(ROUND / 2f, ROUND / 2f);
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
            p.setShader(new android.graphics.LinearGradient(0, 0, 0, ROUND, Color.HSVToColor(new float[]{hsv[0], hsv[1], 0.46f}), deep, android.graphics.Shader.TileMode.CLAMP));
            c.drawRect(0, 0, ROUND, ROUND, p);
            p.setShader(null);
            Bitmap mark = a.logo != null ? a.logo : ib;
            float s = Math.min(ROUND * 0.62f / mark.getWidth(), ROUND * 0.42f / mark.getHeight());
            if (mark == ib) s = 0.7f;
            float mw = mark.getWidth() * s, mh = mark.getHeight() * s;
            c.drawBitmap(mark, null, new android.graphics.RectF((ROUND - mw) / 2, (ROUND - mh) / 2, (ROUND + mw) / 2, (ROUND + mh) / 2), p);
        }
        ib.recycle();
        return out;
    }

    static final int WATCH = 0, LISTEN = 1, EXPLORE = 2;

    static int worldOf(String pkg, String label) {
        String k = (pkg + " " + label).toLowerCase(java.util.Locale.US);
        for (String m : new String[]{"spotify", "music", "gaana", "saavn", "wynk", "audible", "radio", "podcast", "soundcloud", "tunein"})
            if (k.contains(m)) return LISTEN;
        for (String v : new String[]{"netflix", "firebat", "prime", "hotstar", "youtube", "zee5", "sonyliv", "jio", "mxtech", "mx player",
                "lionsgate", "ullu", "fawesome", "imdb", "video", "cinema", "movies", "tv", "hulu", "disney", "plex", "kodi", "vlc"})
            if (k.contains(v)) return WATCH;
        return EXPLORE;
    }

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
        int e = 3;
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
        if (kept < 40 || kept > px.length * 0.5) return null; // not a clean mark
        Bitmap all = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
        int pad = 4;
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

    void toggleFavourite(App a) {
        List<App> f = new ArrayList<>(favourites());
        if (!f.remove(a)) f.add(a);
        StringBuilder sb = new StringBuilder();
        for (App x : f) { if (sb.length() > 0) sb.append(','); sb.append(x.pkg); }
        prefs.edit().putString("favs", sb.toString()).apply();
    }

    /** A newly installed app joins the orbit by itself (unless the family unpinned it before). */
    void autoPin(String pkg) {
        App a = byPkg.get(pkg);
        if (a == null || isFavourite(a) || prefs.getBoolean("unpinned:" + pkg, false)) return;
        toggleFavourite(a);
    }

    void markUnpinned(App a, boolean unpinned) { prefs.edit().putBoolean("unpinned:" + a.pkg, unpinned).apply(); }

    /** The app to offer first in a world: the last one opened there, else the first in it. */
    App lead(int world) {
        App best = null; long t = -1;
        for (App a : all) if (a.world == world && lastOpened(a) > t) { t = lastOpened(a); best = a; }
        if (t > 0) return best;
        for (App a : favourites()) if (a.world == world) return a;
        return best;
    }

    List<App> inWorld(int world) {
        List<App> out = new ArrayList<>();
        for (App a : favourites()) if (a.world == world) out.add(a);
        for (App a : all) if (a.world == world && !out.contains(a)) out.add(a);
        return out;
    }

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
