package tv.nova;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
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
 * Home, in the visionOS manner: a colour (or photographic) environment, real frosted glass,
 * a calm home with only your main apps, everything else one page away, and a glass ornament
 * along the bottom to move between pages.
 */
public class Nova extends Activity {
    static final int WHITE = 0xffffffff, WARN = 0xffffc2a1;
    static final String[] PAGES = {"Home", "All apps", "Today", "Backgrounds", "Settings"};
    static final int HOME = 0, ALL = 1, TODAY = 2, BACKGROUNDS = 3, SETTINGS = 4;

    private int pageIx;              // page shown
    private boolean onOrnament;      // focus is on the bottom ornament
    private int ornSel;

    private Apps apps;
    private SharedPreferences prefs;
    private float dp;
    private Typeface display, displayLight, inter, interM;
    private final Handler ui = new Handler();

    private FrameLayout root, stage, launchLayer;
    private final FrameLayout[] pages = new FrameLayout[PAGES.length];
    private ImageView envView;
    private Weather weather;

    // ornament
    private GlassPanel ornament;
    private final List<OrnItem> ornItems = new ArrayList<>();

    // home
    private TextView hDate, hTime, pillTemp, pillLine;
    private GlassPanel pill;
    private final List<Icon> homeIcons = new ArrayList<>();
    private LinearLayout homeRow;
    private int hSel;

    // all apps
    private FrameLayout allGrid;
    private final List<Icon> allIcons = new ArrayList<>();
    private int aSel;
    static final int COLS = 6;

    // today
    private final List<GlassPanel> cards = new ArrayList<>();
    private int tSel;
    private TextView tTime, tDate, tCity, tTemp, tCond, tAir, tAirNote, moonName, moonInfo, contName, contWhen;
    private LinearLayout tHourly;
    private ImageView moonView, contIcon;
    private SunArc sunArc;
    private Apps.App contApp;

    // backgrounds, settings
    private final List<GlassPanel> bgCards = new ArrayList<>();
    private int bSel;
    private final List<SetRow> setRows = new ArrayList<>();
    private int sSel;

    private boolean idle, launching, entered;
    private long lastInput = SystemClock.uptimeMillis();

    static final float M = 56; // outer margin (dp)

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff000000));
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("nova", MODE_PRIVATE);
        Motion.scale = prefs.getBoolean("reducedMotion", false) ? 0.55f : 1f;
        display = Typeface.createFromAsset(getAssets(), "fonts/inter-display-semibold.ttf");
        displayLight = Typeface.createFromAsset(getAssets(), "fonts/inter-display.ttf");
        inter = Typeface.createFromAsset(getAssets(), "fonts/inter-regular.ttf");
        interM = Typeface.createFromAsset(getAssets(), "fonts/inter-medium.ttf");

        int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
        Env.load(this, Math.min(prefs.getInt("bg", 0), Env.FILES.length - 1), sw, sh);

        root = new FrameLayout(this);
        envView = new ImageView(this);
        envView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        envView.setImageBitmap(Env.photo);
        root.addView(envView, match());
        stage = new FrameLayout(this);
        stage.setClipChildren(false);
        root.addView(stage, match());
        for (int i = 0; i < pages.length; i++) {
            pages[i] = new FrameLayout(this);
            pages[i].setClipChildren(false);
            pages[i].setVisibility(i == 0 ? View.VISIBLE : View.GONE);
            stage.addView(pages[i], match());
        }
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
        buildOrnament();

        weather = new Weather(this, new Weather.Listener() { public void onWeather(Weather w) { showWeather(); } });
        showWeather();
        watchPackages();
        refreshContinue();
        focus(false);
        tick.run();
    }

    // ================================================================== home

    private void buildHome() {
        FrameLayout p = pages[HOME];
        hDate = text(interM, 17, WHITE, 0.92f);
        hDate.setShadowLayer(px(10), 0, px(1), 0x40000000);
        place(p, hDate, M, 44);
        hTime = text(display, 84, WHITE, 1f);
        hTime.setLetterSpacing(-0.045f);
        hTime.setShadowLayer(px(18), 0, px(2), 0x33000000);
        place(p, hTime, M - 4, 64);

        pill = new GlassPanel(this, dp, 24);
        LinearLayout pl = new LinearLayout(this);
        pl.setGravity(Gravity.CENTER_VERTICAL);
        pl.setPadding(px(18), px(10), px(22), px(11));
        View sun = new View(this) {
            final Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                float cx = getWidth() / 2f, cy = getHeight() / 2f, r = getWidth() * 0.18f;
                q.setColor(0xffffd27a); q.setStyle(Paint.Style.FILL);
                c.drawCircle(cx, cy, r, q);
                q.setStyle(Paint.Style.STROKE); q.setStrokeWidth(dp * 1.4f); q.setStrokeCap(Paint.Cap.ROUND);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; c.drawLine(cx + (float) Math.cos(a) * r * 1.6f, cy + (float) Math.sin(a) * r * 1.6f, cx + (float) Math.cos(a) * r * 2.3f, cy + (float) Math.sin(a) * r * 2.3f, q); }
            }
        };
        pl.addView(sun, new LinearLayout.LayoutParams(px(26), px(26)));
        LinearLayout pt = new LinearLayout(this);
        pt.setOrientation(LinearLayout.VERTICAL);
        pillTemp = text(interM, 15, WHITE, 1f);
        pillLine = text(inter, 12, WHITE, 0.78f);
        pt.addView(pillTemp);
        pt.addView(pillLine);
        LinearLayout.LayoutParams ptl = new LinearLayout.LayoutParams(-2, -2);
        ptl.leftMargin = px(12);
        pl.addView(pt, ptl);
        pill.addView(pl);
        FrameLayout.LayoutParams pll = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        pll.setMargins(0, px(48), px(M), 0);
        p.addView(pill, pll);

        homeRow = new LinearLayout(this);
        homeRow.setClipChildren(false);
        FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        rl.topMargin = px(262);
        p.addView(homeRow, rl);
        fillHome();
    }

    private void fillHome() {
        homeRow.removeAllViews();
        homeIcons.clear();
        List<Apps.App> fav = apps.favourites();
        int n = Math.min(6, fav.size());
        for (int i = 0; i <= n; i++) {
            Icon ic = i < n ? new Icon(fav.get(i), 100, 15) : new Icon(null, 100, 15);
            LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(px(124), px(150));
            homeRow.addView(ic.v, l);
            homeIcons.add(ic);
        }
        hSel = Math.min(hSel, homeIcons.size() - 1);
    }

    // ================================================================== icons

    /** A round icon with its name, lifting toward you when focused. app == null draws the "All apps" glass button. */
    private final class Icon {
        final Apps.App app;
        final FrameLayout v = new FrameLayout(Nova.this);
        final View disc;
        final TextView label;
        float lit;
        Icon(final Apps.App a, float size, float labelSp) {
            app = a;
            disc = new View(Nova.this) {
                final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
                @Override protected void onDraw(Canvas c) {
                    float s = getWidth(), r = s / 2f;
                    if (app != null) c.drawBitmap(app.round, null, new android.graphics.RectF(0, 0, s, s), p);
                    else {
                        p.setColor(0x38ffffff); c.drawCircle(r, r, r, p);
                        p.setColor(WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(s * 0.03f);
                        float g = s * 0.1f, q = s * 0.13f;
                        for (int i = 0; i < 4; i++) { float x = r - g / 2 - q + (i % 2) * (q + g), y = r - g / 2 - q + (i / 2) * (q + g); c.drawRoundRect(x, y, x + q, y + q, q * 0.3f, q * 0.3f, p); }
                        p.setStyle(Paint.Style.FILL);
                    }
                    sheen.setShader(new LinearGradient(0, 0, 0, s, new int[]{(int) (50 + 40 * lit) << 24 | 0xffffff, 0x00ffffff, 0x00ffffff}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                    c.drawCircle(r, r, r, sheen);
                    rim.setStyle(Paint.Style.STROKE);
                    rim.setStrokeWidth(Math.max(1f, dp * (0.8f + 1.4f * lit)));
                    rim.setColor(((int) (60 + 195 * lit) << 24) | 0xffffff);
                    c.drawCircle(r, r, r - rim.getStrokeWidth() / 2, rim);
                }
            };
            disc.setOutlineProvider(new android.view.ViewOutlineProvider() {
                public void getOutline(View vv, android.graphics.Outline o) { o.setOval(0, 0, vv.getWidth(), vv.getHeight()); }
            });
            disc.setElevation(a == null ? 0 : px(8)); // a translucent disc would show its own shadow through it
            v.setClipChildren(false);
            v.addView(disc, new FrameLayout.LayoutParams(px(size), px(size), Gravity.TOP | Gravity.CENTER_HORIZONTAL));
            label = text(interM, labelSp, WHITE, 0.9f);
            label.setShadowLayer(px(8), 0, px(1), 0x80000000);
            label.setMaxLines(2);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setText(a == null ? "All apps" : a.label);
            FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            ll.topMargin = px(size + 12);
            ll.leftMargin = ll.rightMargin = px(4);
            v.addView(label, ll);
        }
        void set(boolean on, boolean animate) {
            lit = on ? 1f : 0f;
            disc.invalidate();
            disc.animate().scaleX(on ? 1.15f : 1f).scaleY(on ? 1.15f : 1f).translationY(on ? -px(5) : 0).translationZ(on && app != null ? px(16) : 0)
                    .setDuration(animate ? Motion.ms(320) : 0).setInterpolator(Motion.FOCUS).start();
            label.animate().alpha(on ? 1f : 0.85f).translationY(on ? px(7) : 0).setDuration(animate ? Motion.ms(320) : 0).start();
        }
    }

    // ================================================================== all apps

    private void buildAll() {
        FrameLayout p = pages[ALL];
        title(p, "All apps");
        FrameLayout viewport = new FrameLayout(this);
        FrameLayout.LayoutParams vl = new FrameLayout.LayoutParams(-1, px(330));
        vl.topMargin = px(104);
        p.addView(viewport, vl);
        allGrid = new FrameLayout(this);
        allGrid.setClipChildren(false);
        FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, -2);
        gl.topMargin = px(16);
        viewport.addView(allGrid, gl);
        fillAll();
    }

    private void fillAll() {
        allGrid.removeAllViews();
        allIcons.clear();
        float cell = (960 - 2 * M) / COLS;
        for (int i = 0; i < apps.all.size(); i++) {
            Icon ic = new Icon(apps.all.get(i), 78, 12.5f);
            FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(cell), px(150));
            l.leftMargin = px(M + (i % COLS) * cell);
            l.topMargin = px((i / COLS) * 150);
            allGrid.addView(ic.v, l);
            allIcons.add(ic);
        }
        aSel = Math.min(aSel, Math.max(0, allIcons.size() - 1));
    }

    // ================================================================== today

    private GlassPanel card(FrameLayout p, float x, float y, float w, float h) {
        GlassPanel g = new GlassPanel(this, dp, 28);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(w), px(h));
        l.leftMargin = px(x);
        l.topMargin = px(y);
        p.addView(g, l);
        cards.add(g);
        return g;
    }

    private void buildToday() {
        FrameLayout p = pages[TODAY];
        title(p, "Today");
        float x0 = M, gap = 16, W = 960 - 2 * M, y0 = 100, h1 = 176, h2 = 156;
        float wClock = 300, wWx = W - wClock - gap;

        GlassPanel clock = card(p, x0, y0, wClock, h1);
        LinearLayout c = col(clock, 24, 20);
        tDate = text(interM, 15, WHITE, 0.9f);
        c.addView(tDate);
        tTime = text(display, 64, WHITE, 1f);
        tTime.setLetterSpacing(-0.04f);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.topMargin = px(14);
        c.addView(tTime, tl);

        GlassPanel wx = card(p, x0 + wClock + gap, y0, wWx, h1);
        LinearLayout w = col(wx, 24, 20);
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.BOTTOM);
        LinearLayout tt = new LinearLayout(this);
        tt.setOrientation(LinearLayout.VERTICAL);
        tCity = text(interM, 15, WHITE, 0.9f);
        tt.addView(tCity);
        LinearLayout tr = new LinearLayout(this);
        tr.setGravity(Gravity.BOTTOM);
        tTemp = text(displayLight, 52, WHITE, 1f);
        tCond = text(inter, 14, WHITE, 0.85f);
        tCond.setPadding(px(12), 0, 0, px(9));
        tr.addView(tTemp);
        tr.addView(tCond);
        tt.addView(tr);
        top.addView(tt, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout air = new LinearLayout(this);
        air.setOrientation(LinearLayout.VERTICAL);
        air.setGravity(Gravity.END);
        tAir = text(display, 26, WARN, 1f);
        tAirNote = text(inter, 12, WHITE, 0.75f);
        tAirNote.setGravity(Gravity.END);
        air.addView(tAir);
        air.addView(tAirNote);
        top.addView(air);
        w.addView(top);
        tHourly = new LinearLayout(this);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-1, -2);
        hl.topMargin = px(14);
        w.addView(tHourly, hl);

        float y1 = y0 + h1 + gap, w3 = (W - 2 * gap) / 3f;
        GlassPanel moon = card(p, x0, y1, w3, h2);
        LinearLayout m = col(moon, 24, 20);
        moonView = new ImageView(this);
        m.addView(moonView, new LinearLayout.LayoutParams(px(58), px(58)));
        moonName = text(interM, 15, WHITE, 1f);
        LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(-2, -2);
        ml.topMargin = px(12);
        m.addView(moonName, ml);
        moonInfo = text(inter, 12.5f, WHITE, 0.72f);
        m.addView(moonInfo);

        GlassPanel day = card(p, x0 + w3 + gap, y1, w3, h2);
        LinearLayout d = col(day, 20, 18);
        sunArc = new SunArc(this, dp, displayLight, inter);
        d.addView(sunArc, new LinearLayout.LayoutParams(-1, -1));

        GlassPanel cont = card(p, x0 + 2 * (w3 + gap), y1, w3, h2);
        LinearLayout k = col(cont, 24, 20);
        LinearLayout kr = new LinearLayout(this);
        kr.setGravity(Gravity.CENTER_VERTICAL);
        contIcon = new ImageView(this);
        kr.addView(contIcon, new LinearLayout.LayoutParams(px(58), px(58)));
        LinearLayout kt = new LinearLayout(this);
        kt.setOrientation(LinearLayout.VERTICAL);
        TextView ct = text(inter, 12.5f, WHITE, 0.72f);
        ct.setText("Continue");
        contName = text(interM, 16, WHITE, 1f);
        contName.setSingleLine(true);
        contName.setEllipsize(TextUtils.TruncateAt.END);
        kt.addView(ct);
        kt.addView(contName);
        LinearLayout.LayoutParams ktl = new LinearLayout.LayoutParams(-2, -2);
        ktl.leftMargin = px(14);
        kr.addView(kt, ktl);
        k.addView(kr);
        contWhen = text(inter, 12.5f, WHITE, 0.72f);
        LinearLayout.LayoutParams cwl = new LinearLayout.LayoutParams(-2, -2);
        cwl.topMargin = px(16);
        k.addView(contWhen, cwl);
    }

    private LinearLayout col(GlassPanel g, float padH, float padV) {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(px(padH), px(padV), px(padH), px(padV));
        g.addView(l, match());
        return l;
    }

    private void showWeather() {
        if (!weather.ok) return;
        String cond = weather.describe();
        pillTemp.setText(Math.round(weather.temp) + "°  " + weather.city);
        String line = cond;
        if (weather.aqi > 150) line += "  ·  Air is unhealthy";
        else if (!weather.sunset.isEmpty()) line += "  ·  Sunset " + weather.sunset;
        pillLine.setText(line);
        tCity.setText(weather.city);
        tTemp.setText(Math.round(weather.temp) + "°");
        tCond.setText(cond);
        tAir.setText(weather.aqi < 0 ? "" : "AQI " + weather.aqi);
        tAir.setTextColor(weather.aqi > 100 ? WARN : WHITE);
        tAirNote.setText(weather.aqi > 150 ? "Unhealthy. Keep windows shut." : weather.aqi > 100 ? "Unhealthy for some." : weather.aqi >= 0 ? "Air is fine." : "");
        tHourly.removeAllViews();
        for (int i = 1; i <= 11; i += 2) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView hh = text(inter, 11.5f, WHITE, 0.62f);
            hh.setText(String.format(Locale.US, "%02d:00", (weather.startHour + i) % 24));
            TextView tv = text(interM, 14, WHITE, 1f);
            tv.setText(Math.round(weather.hourly[Math.min(i, weather.hourly.length - 1)]) + "°");
            cell.addView(hh);
            cell.addView(tv);
            tHourly.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
        }
        sunArc.set(weather.sunrise, weather.sunset);
    }

    private void refreshContinue() {
        List<Apps.App> r = apps.recents(1);
        contApp = r.isEmpty() ? apps.lead(Apps.WATCH) : r.get(0);
        if (contApp == null) return;
        contIcon.setImageBitmap(contApp.round);
        contName.setText(contApp.label);
        long last = apps.lastOpened(contApp);
        contWhen.setText(last <= 0 ? "Press OK to start" : System.currentTimeMillis() - last < 60_000 ? "Opened just now"
                : "Opened " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS));
    }

    // ================================================================== backgrounds

    private void buildBackgrounds() {
        FrameLayout p = pages[BACKGROUNDS];
        title(p, "Backgrounds");
        int per = 5;
        float gap = 14, w = (960 - 2 * M - (per - 1) * gap) / per, h = w * 9 / 16f + 46;
        for (int i = 0; i < Env.FILES.length; i++) {
            GlassPanel g = new GlassPanel(this, dp, 20);
            LinearLayout c = col(g, 8, 8);
            ImageView th = new ImageView(this);
            th.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.setOutlineProvider(new android.view.ViewOutlineProvider() {
                public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(13)); }
            });
            th.setClipToOutline(true);
            try { th.setImageBitmap(android.graphics.BitmapFactory.decodeStream(getAssets().open(Env.FILES[i]), null, sampled(8))); } catch (Exception ignored) { }
            c.addView(th, new LinearLayout.LayoutParams(-1, px((w - 16) * 9 / 16f)));
            TextView n = text(interM, 13, WHITE, 1f);
            n.setText(Env.NAMES[i]);
            n.setPadding(px(6), px(8), 0, 0);
            c.addView(n);
            FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(w), px(h));
            l.leftMargin = px(M + (i % per) * (w + gap));
            l.topMargin = px(104 + (i / per) * (h + gap));
            p.addView(g, l);
            bgCards.add(g);
        }
    }

    private static android.graphics.BitmapFactory.Options sampled(int n) { android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options(); o.inSampleSize = n; return o; }

    private void setBackground(final int i) {
        prefs.edit().putInt("bg", i).apply();
        final int sw = root.getWidth(), sh = root.getHeight();
        new Thread(new Runnable() { public void run() {
            Env.load(Nova.this, i, sw, sh);
            ui.post(new Runnable() { public void run() {
                envView.setAlpha(0.4f);
                envView.setImageBitmap(Env.photo);
                envView.animate().alpha(1f).setDuration(Motion.ms(600)).start();
                invalidateGlass(root);
                focus(true);
            } });
        } }).start();
    }

    private void invalidateGlass(View v) {
        if (v instanceof GlassPanel) v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) invalidateGlass(((ViewGroup) v).getChildAt(i));
    }

    // ================================================================== settings

    private final class SetRow {
        final GlassPanel v = new GlassPanel(Nova.this, dp, 22);
        final TextView label = text(interM, 15.5f, WHITE, 1f), value = text(inter, 14, WHITE, 0.72f);
        final Value val; final Runnable action;
        SetRow(String l, Value val, Runnable action) {
            this.val = val; this.action = action;
            LinearLayout row = new LinearLayout(Nova.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(22), 0, px(22), 0);
            label.setText(l);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(value);
            v.addView(row, match());
        }
        void refresh(boolean on) { value.setText(val == null ? "" : val.get()); v.setLit(on ? 1f : 0f); }
    }

    private void buildSettings() {
        title(pages[SETTINGS], "Settings");
        addSet("Fire TV settings", null, new Runnable() { public void run() {
            String[] targets = {"com.amazon.tv.launcher/.ui.MainSettingsActivity", "com.amazon.tv.launcher/.util.QuickSettingsProxyActivity"};
            for (String t : targets) {
                try { startActivity(new Intent().setComponent(android.content.ComponentName.unflattenFromString(t)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                catch (Exception ignored) { }
            }
        } });
        addSet("Ambient after", new Value() { public String get() { int m = prefs.getInt("ambientMin", 3); return m + (m == 1 ? " minute" : " minutes"); } },
                new Runnable() { public void run() { int[] o = {1, 3, 5, 10, 20}; int cur = prefs.getInt("ambientMin", 3), n = o[0];
                    for (int i = 0; i < o.length; i++) if (o[i] == cur) n = o[(i + 1) % o.length]; prefs.edit().putInt("ambientMin", n).apply(); } });
        addSet("Motion", new Value() { public String get() { return Motion.scale < 1 ? "Reduced" : "Full"; } },
                new Runnable() { public void run() { boolean r = Motion.scale == 1f; Motion.scale = r ? .55f : 1f; prefs.edit().putBoolean("reducedMotion", r).apply(); } });
        addSet("Wi-Fi", null, settingsIntent("android.settings.PICK_WIFI_NETWORK"));
        addSet("Manage apps", null, settingsIntent("android.settings.APPLICATION_SETTINGS"));
        addSet("About this TV", null, settingsIntent("android.settings.DEVICE_INFO_SETTINGS"));
    }

    private void addSet(String l, Value v, Runnable r) {
        SetRow row = new SetRow(l, v, r);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(440), px(46));
        lp.leftMargin = px(M);
        lp.topMargin = px(104 + setRows.size() * 54);
        pages[SETTINGS].addView(row.v, lp);
        setRows.add(row);
    }

    private Runnable settingsIntent(final String action) {
        return new Runnable() { public void run() {
            try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); } catch (Exception ignored) { }
        } };
    }

    interface Value { String get(); }

    private void title(FrameLayout p, String s) {
        TextView t = text(display, 34, WHITE, 1f);
        t.setLetterSpacing(-0.035f);
        t.setShadowLayer(px(10), 0, px(1), 0x40000000);
        t.setText(s);
        place(p, t, M, 40);
    }

    // ================================================================== ornament

    private final class OrnItem {
        final LinearLayout v = new LinearLayout(Nova.this);
        final TextView label;
        final android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        OrnItem(String name) {
            v.setGravity(Gravity.CENTER);
            v.setPadding(px(18), 0, px(18), 0);
            bg.setCornerRadius(px(19));
            v.setBackground(bg);
            label = text(interM, 13.5f, WHITE, 1f);
            label.setText(name);
            v.addView(label);
        }
        void state(boolean focused, boolean current) {
            bg.setColor(focused ? WHITE : current ? 0x2effffff : 0x00000000);
            label.setTextColor(focused ? 0xff15161a : WHITE);
            label.setAlpha(focused || current ? 1f : 0.78f);
            v.animate().scaleX(focused ? 1.06f : 1f).scaleY(focused ? 1.06f : 1f).setDuration(Motion.ms(220)).setInterpolator(Motion.FOCUS).start();
        }
    }

    private void buildOrnament() {
        ornament = new GlassPanel(this, dp, 25);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(px(6), px(6), px(6), px(6));
        for (String name : PAGES) {
            OrnItem it = new OrnItem(name);
            row.addView(it.v, new LinearLayout.LayoutParams(-2, px(38)));
            ornItems.add(it);
        }
        ornament.addView(row);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        l.bottomMargin = px(30);
        stage.addView(ornament, l);
    }

    // ================================================================== focus

    private void focus(boolean animate) {
        for (int i = 0; i < pages.length; i++) pages[i].setVisibility(i == pageIx ? View.VISIBLE : View.GONE);
        for (int i = 0; i < ornItems.size(); i++) ornItems.get(i).state(onOrnament && i == ornSel, i == pageIx);
        boolean content = !onOrnament;
        for (int i = 0; i < homeIcons.size(); i++) homeIcons.get(i).set(content && pageIx == HOME && i == hSel, animate);
        int row = aSel / COLS, top = Math.max(0, row - 1);
        for (int i = 0; i < allIcons.size(); i++) {
            allIcons.get(i).set(content && pageIx == ALL && i == aSel, animate);
            int r = i / COLS;
            allIcons.get(i).v.animate().alpha(r >= top && r <= top + 1 ? 1f : 0f).setDuration(animate ? Motion.ms(320) : 0).start();
        }
        allGrid.animate().translationY(-top * px(150)).setDuration(animate ? Motion.ms(420) : 0).setInterpolator(Motion.GLIDE).start();
        for (int i = 0; i < cards.size(); i++) {
            boolean on = content && pageIx == TODAY && i == tSel;
            lift(cards.get(i), on, 1.03f);
        }
        for (int i = 0; i < bgCards.size(); i++) {
            boolean on = content && pageIx == BACKGROUNDS && i == bSel;
            lift(bgCards.get(i), on, 1.06f);
            if (!on && i == Env.index) bgCards.get(i).setLit(0.4f);
        }
        for (int i = 0; i < setRows.size(); i++) setRows.get(i).refresh(content && pageIx == SETTINGS && i == sSel);
    }

    private void lift(final GlassPanel g, boolean on, float s) {
        g.setLit(on ? 1f : 0f);
        g.animate().scaleX(on ? s : 1f).scaleY(on ? s : 1f).setDuration(Motion.ms(280)).setInterpolator(Motion.FOCUS)
                .setUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { g.invalidate(); } }).start();
    }

    private void show(int page) {
        if (page == pageIx) return;
        pageIx = page;
        FrameLayout p = pages[page];
        p.setAlpha(0f);
        p.setTranslationY(px(14));
        focus(false);
        p.animate().alpha(1f).translationY(0).setDuration(Motion.ms(420)).setInterpolator(Motion.GLIDE)
                .setUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { invalidateGlass(pages[pageIx]); } }).start();
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
        if (k == KeyEvent.KEYCODE_MENU) { onOrnament = false; ornSel = SETTINGS; show(SETTINGS); focus(true); return true; }
        if (k == KeyEvent.KEYCODE_BACK) {
            if (pageIx != HOME || onOrnament) { onOrnament = false; ornSel = HOME; show(HOME); }
            else hSel = 0;
            focus(true); return true;
        }
        if (onOrnament) {
            if (L) ornSel = Math.max(0, ornSel - 1);
            else if (R) ornSel = Math.min(PAGES.length - 1, ornSel + 1);
            else if (ok || U) { show(ornSel); onOrnament = false; }
            if (L || R) show(ornSel); // pages follow the ornament, like tabs
            focus(true); return true;
        }
        switch (pageIx) {
            case HOME:
                if (L) hSel = Math.max(0, hSel - 1);
                else if (R) hSel = Math.min(homeIcons.size() - 1, hSel + 1);
                else if (D) { onOrnament = true; ornSel = HOME; }
                else if (ok) { Icon ic = homeIcons.get(hSel); if (ic.app == null) { show(ALL); } else { launch(ic.disc, ic.app); return true; } }
                break;
            case ALL: {
                int n = allIcons.size();
                if (L) { if (aSel % COLS > 0) aSel--; }
                else if (R) { if (aSel % COLS < COLS - 1 && aSel + 1 < n) aSel++; }
                else if (U) { if (aSel >= COLS) aSel -= COLS; }
                else if (D) { if (aSel + COLS < n) aSel += COLS; else if (aSel / COLS < (n - 1) / COLS) aSel = n - 1; else { onOrnament = true; ornSel = ALL; } }
                else if (ok && n > 0) { Icon ic = allIcons.get(aSel); launch(ic.disc, ic.app); return true; }
                else if (k == KeyEvent.KEYCODE_MENU) { apps.toggleFavourite(allIcons.get(aSel).app); fillHome(); }
                break;
            }
            case TODAY:
                // two rows: [clock, weather] then [moon, daylight, continue]
                if (L) tSel = tSel == 0 || tSel == 2 ? tSel : tSel - 1;
                else if (R) tSel = tSel == 1 || tSel == 4 ? tSel : tSel + 1;
                else if (U) tSel = tSel >= 2 ? (tSel == 2 ? 0 : 1) : tSel;
                else if (D) { if (tSel < 2) tSel = tSel == 0 ? 2 : 3; else { onOrnament = true; ornSel = TODAY; } }
                else if (ok) { if (tSel == 4 && contApp != null) { launch(cards.get(4), contApp); return true; } if (tSel == 0) { sleep(); return true; } }
                break;
            case BACKGROUNDS: {
                int n = bgCards.size(), per = 5;
                if (L) { if (bSel % per > 0) bSel--; }
                else if (R) { if (bSel % per < per - 1 && bSel + 1 < n) bSel++; }
                else if (U) { if (bSel >= per) bSel -= per; }
                else if (D) { if (bSel + per < n) bSel += per; else { onOrnament = true; ornSel = BACKGROUNDS; } }
                else if (ok) setBackground(bSel);
                break;
            }
            case SETTINGS:
                if (U) sSel = Math.max(0, sSel - 1);
                else if (D) { if (sSel < setRows.size() - 1) sSel++; else { onOrnament = true; ornSel = SETTINGS; } }
                else if (ok || R) setRows.get(sSel).action.run();
                break;
        }
        focus(true);
        return true;
    }

    // ================================================================== launch

    private void launch(View from, final Apps.App a) {
        if (launching || a == null) return;
        launching = true;
        apps.opened(a);
        int[] loc = new int[2];
        from.getLocationInWindow(loc);
        float w = from.getWidth() * from.getScaleX(), h = from.getHeight() * from.getScaleY();
        ImageView img = new ImageView(this);
        img.setImageBitmap(a.round);
        launchLayer.addView(img, new FrameLayout.LayoutParams((int) w, (int) h));
        img.setX(loc[0]); img.setY(loc[1]);
        float sw = root.getWidth(), sh = root.getHeight(), s = 2.6f * Math.max(sw, sh) / Math.max(w, h);
        long d = Motion.ms(560);
        stage.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(d).start();
        img.animate().x(sw / 2 - w / 2).y(sh / 2 - h / 2).scaleX(s).scaleY(s).alpha(0f)
                .setDuration(d).setInterpolator(Motion.EASE).setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator an) {
                        try { startActivity(a.launch); overridePendingTransition(0, 0); } catch (Exception ex) { comeBack(); }
                    }
                }).start();
    }

    private void comeBack() {
        launchLayer.removeAllViews();
        launching = false;
        stage.setAlpha(0f);
        stage.setScaleX(1.03f);
        stage.setScaleY(1.03f);
        stage.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(650)).setInterpolator(Motion.GLIDE).start();
        refreshContinue();
        focus(false);
        ui.postDelayed(new Runnable() { public void run() { invalidateGlass(root); } }, Motion.ms(700));
    }

    // ================================================================== idle

    private void sleep() {
        idle = true;
        stage.animate().alpha(0f).setDuration(1600).start();
        envView.animate().scaleX(1.08f).scaleY(1.08f).setDuration(120000).setInterpolator(Motion.EASE).start();
    }

    private void wake() {
        idle = false;
        envView.animate().cancel();
        envView.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE).start();
        stage.animate().alpha(1f).setDuration(Motion.ms(700)).start();
        ui.postDelayed(new Runnable() { public void run() { invalidateGlass(root); } }, Motion.ms(950));
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
        onOrnament = false;
        show(HOME);
        focus(false);
    }

    private void enter() {
        View[] seq = {hDate, hTime, pill, homeRow, ornament};
        for (int i = 0; i < seq.length; i++) {
            final View v = seq[i];
            v.setAlpha(0f);
            v.setTranslationY(px(16));
            v.animate().alpha(v.getTag() instanceof Float ? (Float) v.getTag() : 1f).translationY(0).setStartDelay(Motion.ms(200 + i * 90)).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE)
                    .setUpdateListener(new android.animation.ValueAnimator.AnimatorUpdateListener() { public void onAnimationUpdate(android.animation.ValueAnimator a) { if (v instanceof GlassPanel) v.invalidate(); } })
                    .withEndAction(new Runnable() { public void run() { v.animate().setStartDelay(0); v.invalidate(); } }).start();
        }
    }

    // ================================================================== time

    private final Runnable tick = new Runnable() {
        public void run() {
            Calendar c = Calendar.getInstance();
            String t = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.getTime());
            String d = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(c.getTime());
            hTime.setText(t); tTime.setText(t);
            hDate.setText(d); tDate.setText(d);
            long now = System.currentTimeMillis();
            if (c.get(Calendar.SECOND) == 0 || moonView.getDrawable() == null) {
                double age = Earth.moonAge(now);
                moonView.setImageBitmap(Earth.moon(Nova.this, px(58), age));
                moonName.setText(Earth.moonName(age));
                double toFull = ((14.765 - age) % 29.530588853 + 29.530588853) % 29.530588853;
                moonInfo.setText(Math.round(Earth.moonLit(age) * 100) + "% lit  ·  full on " + new SimpleDateFormat("d MMMM", Locale.getDefault()).format(now + (long) (toFull * 86400000L)));
                sunArc.invalidate();
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
                refreshContinue();
                focus(false);
            }
        }, pf);
    }

    // ================================================================== helpers

    private TextView text(Typeface tf, float sp, int color, float alpha) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setAlpha(alpha);
        t.setTag(alpha);
        t.setIncludeFontPadding(false);
        return t;
    }

    private void place(ViewGroup parent, View v, float leftDp, float topDp) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2);
        lp.leftMargin = px(leftDp);
        lp.topMargin = px(topDp);
        parent.addView(v, lp);
    }

    private static FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }

    int px(float d) { return Math.round(d * dp); }
}
