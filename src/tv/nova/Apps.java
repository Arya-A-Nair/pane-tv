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
