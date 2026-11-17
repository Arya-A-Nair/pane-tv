package tv.nova;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Home. The design language this file implements is written down in DESIGN.md — read that
 * first. In one line: a dark, lit environment; Porcelain type; glass only where information
 * is grouped; controls turn solid Porcelain under focus, objects lift and gain a ring.
 */
public class Nova extends Activity {
    // ---- colour (DESIGN.md "Colour")
    static final int OBSIDIAN = 0xff090b10, GRAPHITE = 0xff20242c, PORCELAIN = 0xffe9ecf2, MIST = 0xffced4df,
            GLACIER = 0xffa4b8ee, CHAMPAGNE = 0xffb4a28c, CHAMPAGNE_LIGHT = 0xffe0d2bf;
    static final int[] AIR = {0xff8fcba8, 0xffd9c58a, 0xffe0a66e, 0xffde8266, 0xffc97b9e, 0xffa97fb5}; // good .. hazardous, marks only
    // ---- space (dp): side margin, top and bottom safe lines
    static final float M = 56, TOP = 48, BOTTOM = 48, SCREEN_W = 960, SCREEN_H = 540;

    static final String[] PAGES = {"Home", "Apps", "Today", "Backgrounds", "Settings"};
    static final int HOME = 0, ALL = 1, TODAY = 2, BACKGROUNDS = 3, SETTINGS = 4;

    private int pageIx;
    private boolean onNav;           // focus is on the bottom navigation
    private int navSel;

    private Apps apps;
    private SharedPreferences prefs;
    private float dp;
    private Typeface manrope, inter, interM, mono;
    private final Handler ui = new Handler();

    private FrameLayout root, stage, ambient, launchLayer;
    private final FrameLayout[] pages = new FrameLayout[PAGES.length];
    private ImageView envView, backdrop;
    private Weather weather;

    private GlassPanel nav;
    private final List<NavItem> navItems = new ArrayList<>();

    // home
    private TextView hTime, hDate, heroName, heroLine, wTemp, wCond, wCity, wAir, wMoonName, wSun, aLine;
    private ImageView heroLogo, wMoon;
    private View wAirDot;
    private LinearLayout hero;
    private GlassPanel wWeather, wSky;
    private Clock aClock;
    private Apps.App recent, heroApp;
    private boolean heroShown;
    private final List<Tile> homeTiles = new ArrayList<>();
    private LinearLayout homeRow;
    private int hSel;
    private final float[] grade = {1, 1, 1, 0}, gradeTo = {1, 1, 1, 0};   // tint r,g,b (luma 1) and how much of it
    private ValueAnimator gradeAnim;

    // apps
    private FrameLayout allGrid;
    private final List<Tile> allTiles = new ArrayList<>();
    private int aSel;
    private TextView allAside;
    static final int COLS = 6;
    static final float ROW_H = 152;

    // today
    private TextView tAside, tCity, tTemp, tCond, tRange, tAir, tAirName, tAirNote, tSunValue, tSunNote, moonName, moonInfo;
    private View tAirDot;
    private Hourly hourly;
    private AirScale airScale;
    private ImageView moonView;
    private SunArc sunArc;

    // backgrounds, settings
    private final List<BgTile> bgTiles = new ArrayList<>();
    private int bSel;
    private TextView bgAside;
    private final List<SetRow> setRows = new ArrayList<>();
    private int sSel;

    private boolean idle, launching, entered;
    private long lastInput = SystemClock.uptimeMillis();
    // test hooks, set only by adb intent extras (see handleDebug)
    private static boolean dryRun;
    private static int fakeAqi = Integer.MIN_VALUE;
    private static boolean fakeNoWeather;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new ColorDrawable(OBSIDIAN));
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("nova", MODE_PRIVATE);
        Motion.scale = prefs.getBoolean("reducedMotion", false) ? 0.55f : 1f;
        manrope = Typeface.createFromAsset(getAssets(), "fonts/manrope-light.ttf");
        inter = Typeface.createFromAsset(getAssets(), "fonts/inter-regular.ttf");
        interM = Typeface.createFromAsset(getAssets(), "fonts/inter-medium.ttf");
        mono = Typeface.createFromAsset(getAssets(), "fonts/jbmono-regular.ttf");

        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        Env.load(this, Env.indexOf(prefs.getString("env", Env.IDS[0])), sw, sh);

        root = new FrameLayout(this);
        envView = new ImageView(this);
        envView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        envView.setImageBitmap(Env.photo);
        root.addView(envView, match());
        // Away from Home the environment falls out of focus and dims; glass keeps its light.
        backdrop = new ImageView(this);
        backdrop.setScaleType(ImageView.ScaleType.FIT_XY);
        backdrop.setImageBitmap(Env.frost);
        backdrop.setColorFilter(0xff8a8a8a, PorterDuff.Mode.MULTIPLY);
        backdrop.setAlpha(0f);
        root.addView(backdrop, match());
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, match());
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new FrameLayout(this);
            pages[i].setClipChildren(false);
            pages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            stage.addView(pages[i], match());
        }
        ambient = new FrameLayout(this);
        ambient.setAlpha(0f);
        root.addView(ambient, match());
        launchLayer = new FrameLayout(this);
        root.addView(launchLayer, match());
        setContentView(root);
        startService(new Intent(this, Watcher.class));

        apps = new Apps(this);
        buildHome();
        buildAll();
        buildToday();
        buildBackgrounds();
        buildSettings();
        buildNav();
        buildAmbient();

        weather = new Weather(this, w -> showWeather());
        showWeather();
        watchPackages();
        homeStart();
        handleDebug(getIntent());
        focus(false);
        tick.run();
    }

    // ================================================================== home

    private void buildHome() {
        FrameLayout p = pages[HOME];
        LinearLayout meta = new LinearLayout(this);
        meta.setGravity(Gravity.BOTTOM);
        hTime = text(manrope, 34, PORCELAIN);
        hDate = text(interM, 15, MIST);
        hDate.setPadding(px(14), 0, 0, px(4));
        shadow(hTime); shadow(hDate);
        meta.addView(hTime);
        meta.addView(hDate);
        place(p, meta, M - 1, TOP - 7);

        // the hero: whatever is focused in the row, named large, with one line of real context
        hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        FrameLayout mark = new FrameLayout(this);
        heroLogo = new ImageView(this);
        heroLogo.setScaleType(ImageView.ScaleType.FIT_START);
        mark.addView(heroLogo, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM));
        heroName = text(manrope, 72, PORCELAIN);
        heroName.setLetterSpacing(-0.02f);
        heroName.setShadowLayer(px(14), 0, px(2), 0x55000000);
        mark.addView(heroName, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM));
        hero.addView(mark, new LinearLayout.LayoutParams(-2, px(88)));
        heroLine = text(inter, 16, MIST);
        shadow(heroLine);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = px(14);
        hero.addView(heroLine, hl);
        place(p, hero, M - 3, 128);

        // what the family asks the TV most: the weather, the air, tonight's sky
        float ww = 280, x = SCREEN_W - M - ww;
        wWeather = panel(p, x, TOP, ww, 116);
        wTemp = text(manrope, 46, PORCELAIN);
        place(wWeather, wTemp, 22, 14);
        LinearLayout wl = new LinearLayout(this);
        wl.setOrientation(LinearLayout.VERTICAL);
        wCond = text(interM, 16, PORCELAIN);
        wCity = text(inter, 14, MIST);
        wCity.setPadding(0, px(3), 0, 0);
        wl.addView(wCond);
        wl.addView(wCity);
        place(wWeather, wl, 104, 22);
        LinearLayout al = new LinearLayout(this);
        al.setGravity(Gravity.CENTER_VERTICAL);
        wAirDot = dot(GLACIER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(9), px(9));
        dl.rightMargin = px(9);
        al.addView(wAirDot, dl);
        wAir = text(inter, 14, PORCELAIN);
        al.addView(wAir);
        place(wWeather, al, 24, 80);

        wSky = panel(p, x, TOP + 116 + 12, ww, 76);
        wMoon = new ImageView(this);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(44), px(44), Gravity.CENTER_VERTICAL);
        ml.leftMargin = px(20);
        wSky.addView(wMoon, ml);
        LinearLayout sl = new LinearLayout(this);
        sl.setOrientation(LinearLayout.VERTICAL);
        wMoonName = text(interM, 16, PORCELAIN);
        wSun = text(inter, 14, MIST);
        wSun.setPadding(0, px(3), 0, 0);
        sl.addView(wMoonName);
        sl.addView(wSun);
        FrameLayout.LayoutParams sll = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL);
        sll.leftMargin = px(80);
        wSky.addView(sl, sll);

        homeRow = new LinearLayout(this);
        homeRow.setClipChildren(false);
        place(p, homeRow, M - Tile.PAD, 300 - Tile.PAD);
        fillHome();
    }

    private View dot(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        View v = new View(this);
        v.setBackground(d);
        return v;
    }

    /** The Home row: the favourites, with the app last used put first if it is not one of them. */
    private void fillHome() {
        homeRow.removeAllViews();
        homeTiles.clear();
        List<Apps.App> fav = apps.favourites();
        List<Apps.App> row = new ArrayList<>(fav.subList(0, Math.min(Apps.HOME_SLOTS, fav.size())));
        List<Apps.App> r = apps.recents(1);
        recent = r.isEmpty() ? null : r.get(0);
        if (recent != null && !row.contains(recent)) { if (row.size() == Apps.HOME_SLOTS) row.remove(row.size() - 1); row.add(0, recent); }
        for (int i = 0; i <= row.size(); i++) {
            Tile t = new Tile(i < row.size() ? row.get(i) : null, 92, 121);
            homeRow.addView(t.v);
            homeTiles.add(t);
        }
        hSel = Math.min(hSel, homeTiles.size() - 1);
        heroShown = false;
    }

    /** Where focus rests when Home opens: on what was last watched. */
    private void homeStart() { hSel = Math.max(0, recent == null ? 0 : indexOfHome(recent)); }
    private int indexOfHome(Apps.App a) { for (int i = 0; i < homeTiles.size(); i++) if (homeTiles.get(i).app == a) return i; return -1; }

    /** Name the focused app large, say when it was last used, and re-light the room in its colour. */
    private void showHero(boolean animate) {
        final Apps.App a = homeTiles.get(hSel).app;
        boolean home = pageIx == HOME;
        // the light: the app's own colour, held back so it stays a mood, never a wash
        if (a == null || !home) gradeTo[3] = 0;
        else {
            float[] hsv = new float[3];
            android.graphics.Color.colorToHSV(a.accent, hsv);
            hsv[1] = Math.min(hsv[1], 0.75f); hsv[2] = 1f;
            int c = android.graphics.Color.HSVToColor(hsv);
            float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f, y = 0.299f * r + 0.587f * g + 0.114f * b;
            // halfway between "same brightness" (which turns red to pink) and the pure colour (which is deep)
            gradeTo[0] = (r / y + r) / 2; gradeTo[1] = (g / y + g) / 2; gradeTo[2] = (b / y + b) / 2;
            gradeTo[3] = Env.index >= 6 ? 0.3f : 0.66f;   // photographs keep most of their own colour
        }
        if (gradeAnim != null) gradeAnim.cancel();
        final float[] from = grade.clone();
        if (from[3] == 0) { from[0] = gradeTo[0]; from[1] = gradeTo[1]; from[2] = gradeTo[2]; }
        gradeAnim = ValueAnimator.ofFloat(0, 1).setDuration(animate ? Motion.ms(700) : 0);
        gradeAnim.setInterpolator(Motion.EASE);
        gradeAnim.addUpdateListener(x -> {
            float t = (Float) x.getAnimatedValue();
            for (int i = 0; i < 4; i++) grade[i] = from[i] + (gradeTo[i] - from[i]) * t;
            applyGrade();
        });
        gradeAnim.start();
        if (!home || (heroShown && heroApp == a)) return;
        heroShown = true;
        heroApp = a;
        Runnable fill = () -> {
            // The app's own mark, as large as the box allows but never enlarged past its own pixels.
            // Most TV banners are too small for that to be a hero, and then the name is set in type instead.
            float k = a == null || a.logo == null ? 0 : Math.min(Math.min(px(340) / (float) a.logo.getWidth(), px(88) / (float) a.logo.getHeight()), a.logoSharp / a.logo.getWidth());
            boolean logo = k > 0 && (a.logo.getWidth() * k >= px(220) || a.logo.getHeight() * k >= px(64));
            heroLogo.setVisibility(logo ? View.VISIBLE : View.GONE);
            heroName.setVisibility(logo ? View.GONE : View.VISIBLE);
            if (logo) {
                heroLogo.setImageBitmap(a.logo);
                heroLogo.setLayoutParams(new FrameLayout.LayoutParams(Math.round(a.logo.getWidth() * k), Math.round(a.logo.getHeight() * k), Gravity.BOTTOM));
            } else heroName.setText(a == null ? "All apps" : a.label);
            long last = a == null ? 0 : apps.lastOpened(a), now = System.currentTimeMillis();
            String when = now - last < 60_000 ? "Opened just now"
                    : "Opened " + DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS).toString().toLowerCase(Locale.getDefault());
            heroLine.setText(a == null ? apps.all.size() + " apps on this TV" : last <= 0 ? "Press OK to open" : (logo ? a.label + "  ·  " : "") + (a == recent ? "Continue  ·  " : "") + when);
        };
        hero.animate().cancel();
        if (!animate) { fill.run(); hero.setAlpha(1f); hero.setTranslationX(0); return; }
        hero.animate().alpha(0f).setDuration(Motion.ms(110)).withEndAction(() -> {
            fill.run();
            hero.setTranslationX(px(10));
            hero.animate().alpha(1f).translationX(0).setDuration(Motion.ms(380)).setInterpolator(Motion.GLIDE).start();
        }).start();
    }

    private void applyGrade() {
        float m = grade[3];
        android.graphics.ColorFilter f = null;
        if (m > 0.004f) {
            float[] k = new float[20];
            for (int c = 0; c < 3; c++) {
                k[c * 5] = m * grade[c] * 0.299f; k[c * 5 + 1] = m * grade[c] * 0.587f; k[c * 5 + 2] = m * grade[c] * 0.114f;
                k[c * 5 + c] += 1 - m;
            }
            k[18] = 1;
            f = new android.graphics.ColorMatrixColorFilter(k);
        }
        envView.setColorFilter(f);
        GlassPanel.grade = f;
        invalidateGlass(pages[HOME]);
        nav.invalidate();
    }

    /** The time, set large in Manrope Light. Drawn by hand so its top edge is the top of the digits. */
    private final class Clock extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Rect b = new Rect();
        String t = "";
        Clock(float sp) {
            super(Nova.this);
            p.setTypeface(manrope);
            p.setTextSize(sp * dp);
            p.setLetterSpacing(-0.02f);
            p.setShadowLayer(14 * dp, 0, 2 * dp, 0x55000000);
            p.getTextBounds("0", 0, 1, b);
        }
        void set(String s) { if (!s.equals(t)) { t = s; requestLayout(); invalidate(); } }
        @Override protected void onMeasure(int w, int h) { setMeasuredDimension((int) (p.measureText(t) + 12 * dp), b.height() + (int) (10 * dp)); }
        @Override protected void onDraw(Canvas c) {
            int colon = t.indexOf(':');
            float x = 4 * dp, y = b.height() + 2 * dp;
            if (colon < 0) { p.setColor(PORCELAIN); c.drawText(t, x, y, p); return; }
            String h = t.substring(0, colon), m = t.substring(colon + 1);
            p.setColor(PORCELAIN);
            c.drawText(h, x, y, p);
            x += p.measureText(h);
            p.setColor(CHAMPAGNE_LIGHT);                       // the one warm mark in the composition
            c.drawText(":", x, y - b.height() * 0.07f, p);     // lifted to sit centred on the digits
            x += p.measureText(":");
            p.setColor(PORCELAIN);
            c.drawText(m, x, y, p);
        }
    }

    // ================================================================== app tiles

    /** A round app icon with its name. Under focus it comes forward, gains a Porcelain ring, and its name brightens. */
    private final class Tile {
        static final float PAD = 10; // room round the disc for the ring (dp)
        final Apps.App app;
        final LinearLayout v = new LinearLayout(Nova.this);
        final View disc;
        final TextView label;
        boolean lit, pinned;
        Tile(Apps.App a, final float size, float cell) {
            app = a;
            v.setOrientation(LinearLayout.VERTICAL);
            v.setGravity(Gravity.CENTER_HORIZONTAL);
            v.setClipChildren(false);
            disc = new View(Nova.this) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                final RectF r = new RectF();
                @Override protected void onDraw(Canvas c) {
                    float pad = PAD * dp, s = getWidth() - 2 * pad, cx = getWidth() / 2f, rad = s / 2f;
                    p.setStyle(Paint.Style.FILL);
                    if (app != null) { r.set(pad, pad, pad + s, pad + s); p.setColor(0xffffffff); c.drawBitmap(app.round, null, r, p); }
                    else {   // "All apps": a quiet disc with a 2x2 grid
                        p.setColor(0x24e9ecf2); c.drawCircle(cx, cx, rad, p);
                        p.setColor(PORCELAIN);
                        float q = s * 0.105f, g = s * 0.07f;
                        for (int i = 0; i < 4; i++) { float x = cx - g / 2 - q + (i % 2) * (q + g), y = cx - g / 2 - q + (i / 2) * (q + g); c.drawRoundRect(x, y, x + q, y + q, q * 0.3f, q * 0.3f, p); }
                    }
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(Math.max(1.5f, dp * 0.75f));      // hairline that seats the icon on the dark
                    p.setColor(0x33ffffff);
                    c.drawCircle(cx, cx, rad - p.getStrokeWidth() / 2, p);
                    if (lit) {
                        p.setStrokeWidth(2.5f * dp);
                        p.setColor(PORCELAIN);
                        c.drawCircle(cx, cx, rad + 4.5f * dp, p);
                    }
                }
            };
            disc.setOutlineProvider(new ViewOutlineProvider() {
                public void getOutline(View vv, Outline o) { int pd = px(PAD); o.setOval(pd, pd, vv.getWidth() - pd, vv.getHeight() - pd); }
            });
            disc.setElevation(a == null ? 0 : px(6));
            v.addView(disc, new LinearLayout.LayoutParams(px(size + 2 * PAD), px(size + 2 * PAD)));
            label = text(interM, 14, MIST);
            shadow(label);
            label.setMaxLines(2);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setText(a == null ? "All apps" : a.label);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(px(cell - 6), -2);
            ll.topMargin = px(4);
            v.addView(label, ll);
            v.setLayoutParams(new LinearLayout.LayoutParams(px(cell), -2));
        }
        void set(boolean on, boolean animate) {
            lit = on;
            disc.invalidate();
            label.setTextColor(on ? PORCELAIN : MIST);
            disc.animate().scaleX(on ? 1.12f : 1f).scaleY(on ? 1.12f : 1f).translationZ(on && app != null ? px(14) : 0)
                    .setDuration(animate ? Motion.ms(300) : 0).setInterpolator(Motion.FOCUS).start();
            label.animate().translationY(on ? px(7) : 0).setDuration(animate ? Motion.ms(300) : 0).setInterpolator(Motion.FOCUS).start();
        }
    }

    // ================================================================== apps

    private void buildAll() {
        FrameLayout p = pages[ALL];
        allAside = header(p, "Apps");
        allGrid = new FrameLayout(this);   // rows outside the two on show are faded out, not clipped
        allGrid.setClipChildren(false);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, px(2000));
        gl.topMargin = px(116);
        p.addView(allGrid, gl);
        fillAll();
    }

    private void fillAll() {
        allGrid.removeAllViews();
        allTiles.clear();
        float cell = (SCREEN_W - 2 * M) / COLS;
        List<Apps.App> list = apps.ordered();
        for (int i = 0; i < list.size(); i++) {
            Tile t = new Tile(list.get(i), 80, cell);
            FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(cell), -2);
            l.leftMargin = px(M + (i % COLS) * cell);
            l.topMargin = px((i / COLS) * ROW_H - Tile.PAD);
            allGrid.addView(t.v, l);
            allTiles.add(t);
        }
        aSel = Math.min(aSel, Math.max(0, allTiles.size() - 1));
        markPinned();
    }

    private void markPinned() {
        List<Apps.App> fav = apps.favourites();
        for (Tile t : allTiles) {
            t.pinned = fav.indexOf(t.app) >= 0 && fav.indexOf(t.app) < Apps.HOME_SLOTS;
            t.label.setText(t.pinned ? mark(t.app.label) : t.app.label);
        }
    }

    // ================================================================== today

    private GlassPanel panel(FrameLayout p, float x, float y, float w, float h) {
        GlassPanel g = new GlassPanel(this, dp, 28);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(w), px(h));
        l.leftMargin = px(x);
        l.topMargin = px(y);
        p.addView(g, l);
        return g;
    }

    /** The shape every reading shares: a quiet label, one large Manrope figure, two lines beside it. */
    private TextView[] reading(GlassPanel g, String label, float figureSp) {
        TextView cap = text(inter, 14, MIST);
        cap.setText(label);
        place(g, cap, 24, 20);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView fig = text(manrope, figureSp, PORCELAIN);
        row.addView(fig);
        LinearLayout lines = new LinearLayout(this);
        lines.setOrientation(LinearLayout.VERTICAL);
        TextView a = text(interM, 16, PORCELAIN), b = text(inter, 14, MIST);
        b.setPadding(0, px(3), 0, 0);
        lines.addView(a);
        lines.addView(b);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = px(16);
        row.addView(lines, ll);
        place(g, row, 24, 46);
        return new TextView[]{cap, fig, a, b};
    }

    private void buildToday() {
        FrameLayout p = pages[TODAY];
        tAside = header(p, "Today");
        float gap = 16, W = SCREEN_W - 2 * M, y0 = 112, h1 = 152, h2 = 140, wAir = 320, wWx = W - wAir - gap, w2 = (W - gap) / 2;

        GlassPanel wx = panel(p, M, y0, wWx, h1);
        TextView[] r = reading(wx, "", 58);
        tCity = r[0]; tTemp = r[1]; tCond = r[2]; tRange = r[3];
        hourly = new Hourly();
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(px(250), px(h1 - 40), Gravity.END);
        hl.setMargins(0, px(20), px(24), 0);
        wx.addView(hourly, hl);

        GlassPanel air = panel(p, M + wWx + gap, y0, wAir, h1);
        r = reading(air, "Air quality", 58);
        tAir = r[1]; tAirName = r[2]; tAirNote = r[3];
        airScale = new AirScale();
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, px(5), Gravity.BOTTOM);
        al.setMargins(px(24), 0, px(24), px(22));
        air.addView(airScale, al);

        float y1 = y0 + h1 + gap;
        GlassPanel day = panel(p, M, y1, w2, h2);
        TextView dl = text(inter, 14, MIST);
        dl.setText("Daylight");
        place(day, dl, 24, 20);
        tSunValue = text(manrope, 30, PORCELAIN);
        place(day, tSunValue, 24, 50);
        tSunNote = text(inter, 14, MIST);
        place(day, tSunNote, 24, 96);
        sunArc = new SunArc(this, dp, interM);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(px(196), px(h2 - 40), Gravity.END);
        sl.setMargins(0, px(22), px(24), 0);
        day.addView(sunArc, sl);

        GlassPanel moon = panel(p, M + w2 + gap, y1, w2, h2);
        moonView = new ImageView(this);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(96), px(96), Gravity.CENTER_VERTICAL);
        ml.leftMargin = px(24);
        moon.addView(moonView, ml);
        TextView mlab = text(inter, 14, MIST);
        mlab.setText("Moon");
        place(moon, mlab, 144, 20);
        moonName = text(manrope, 30, PORCELAIN);
        place(moon, moonName, 144, 50);
        moonInfo = text(inter, 14, MIST);
        place(moon, moonInfo, 144, 96);
    }

    /** The next twelve hours as a line: six readings, each with its temperature above and its hour below. */
    private final class Hourly extends View {
        final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG), val = new Paint(Paint.ANTI_ALIAS_FLAG), hr = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Path path = new Path();
        float[] t; int start;
        Hourly() {
            super(Nova.this);
            line.setStyle(Paint.Style.STROKE); line.setStrokeWidth(2 * dp); line.setColor(0x8ce9ecf2); line.setStrokeCap(Paint.Cap.ROUND);
            dot.setColor(PORCELAIN);
            val.setTypeface(interM); val.setTextSize(15 * dp); val.setColor(PORCELAIN); val.setTextAlign(Paint.Align.CENTER);
            hr.setTypeface(inter); hr.setTextSize(13 * dp); hr.setColor(MIST); hr.setTextAlign(Paint.Align.CENTER);
        }
        void set(float[] temps, int startHour) { t = temps; start = startHour; invalidate(); }
        @Override protected void onDraw(Canvas c) {
            if (t == null) return;
            int n = 6;
            float w = getWidth(), h = getHeight(), lo = 1e9f, hi = -1e9f, col = w / n, top = 30 * dp, bot = h - 34 * dp;
            for (int i = 0; i < n; i++) { float v = t[Math.min(1 + 2 * i, t.length - 1)]; lo = Math.min(lo, v); hi = Math.max(hi, v); }
            float[] xs = new float[n], ys = new float[n];
            for (int i = 0; i < n; i++) {
                float v = t[Math.min(1 + 2 * i, t.length - 1)];
                xs[i] = col * (i + 0.5f);
                ys[i] = hi - lo < 0.5f ? (top + bot) / 2 : bot - (v - lo) / (hi - lo) * (bot - top);
            }
            path.reset();
            path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < n; i++) { float mx = (xs[i - 1] + xs[i]) / 2; path.cubicTo(mx, ys[i - 1], mx, ys[i], xs[i], ys[i]); }
            c.drawPath(path, line);
            for (int i = 0; i < n; i++) {
                c.drawCircle(xs[i], ys[i], 3.5f * dp, dot);
                c.drawText(Math.round(t[Math.min(1 + 2 * i, t.length - 1)]) + "°", xs[i] + 2 * dp, ys[i] - 11 * dp, val);
                c.drawText(String.format(Locale.US, "%02d:00", (start + 1 + 2 * i) % 24), xs[i], h - 3 * dp, hr);
            }
        }
    }

    /** Six steps from good to hazardous; the one the air is in now is lit. Colour is only ever a mark here, never text. */
    private final class AirScale extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        int level = -1;
        AirScale() { super(Nova.this); }
        @Override protected void onDraw(Canvas c) {
            float gap = 4 * dp, w = (getWidth() - 5 * gap) / 6f, h = getHeight();
            for (int i = 0; i < 6; i++) {
                p.setColor(i == level ? AIR[i] : 0x33e9ecf2);
                c.drawRoundRect(i * (w + gap), 0, i * (w + gap) + w, h, h / 2, h / 2, p);
            }
        }
    }

    static int airLevel(int aqi) { return aqi <= 50 ? 0 : aqi <= 100 ? 1 : aqi <= 150 ? 2 : aqi <= 200 ? 3 : aqi <= 300 ? 4 : 5; }
    static final String[] AIR_NAME = {"Good", "Moderate", "Poor", "Unhealthy", "Very unhealthy", "Hazardous"};
    static final String[] AIR_NOTE = {"A good day for open windows", "Fine for most people", "Hard on sensitive lungs", "Keep the windows shut", "Stay indoors if you can", "Stay indoors"};

    private void showWeather() {
        boolean ok = weather != null && weather.ok && !fakeNoWeather;
        int aqi = !ok ? -1 : fakeAqi != Integer.MIN_VALUE ? fakeAqi : weather.aqi;
        // Home
        wTemp.setText(ok ? Math.round(weather.temp) + "°" : "–");
        wCond.setText(ok ? weather.describe() : "No reading yet");
        wCity.setText(ok ? weather.city : "Waiting for the network");
        wAirDot.setVisibility(aqi < 0 ? View.GONE : View.VISIBLE);
        if (aqi >= 0) ((GradientDrawable) wAirDot.getBackground()).setColor(AIR[airLevel(aqi)]);
        wAir.setText(aqi < 0 ? "" : "Air " + aqi + "  ·  " + AIR_NAME[airLevel(aqi)]);
        summary = !ok ? "" : Math.round(weather.temp) + "°  " + weather.describe() + (aqi > 150 ? "  ·  " + AIR_NAME[airLevel(aqi)] + " air" : "");
        // Today
        tCity.setText(ok ? weather.city : "Weather");
        tTemp.setText(ok ? Math.round(weather.temp) + "°" : "–");
        tCond.setText(ok ? weather.describe() : "No reading yet");
        tRange.setText(ok ? "High " + Math.round(weather.hi) + "°  ·  Low " + Math.round(weather.lo) + "°" : "Waiting for the network");
        hourly.set(ok ? weather.hourly : null, ok ? weather.startHour : 0);
        tAir.setText(aqi < 0 ? "–" : String.valueOf(aqi));
        tAirName.setText(aqi < 0 ? "No reading yet" : AIR_NAME[airLevel(aqi)]);
        tAirNote.setText(aqi < 0 ? "" : AIR_NOTE[airLevel(aqi)]);
        airScale.level = aqi < 0 ? -1 : airLevel(aqi);
        airScale.invalidate();
        sunArc.set(ok ? weather.sunrise : "", ok ? weather.sunset : "");
        showSun();
        if (aLine != null) showAmbientLine();
    }

    private String summary = "";   // one line of weather, for the screensaver

    private void showSun() {
        String[] s = sunArc.summary();
        boolean night = s != null && s[1].startsWith("until");
        wSun.setText(weather == null || !weather.ok || fakeNoWeather ? "" : night ? "Sunrise " + weather.sunrise : "Sunset " + weather.sunset);
        tSunValue.setText(s == null ? "–" : s[0]);
        tSunNote.setText(s == null ? "No reading yet" : s[1]);
        sunArc.invalidate();
    }

    // ================================================================== backgrounds

    /** An environment to choose: the picture itself is the tile — no card around it. */
    private final class BgTile {
        final LinearLayout v = new LinearLayout(Nova.this);
        final FrameLayout frame = new FrameLayout(Nova.this);
        final View ring, dot;
        final TextView name;
        BgTile(int i, float w) {
            v.setOrientation(LinearLayout.VERTICAL);
            v.setClipChildren(false);
            frame.setClipChildren(false);
            ImageView th = new ImageView(Nova.this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setOutlineProvider(new ViewOutlineProvider() { public void getOutline(View x, Outline o) { o.setRoundRect(0, 0, x.getWidth(), x.getHeight(), px(12)); } });
            th.setClipToOutline(true);
            th.setElevation(px(6));
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 4;
            try { th.setImageBitmap(BitmapFactory.decodeStream(getAssets().open("env/" + Env.IDS[i] + ".jpg"), null, o)); } catch (Exception ignored) { }
            frame.addView(th, match());
            GradientDrawable rim = new GradientDrawable();
            rim.setCornerRadius(px(12));
            rim.setStroke(Math.max(1, px(0.75f)), 0x33ffffff);
            View edge = new View(Nova.this);
            edge.setBackground(rim);
            edge.setElevation(px(6));
            frame.addView(edge, match());
            GradientDrawable rd = new GradientDrawable();
            rd.setCornerRadius(px(17));
            rd.setStroke(px(2.5f), PORCELAIN);
            ring = new View(Nova.this);
            ring.setBackground(rd);
            ring.setAlpha(0f);
            ring.setElevation(px(6));
            FrameLayout.LayoutParams rl = match();
            rl.setMargins(-px(5), -px(5), -px(5), -px(5));
            frame.addView(ring, rl);
            v.addView(frame, new LinearLayout.LayoutParams(px(w), px(w * 9 / 16f)));
            LinearLayout cap = new LinearLayout(Nova.this);
            cap.setGravity(Gravity.CENTER_VERTICAL);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(GLACIER);
            dot = new View(Nova.this);
            dot.setBackground(d);
            LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(8), px(8));
            dl.rightMargin = px(8);
            cap.addView(dot, dl);
            name = text(interM, 14, MIST);
            name.setText(Env.NAMES[i]);
            cap.addView(name);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-2, -2);
            cl.topMargin = px(12);
            v.addView(cap, cl);
        }
        void set(boolean on, boolean current) {
            ring.animate().alpha(on ? 1f : 0f).setDuration(Motion.ms(160)).start();
            frame.animate().scaleX(on ? 1.07f : 1f).scaleY(on ? 1.07f : 1f).translationZ(on ? px(12) : 0).setDuration(Motion.ms(300)).setInterpolator(Motion.FOCUS).start();
            dot.setVisibility(current ? View.VISIBLE : View.GONE);
            name.setTextColor(on || current ? PORCELAIN : MIST);
            name.animate().translationY(on ? px(5) : 0).setDuration(Motion.ms(300)).setInterpolator(Motion.FOCUS).start();
        }
    }
    static final int BG_COLS = 5;

    private void buildBackgrounds() {
        FrameLayout p = pages[BACKGROUNDS];
        bgAside = header(p, "Backgrounds");
        float gap = 20, w = (SCREEN_W - 2 * M - (BG_COLS - 1) * gap) / BG_COLS, pitch = w * 9 / 16f + 62;
        for (int i = 0; i < Env.IDS.length; i++) {
            BgTile t = new BgTile(i, w);
            place(p, t.v, M + (i % BG_COLS) * (w + gap), 124 + (i / BG_COLS) * pitch);
            bgTiles.add(t);
        }
        bSel = Math.max(0, Env.index);
    }

    private void setBackground(final int i) {
        prefs.edit().putString("env", Env.IDS[i]).apply();
        final int sw = root.getWidth(), sh = root.getHeight();
        new Thread(() -> {
            Env.load(Nova.this, i, sw, sh);
            ui.post(() -> {
                envView.setImageBitmap(Env.photo);
                backdrop.setImageBitmap(Env.frost);
                stage.setAlpha(0.6f);
                stage.animate().alpha(1f).setDuration(Motion.ms(500)).start();
                invalidateGlass(root);
                focus(true);
            });
        }).start();
    }

    private void invalidateGlass(View v) {
        if (v instanceof GlassPanel) v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateGlass(((ViewGroup) v).getChildAt(i));
    }

    // ================================================================== settings

    interface Value { String get(); }

    /** One line of the settings list. Focus turns the whole line to Porcelain. */
    private final class SetRow {
        final LinearLayout v = new LinearLayout(Nova.this);
        final GradientDrawable bg = new GradientDrawable();
        final TextView label = text(interM, 16, PORCELAIN), value = text(inter, 15, MIST);
        final Value val; final Runnable action;
        SetRow(String l, Value val, Runnable action) {
            this.val = val; this.action = action;
            v.setGravity(Gravity.CENTER_VERTICAL);
            v.setPadding(px(20), 0, px(20), 0);
            bg.setCornerRadius(px(22));
            v.setBackground(bg);
            label.setText(l);
            v.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            v.addView(value);
        }
        void refresh(boolean on) {
            value.setText(val == null ? "Open" : val.get());
            bg.setColor(on ? PORCELAIN : 0);
            label.setTextColor(on ? OBSIDIAN : PORCELAIN);
            value.setTextColor(on ? GRAPHITE : MIST);
        }
    }

    private void buildSettings() {
        FrameLayout p = pages[SETTINGS];
        header(p, "Settings");
        final int[] mins = {1, 3, 5, 10, 20};
        setRows.add(new SetRow("Fire TV settings", null, () -> {
            String[] targets = {"com.amazon.tv.launcher/.ui.MainSettingsActivity", "com.amazon.tv.launcher/.util.QuickSettingsProxyActivity"};
            for (String t : targets) {
                try { startActivity(new Intent().setComponent(android.content.ComponentName.unflattenFromString(t)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                catch (Exception ignored) { }
            }
        }));
        setRows.add(new SetRow("Wi-Fi", null, settingsIntent("android.settings.PICK_WIFI_NETWORK")));
        setRows.add(new SetRow("Manage apps", null, settingsIntent("android.settings.APPLICATION_SETTINGS")));
        setRows.add(new SetRow("About this TV", null, settingsIntent("android.settings.DEVICE_INFO_SETTINGS")));
        setRows.add(new SetRow("Screensaver after", () -> { int m = prefs.getInt("ambientMin", 3); return m + (m == 1 ? " minute" : " minutes"); }, () -> {
            int cur = prefs.getInt("ambientMin", 3), n = mins[0];
            for (int i = 0; i < mins.length; i++) if (mins[i] == cur) n = mins[(i + 1) % mins.length];
            prefs.edit().putInt("ambientMin", n).apply();
        }));
        setRows.add(new SetRow("Motion", () -> Motion.scale < 1 ? "Reduced" : "Full", () -> {
            boolean r = Motion.scale == 1f;
            Motion.scale = r ? .55f : 1f;
            prefs.edit().putBoolean("reducedMotion", r).apply();
        }));

        float rowH = 50, pad = 6, w = 440;
        GlassPanel list = panel(p, M, 112, w, setRows.size() * rowH + 2 * pad);
        for (int i = 0; i < setRows.size(); i++) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, px(rowH - 6));
            lp.setMargins(px(pad), px(pad + 3 + i * rowH), px(pad), 0);
            list.addView(setRows.get(i).v, lp);
        }

        // This TV: the only place the system speaks in mono
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        TextView it = text(inter, 14, MIST);
        it.setText("This TV");
        info.addView(it);
        String ip = "–";
        try {
            int a = ((WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE)).getConnectionInfo().getIpAddress();
            if (a != 0) ip = (a & 255) + "." + ((a >> 8) & 255) + "." + ((a >> 16) & 255) + "." + ((a >> 24) & 255);
        } catch (Exception ignored) { }
        StatFs fs = new StatFs(Environment.getDataDirectory().getPath());
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
        String[][] rows = {{"Model", Build.MANUFACTURER + " " + Build.MODEL}, {"System", "Android " + Build.VERSION.RELEASE},
                {"Address", ip}, {"Storage", String.format(Locale.US, "%.1f GB free", fs.getAvailableBytes() / 1e9)},
                {"Memory", (mi.availMem >> 20) + " MB free"}, {"Apps", String.valueOf(apps.all.size())}};
        for (String[] r : rows) {
            LinearLayout line = new LinearLayout(this);
            TextView k = text(mono, 13, MIST), v = text(mono, 13, PORCELAIN);
            k.setText(r[0]);
            v.setText(r[1]);
            line.addView(k, new LinearLayout.LayoutParams(px(92), -2));
            line.addView(v);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = px(r == rows[0] ? 16 : 10);
            info.addView(line, ll);
        }
        place(p, info, M + w + 56, 112 + pad + 16);
    }

    private Runnable settingsIntent(final String action) {
        return () -> { try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { } };
    }

    /** Page title on the left, one line of context on the right; both share a baseline. Returns the context line. */
    private TextView header(FrameLayout p, String s) {
        TextView t = text(manrope, 40, PORCELAIN);
        t.setLetterSpacing(-0.02f);
        t.setText(s);
        place(p, t, M - 2, TOP - 9);
        TextView aside = text(inter, 15, MIST);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, Gravity.END);
        l.setMargins(0, px(TOP + 14), px(M), 0);
        p.addView(aside, l);
        return aside;
    }

    // ================================================================== navigation

    private final class NavItem {
        final TextView label = text(interM, 15, PORCELAIN);
        final GradientDrawable bg = new GradientDrawable();
        NavItem(String name) {
            label.setGravity(Gravity.CENTER);
            label.setPadding(px(20), 0, px(20), 0);
            bg.setCornerRadius(px(18));
            label.setBackground(bg);
            label.setText(name);
        }
        void state(boolean focused, boolean current) {
            bg.setColor(focused ? PORCELAIN : current ? 0x26e9ecf2 : 0);
            label.setTextColor(focused ? OBSIDIAN : current ? PORCELAIN : MIST);
        }
    }

    private void buildNav() {
        nav = new GlassPanel(this, dp, 22);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(px(4), px(4), px(4), px(4));
        for (String name : PAGES) {
            NavItem it = new NavItem(name);
            row.addView(it.label, new LinearLayout.LayoutParams(-2, px(36)));
            navItems.add(it);
        }
        nav.addView(row);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        l.bottomMargin = px(BOTTOM);
        stage.addView(nav, l);
    }

    // ================================================================== focus

    private void focus(boolean animate) {
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == pageIx ? View.VISIBLE : View.GONE);
        for (int i = 0; i < navItems.size(); i++) navItems.get(i).state(onNav && i == navSel, i == pageIx);
        boolean content = !onNav;

        for (int i = 0; i < homeTiles.size(); i++) homeTiles.get(i).set(content && pageIx == HOME && i == hSel, animate);
        showHero(animate);

        int row = aSel / COLS, top = Math.max(0, Math.min(row, (allTiles.size() - 1) / COLS - 1));
        for (int i = 0; i < allTiles.size(); i++) {
            allTiles.get(i).set(content && pageIx == ALL && i == aSel, animate);
            int r = i / COLS;
            allTiles.get(i).v.animate().alpha(r >= top && r <= top + 1 ? 1f : 0f).setDuration(animate ? Motion.ms(260) : 0).start();
        }
        allGrid.animate().translationY(-top * px(ROW_H)).setDuration(animate ? Motion.ms(420) : 0).setInterpolator(Motion.GLIDE).start();
        if (!allTiles.isEmpty()) allAside.setText(content && pageIx == ALL ? (allTiles.get(aSel).pinned ? "Menu  ·  Remove from Home" : "Menu  ·  Add to Home") : allTiles.size() + " apps");

        for (int i = 0; i < bgTiles.size(); i++) bgTiles.get(i).set(content && pageIx == BACKGROUNDS && i == bSel, i == Env.index);
        bgAside.setText(content && pageIx == BACKGROUNDS ? Env.CREDITS[bSel] + "  ·  OK to use" : Env.NAMES[Math.max(0, Env.index)]);
        for (int i = 0; i < setRows.size(); i++) setRows.get(i).refresh(content && pageIx == SETTINGS && i == sSel);
    }

    /** A name with a small Glacier dot before it: "this one is on Home". */
    private CharSequence mark(String name) {
        android.text.SpannableString s = new android.text.SpannableString("\u25cf  " + name);
        s.setSpan(new android.text.style.ForegroundColorSpan(GLACIER), 0, 1, 0);
        s.setSpan(new android.text.style.RelativeSizeSpan(0.6f), 0, 1, 0);
        return s;
    }

    private void show(int page) {
        if (page == pageIx) return;
        pageIx = page;
        if (page == ALL) fillAll();   // pick up favourites changed since last time
        FrameLayout p = pages[page];
        p.setAlpha(0f);
        p.setTranslationY(px(12));
        focus(false);
        showHero(true);
        backdrop.animate().alpha(page == HOME ? 0f : 1f).setDuration(Motion.ms(450)).setInterpolator(Motion.EASE).start();
        p.animate().alpha(1f).translationY(0).setDuration(Motion.ms(420)).setInterpolator(Motion.GLIDE)
                .setUpdateListener(a -> invalidateGlass(pages[pageIx])).start();
    }

    // ================================================================== input

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = SystemClock.uptimeMillis();
        if (idle) { if (e.getAction() == KeyEvent.ACTION_UP) wake(); return true; }
        if (launching) return true;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        boolean L = k == KeyEvent.KEYCODE_DPAD_LEFT, R = k == KeyEvent.KEYCODE_DPAD_RIGHT, U = k == KeyEvent.KEYCODE_DPAD_UP, D = k == KeyEvent.KEYCODE_DPAD_DOWN;
        if (k == KeyEvent.KEYCODE_MENU) {
            if (pageIx == ALL && !onNav && !allTiles.isEmpty()) { apps.toggleFavourite(allTiles.get(aSel).app); markPinned(); fillHome(); }
            else { onNav = false; navSel = SETTINGS; show(SETTINGS); }
            focus(true); return true;
        }
        if (k == KeyEvent.KEYCODE_BACK) {
            onNav = false; navSel = HOME; homeStart();
            show(HOME);
            focus(true); return true;
        }
        if (onNav) {
            if (L) navSel = Math.max(0, navSel - 1);
            else if (R) navSel = Math.min(PAGES.length - 1, navSel + 1);
            else if ((ok || U) && navSel != TODAY) onNav = false;   // Today is for reading: nothing in it to select
            show(navSel);   // pages follow the navigation, like tabs
            focus(true); return true;
        }
        switch (pageIx) {
            case HOME:
                if (L) hSel = Math.max(0, hSel - 1);
                else if (R) hSel = Math.min(homeTiles.size() - 1, hSel + 1);
                else if (D) { onNav = true; navSel = HOME; }
                else if (ok) { Tile t = homeTiles.get(hSel); if (t.app == null) { navSel = ALL; show(ALL); } else { launch(t.disc, t.app); return true; } }
                break;
            case ALL: {
                int n = allTiles.size();
                if (L) { if (aSel % COLS > 0) aSel--; }
                else if (R) { if (aSel % COLS < COLS - 1 && aSel + 1 < n) aSel++; }
                else if (U) { if (aSel >= COLS) aSel -= COLS; }
                else if (D) { if (aSel + COLS < n) aSel += COLS; else if (aSel / COLS < (n - 1) / COLS) aSel = n - 1; else { onNav = true; navSel = ALL; } }
                else if (ok && n > 0) { Tile t = allTiles.get(aSel); launch(t.disc, t.app); return true; }
                break;
            }
            case BACKGROUNDS: {
                int n = bgTiles.size();
                if (L) { if (bSel % BG_COLS > 0) bSel--; }
                else if (R) { if (bSel % BG_COLS < BG_COLS - 1 && bSel + 1 < n) bSel++; }
                else if (U) { if (bSel >= BG_COLS) bSel -= BG_COLS; }
                else if (D) { if (bSel + BG_COLS < n) bSel += BG_COLS; else { onNav = true; navSel = BACKGROUNDS; } }
                else if (ok) setBackground(bSel);
                break;
            }
            case SETTINGS:
                if (U) sSel = Math.max(0, sSel - 1);
                else if (D) { if (sSel < setRows.size() - 1) sSel++; else { onNav = true; navSel = SETTINGS; } }
                else if (ok) { if (!dryRun || setRows.get(sSel).val != null) setRows.get(sSel).action.run(); }
                break;
            default: onNav = true; navSel = pageIx;
        }
        focus(true);
        return true;
    }

    // ================================================================== launch

    /** The app's icon travels to the centre and its name appears, while the room goes dark around it. */
    private void launch(View from, final Apps.App a) {
        if (launching || a == null) return;
        launching = true;
        if (!dryRun) apps.opened(a);
        int[] loc = new int[2];
        from.getLocationInWindow(loc);
        float w = from.getWidth() * from.getScaleX(), h = from.getHeight() * from.getScaleY();
        { float pad = px(Tile.PAD) * from.getScaleX(); loc[0] += pad; loc[1] += pad; w -= 2 * pad; h -= 2 * pad; }
        View veil = new View(this);
        veil.setBackgroundColor(OBSIDIAN);
        veil.setAlpha(0f);
        launchLayer.addView(veil, match());
        ImageView img = new ImageView(this);
        img.setImageBitmap(a.round);
        launchLayer.addView(img, new FrameLayout.LayoutParams((int) w, (int) h));
        img.setX(loc[0]); img.setY(loc[1]);
        TextView name = text(manrope, 30, PORCELAIN);
        name.setText(a.label);
        name.setAlpha(0f);
        name.setTranslationY(px(8));
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL);
        nl.topMargin = px(SCREEN_H / 2 + 70);
        launchLayer.addView(name, nl);
        float sw = root.getWidth(), sh = root.getHeight(), s = px(112) / w;
        long d = Motion.ms(520);
        veil.animate().alpha(1f).setDuration(d).setInterpolator(Motion.EASE).start();
        stage.animate().alpha(0f).scaleX(0.97f).scaleY(0.97f).setDuration(Motion.ms(320)).start();
        name.animate().alpha(1f).translationY(0).setStartDelay(Motion.ms(180)).setDuration(Motion.ms(360)).setInterpolator(Motion.GLIDE).start();
        img.animate().x(sw / 2 - w / 2).y(sh / 2 - h / 2 - px(16)).scaleX(s).scaleY(s).setDuration(d).setInterpolator(Motion.GLIDE).start();
        ui.postDelayed(() -> {
            if (dryRun) { ui.postDelayed(this::comeBack, 1200); return; }
            try { startActivity(a.launch); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); } catch (Exception ex) { comeBack(); }
        }, d + Motion.ms(260));
    }

    private void comeBack() {
        launchLayer.removeAllViews();
        boolean back = launching;
        launching = false;
        stage.setAlpha(0f);
        stage.setScaleX(1.02f);
        stage.setScaleY(1.02f);
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(600)).setInterpolator(Motion.GLIDE).start();
        if (back) { fillHome(); homeStart(); }
        focus(false);
        ui.postDelayed(() -> invalidateGlass(root), Motion.ms(650));
    }

    // ================================================================== ambient

    /** After a while with no input, everything but the time steps aside and the environment is the picture. */
    private void buildAmbient() {
        aClock = new Clock(132);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        cl.setMargins(px(M - 6), 0, 0, px(BOTTOM + 34));
        ambient.addView(aClock, cl);
        aLine = text(inter, 16, PORCELAIN);
        shadow(aLine);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        ll.setMargins(px(M), 0, 0, px(BOTTOM));
        ambient.addView(aLine, ll);
    }

    private void showAmbientLine() {
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(System.currentTimeMillis());
        aLine.setText(d + (summary.length() > 0 ? "   ·   " + summary : ""));
    }

    private void sleep() {
        idle = true;
        showAmbientLine();
        stage.animate().alpha(0f).setDuration(1400).start();
        backdrop.animate().alpha(0f).setDuration(1400).start();
        ambient.animate().alpha(1f).setStartDelay(900).setDuration(1600).start();
        envView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180000).setInterpolator(new android.view.animation.LinearInterpolator()).start();
    }

    private void wake() {
        idle = false;
        envView.animate().cancel();
        ambient.animate().cancel();
        ambient.animate().alpha(0f).setStartDelay(0).setDuration(Motion.ms(300)).start();
        envView.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE).start();
        backdrop.animate().alpha(pageIx == HOME ? 0f : 1f).setDuration(Motion.ms(600)).start();
        stage.animate().alpha(1f).setDuration(Motion.ms(600)).start();
        ui.postDelayed(() -> invalidateGlass(root), Motion.ms(950));
    }

    // ================================================================== lifecycle

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        lastInput = SystemClock.uptimeMillis();
        if (idle) wake();
        if (!entered) { entered = true; enter(); } else comeBack();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        onNav = false;
        navSel = HOME;
        if (!i.hasExtra("page")) homeStart();
        show(HOME);
        handleDebug(i);
        focus(false);
    }

    private void enter() {
        View[] seq = {(View) hDate.getParent(), hero, wWeather, wSky, homeRow, nav};
        for (int i = 0; i < seq.length; i++) {
            final View v = seq[i];
            v.setAlpha(0f);
            v.setTranslationY(px(14));
            v.animate().alpha(1f).translationY(0).setStartDelay(Motion.ms(150 + i * 80)).setDuration(Motion.ms(800)).setInterpolator(Motion.GLIDE)
                    .setUpdateListener(a -> { if (v instanceof GlassPanel) v.invalidate(); })
                    .withEndAction(() -> { v.animate().setStartDelay(0); v.invalidate(); }).start();
        }
    }

    // ================================================================== time

    private final Runnable tick = new Runnable() {
        public void run() {
            Calendar c = Calendar.getInstance();
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.getTime());
            String d = new SimpleDateFormat("EEEE d MMMM", Locale.getDefault()).format(c.getTime());
            hTime.setText(t); aClock.set(t);
            hDate.setText(d);
            tAside.setText(d + "  ·  " + t);
            long now = System.currentTimeMillis();
            if (c.get(Calendar.SECOND) == 0 || moonView.getDrawable() == null) {
                double age = Earth.moonAge(now);
                moonView.setImageBitmap(Earth.moon(Nova.this, px(96), age));
                wMoon.setImageBitmap(Earth.moon(Nova.this, px(44), age));
                moonName.setText(Earth.moonName(age));
                wMoonName.setText(Earth.moonName(age));
                double toFull = ((14.765 - age) % 29.530588853 + 29.530588853) % 29.530588853;
                moonInfo.setText(Math.round(Earth.moonLit(age) * 100) + "% lit  ·  Full on " + new SimpleDateFormat("d MMMM", Locale.getDefault()).format(now + (long) (toFull * 86400000L)));
                showSun();
                if (idle) showAmbientLine();
            }
            if (!idle && !launching && hasWindowFocus() && SystemClock.uptimeMillis() - lastInput > prefs.getInt("ambientMin", 3) * 60_000L) sleep();
            ui.postDelayed(this, 1000 - now % 1000 + 5);
        }
    };

    private void watchPackages() {
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                boolean replacing = i.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                String pkg = i.getData() == null ? null : i.getData().getSchemeSpecificPart();
                apps = new Apps(Nova.this);
                if (Intent.ACTION_PACKAGE_ADDED.equals(i.getAction()) && !replacing && pkg != null) apps.autoPin(pkg);
                fillHome();
                fillAll();
                focus(false);
            }
        }, pf);
    }

    // ================================================================== test hooks (adb only)

    /**
     * am start -n tv.nova/.Nova --ez dry true      launches play their animation but open nothing
     *                           --ei aqi 40         pretend air reading      --ei now 600  pretend time of day (minutes) for the arc
     *                           --ez noweather true pretend there is no reading
     *                           --es env tide       use an environment          --ei page 2   show a page, focus on the navigation
     *                           --ez ambient true   go to the screensaver now
     *                           --ez audit true     log any text that does not fit and any view outside the safe area
     */
    private void handleDebug(Intent i) {
        if (i == null) return;
        if (i.hasExtra("dry")) dryRun = i.getBooleanExtra("dry", false);
        if (i.hasExtra("aqi")) fakeAqi = i.getIntExtra("aqi", 0);
        if (i.hasExtra("now")) SunArc.nowOverride = i.getIntExtra("now", -1);
        if (i.hasExtra("noweather")) fakeNoWeather = i.getBooleanExtra("noweather", false);
        if (i.hasExtra("aqi") || i.hasExtra("now") || i.hasExtra("noweather")) showWeather();
        if (i.hasExtra("env")) setBackground(Env.indexOf(i.getStringExtra("env")));
        if (i.hasExtra("page")) { navSel = i.getIntExtra("page", 0); onNav = true; show(navSel); if (i.hasExtra("sel")) { onNav = false; hSel = Math.min(homeTiles.size() - 1, i.getIntExtra("sel", 0)); } }
        if (i.getBooleanExtra("ambient", false)) ui.postDelayed(this::sleep, 600);
        if (i.getBooleanExtra("audit", false)) ui.postDelayed(() -> { for (FrameLayout p : pages) { p.setVisibility(View.VISIBLE); } root.post(() -> { audit(stage); focus(false); }); }, 800);
    }

    private void audit(View v) {
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float sx = 1, sy = 1;
        for (View q = v; q != null && q != root; q = q.getParent() instanceof View ? (View) q.getParent() : null) { sx *= q.getScaleX(); sy *= q.getScaleY(); }
        float l = loc[0], t = loc[1], r = l + v.getWidth() * sx, b = t + v.getHeight() * sy;
        boolean leaf = !(v instanceof ViewGroup) || v instanceof GlassPanel;
        if (leaf && v.getWidth() > 0 && v.isShown() && v.getAlpha() > 0 && (l < px(48) - 1 || t < px(48) - 1 || r > px(SCREEN_W - 48) + 1 || b > px(SCREEN_H - 48) + 1))
            Log.w("AUDIT", "outside safe area: " + describe(v) + " [" + l / dp + "," + t / dp + " - " + r / dp + "," + b / dp + "]");
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            if (tv.getLayout() != null) {
                boolean cut = tv.getLayout().getLineCount() > 0 && tv.getLayout().getEllipsisCount(tv.getLayout().getLineCount() - 1) > 0;
                float need = 0;
                for (String line : tv.getText().toString().split("\n")) need = Math.max(need, tv.getPaint().measureText(line));
                if (cut || tv.getLayout().getHeight() > tv.getHeight() + 1 || (tv.getMaxLines() == 1 && need > tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight() + 1))
                    Log.w("AUDIT", "text does not fit: " + describe(v));
            }
            if (tv.getTextSize() < 13 * dp - 0.5f) Log.w("AUDIT", "text below 13sp: " + describe(v));
        }
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) audit(((ViewGroup) v).getChildAt(i));
    }

    private static String describe(View v) { return v.getClass().getSimpleName() + (v instanceof TextView ? " \"" + ((TextView) v).getText() + "\"" : ""); }

    // ================================================================== helpers

    private TextView text(Typeface tf, float sp, int color) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (tf == mono) t.setLetterSpacing(0.01f);
        return t;
    }

    /** Text standing directly on the environment gets a wide, soft shadow: an insurance, not an effect. */
    private void shadow(TextView t) { t.setShadowLayer(px(8), 0, px(1), 0x73000000); }

    private void place(ViewGroup parent, View v, float leftDp, float topDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(leftDp);
        lp.topMargin = px(topDp);
        parent.addView(v, lp);
    }

    private static FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }

    int px(float d) { return Math.round(d * dp); }
}
