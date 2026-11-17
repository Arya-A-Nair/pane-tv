package tv.nova;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
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
 * NOVA. The home is a planet: the real Earth, lit by the sun as it is right now, rising from
 * the bottom of the screen. Apps are satellites on one orbit; left and right turn it, the one
 * nearest you comes forward, OK opens it. Down opens every app; Menu opens settings.
 * Two moves and one button, so anyone in the house can use it.
 */
public class Nova extends Activity {
    static final int OBSIDIAN = 0xff090b10, PORCELAIN = 0xffe9ecf2, GLACIER = 0xffa4b8ee, CHAMPAGNE = 0xffb4a28c, WARN = 0xffe0a27f;

    private enum Mode { ORBIT, DRAWER, CONTROLS, AMBIENT }
    private Mode mode = Mode.ORBIT;

    private Apps apps;
    private SharedPreferences prefs;
    private float dp;
    private Typeface manropeXL, manropeL, inter, interM;
    private final Handler ui = new Handler();

    private FrameLayout root, sky, launchLayer;
    private Earth earth;
    private ImageView planet;
    private Weather weather;
    private Leader leader;
    private TextView time, date, moonName, moonNext, wCity, wTemp, wSky, wLine, wAir, hint, focusName, focusMeta;
    private ImageView moonView;
    private View pin;

    // orbit
    private final List<Card> sats = new ArrayList<>();
    private FrameLayout orbit;
    private float angle;          // current orbit position, in item units (continuous)
    private int focus;            // item nearest the viewer
    private ValueAnimator turn;

    private LinearLayout playing;
    private ImageView playArt;
    private TextView playTitle, playArtist, playHint;
    private Media.Now now;
    private Drawer drawer;
    private Controls controls;
    private long lastInput = SystemClock.uptimeMillis();
    private boolean launching, entered;

    // geometry, in dp on a 960x540 screen
    static final float PLANET = 900, PLANET_CX = 480, PLANET_CY = 712;   // disc size and centre
    static final float ORBIT_CX = 480, ORBIT_CY = 196, ORBIT_RX = 370, ORBIT_RY = 34;
    static final float SAT_W = 204, SAT_H = 114.75f;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        dp = getResources().getDisplayMetrics().density;
        prefs = getSharedPreferences("nova", MODE_PRIVATE);
        Motion.scale = prefs.getBoolean("reducedMotion", false) ? 0.55f : 1f;
        manropeXL = Typeface.createFromAsset(getAssets(), "fonts/manrope-extralight.ttf");
        manropeL = Typeface.createFromAsset(getAssets(), "fonts/manrope-light.ttf");
        inter = Typeface.createFromAsset(getAssets(), "fonts/inter-regular.ttf");
        interM = Typeface.createFromAsset(getAssets(), "fonts/inter-medium.ttf");

        root = new FrameLayout(this);
        root.setBackgroundColor(OBSIDIAN);
        root.setClipChildren(false);
        sky = new FrameLayout(this);
        sky.setClipChildren(false);
        root.addView(sky, match());
        launchLayer = new FrameLayout(this);
        root.addView(launchLayer, match());
        setContentView(root);
        startService(new Intent(this, Watcher.class));

        apps = new Apps(this);
        buildPlanet();
        buildSky();
        buildOrbit();
        buildPlaying();
        drawer = new Drawer();
        controls = new Controls();
        weather = new Weather(this, new Weather.Listener() { public void onWeather(Weather w) { showWeather(); } });
        showWeather();
        tick.run();
    }

    // ================================================================== planet

    private void buildPlanet() {
        planet = new ImageView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(PLANET), px(PLANET));
        lp.leftMargin = px(PLANET_CX - PLANET / 2);
        lp.topMargin = px(PLANET_CY - PLANET / 2);
        sky.addView(planet, lp);
        pin = new View(this) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas c) {
                p.setColor(CHAMPAGNE);
                p.setStyle(Paint.Style.FILL);
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, 3.2f * dp, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(dp * 0.8f);
                p.setAlpha(150);
                c.drawCircle(getWidth() / 2f, getHeight() / 2f, 7 * dp, p);
            }
        };
        sky.addView(pin, new FrameLayout.LayoutParams(px(18), px(18)));
        leader = new Leader();
        sky.addView(leader, match());
        // the planet turns, very slightly, all the time
        ObjectAnimator spin = ObjectAnimator.ofFloat(planet, "rotation", -0.8f, 0.8f);
        spin.setDuration(40000);
        spin.setRepeatMode(ValueAnimator.REVERSE);
        spin.setRepeatCount(ValueAnimator.INFINITE);
        spin.start();
        new Thread(new Runnable() { public void run() {
            earth = new Earth(Nova.this);
            relight.run();
        } }, "nova-earth").start();
    }

    /** Re-render the lit Earth every ten minutes so day and night move across it as they really do. */
    private final Runnable relight = new Runnable() {
        public void run() {
            if (earth == null) return;
            final int size = px(PLANET);
            new Thread(new Runnable() { public void run() {
                final Bitmap b = earth.render(size, System.currentTimeMillis());
                ui.post(new Runnable() { public void run() {
                    boolean first = planet.getDrawable() == null;
                    planet.setImageBitmap(b);
                    if (first) {
                        planet.setAlpha(0f);
                        planet.setTranslationY(px(60));
                        planet.animate().alpha(1f).translationY(0).setDuration(2200).setInterpolator(Motion.GLIDE).start();
                    }
                    placePin();
                } });
            } }, "nova-light").start();
            ui.postDelayed(this, 10 * 60_000L);
        }
    };

    private void placePin() {
        float[] at = Earth.locate(weather.lat, weather.lon, px(PLANET));
        if (at == null) { pin.setVisibility(View.GONE); return; }
        pin.setVisibility(View.VISIBLE);
        pin.setX(px(PLANET_CX - PLANET / 2) + at[0] - pin.getWidth() / 2f);
        pin.setY(px(PLANET_CY - PLANET / 2) + at[1] - pin.getHeight() / 2f);
        leader.invalidate();
    }

    /** A single hairline from the city on the globe up to its weather in the sky. */
    private final class Leader extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        Leader() { super(Nova.this); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, dp * 0.6f)); p.setColor(0x55e9ecf2); }
        @Override protected void onDraw(Canvas c) {
            if (pin.getVisibility() != View.VISIBLE || wCity.getWidth() == 0) return;
            float x0 = pin.getX() + pin.getWidth() / 2f, y0 = pin.getY() + pin.getHeight() / 2f;
            float x1 = wCity.getX() - px(14), y1 = wCity.getY() + wCity.getHeight() / 2f;
            float elbow = x1 - px(40);
            c.drawLine(x0, y0, elbow, y1, p);
            c.drawLine(elbow, y1, x1 - px(4), y1, p);
        }
    }

    // ================================================================== sky: time, moon, weather

    private void buildSky() {
        time = text(manropeXL, 60, PORCELAIN, 1f);
        time.setIncludeFontPadding(false);
        time.setLetterSpacing(-0.02f);
        place(sky, time, 46, 26);
        date = text(inter, 13, PORCELAIN, 0.62f);
        place(sky, date, 49, 96);

        moonView = new ImageView(this);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(px(42), px(42));
        ml.leftMargin = px(392);
        ml.topMargin = px(34);
        sky.addView(moonView, ml);
        moonName = text(inter, 11.5f, PORCELAIN, 0.85f);
        place(sky, moonName, 446, 37);
        moonNext = text(inter, 10.5f, PORCELAIN, 0.5f);
        place(sky, moonNext, 446, 54);

        TextView mark = text(manropeL, 11, PORCELAIN, 0.7f);
        mark.setText("NOVA");
        mark.setLetterSpacing(0.6f);
        FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        kl.setMargins(0, px(42), px(46), 0);
        sky.addView(mark, kl);

        // weather lives on the right, wired to the city on the globe
        wCity = text(inter, 11.5f, PORCELAIN, 0.6f);
        place(sky, wCity, 706, 330);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        wTemp = text(manropeXL, 44, PORCELAIN, 1f);
        wTemp.setIncludeFontPadding(false);
        wSky = text(inter, 13, PORCELAIN, 0.9f);
        wSky.setPadding(px(10), 0, 0, px(6));
        row.addView(wTemp);
        row.addView(wSky);
        place(sky, row, 706, 346);
        wLine = text(inter, 11, PORCELAIN, 0.58f);
        wLine.setMaxWidth(px(230));
        wLine.setLineSpacing(px(2), 1f);
        place(sky, wLine, 706, 402);
        wAir = text(inter, 11, WARN, 1f);
        wAir.setMaxWidth(px(230));
        wAir.setLineSpacing(px(2), 1f);
        place(sky, wAir, 706, 0);
    }

    private void showWeather() {
        if (!weather.ok) { wCity.setText(""); wTemp.setText(""); return; }
        wCity.setText(weather.city);
        wTemp.setText(Math.round(weather.temp) + "°");
        wSky.setText(weather.describe());
        String line = "";
        if (weather.tonight != 0) line = (weather.tonight < weather.temp ? "Cooling to " : "Warming to ") + Math.round(weather.tonight) + "° by midnight.";
        if (!weather.sunset.isEmpty()) line += (line.isEmpty() ? "" : " ") + "Sunset at " + weather.sunset + ".";
        wLine.setText(line);
        String air = weather.airAdvice();
        wAir.setText(air == null ? "" : air);
        wAir.setTextColor(weather.aqi > 100 ? WARN : PORCELAIN);
        wAir.setAlpha(weather.aqi > 100 ? 1f : 0.58f);
        ui.post(new Runnable() { public void run() {
            ((FrameLayout.LayoutParams) wAir.getLayoutParams()).topMargin = (int) (wLine.getY() + wLine.getHeight() + px(4));
            wAir.requestLayout();
            placePin();
        } });
    }

    // ================================================================== orbit

    private void buildOrbit() {
        orbit = new FrameLayout(this);
        orbit.setClipChildren(false);
        sky.addView(orbit, match());
        orbit.addView(new OrbitPath(), match());
        for (Apps.App a : apps.favourites()) {
            Card c = new Card(this, a, px(10));
            orbit.addView(c, new FrameLayout.LayoutParams(px(SAT_W), px(SAT_H)));
            sats.add(c);
        }
        focusName = text(manropeXL, 34, PORCELAIN, 1f);
        focusName.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        nl.topMargin = px(ORBIT_CY + ORBIT_RY + SAT_H / 2 + 8);
        sky.addView(focusName, nl);
        focusMeta = text(inter, 12, PORCELAIN, 0.55f);
        focusMeta.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        ml.topMargin = px(ORBIT_CY + ORBIT_RY + SAT_H / 2 + 52);
        sky.addView(focusMeta, ml);

        hint = text(inter, 11, PORCELAIN, 0.45f);
        hint.setText("← →  Choose        OK  Open        ↓  All apps        ≡  Settings");
        hint.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        hl.bottomMargin = px(22);
        sky.addView(hint, hl);

        focus = Math.max(0, Math.min(prefs.getInt("focus", 0), sats.size() - 1));
        angle = focus;
        layoutOrbit();
        showFocus(false);
    }

    /** Place every satellite on the ellipse for the current (continuous) angle. */
    private void layoutOrbit() {
        int n = sats.size();
        if (n == 0) return;
        float step = (float) (2 * Math.PI / Math.max(n, 9)); // spread at least 7 slots so neighbours keep their size
        for (int i = 0; i < n; i++) {
            float d = i - angle;
            // wrap to the shortest way round
            while (d > n / 2f) d -= n;
            while (d < -n / 2f) d += n;
            float th = d * step;
            float x = (float) Math.sin(th), z = (float) Math.cos(th); // z = 1 nearest the viewer
            float depth = (z + 1) / 2;                                 // 0 far .. 1 near
            float s = 0.36f + 0.64f * depth * depth;
            Card c = sats.get(i);
            c.setScaleX(s);
            c.setScaleY(s);
            c.setX(px(ORBIT_CX) + x * px(ORBIT_RX) - px(SAT_W) / 2);
            c.setY(px(ORBIT_CY) + z * px(ORBIT_RY) - px(SAT_H) / 2);
            float vis = Math.max(0f, Math.min(1f, (z - 0.3f) / 0.5f));      // fade out well before the far side
            c.setAlpha(vis * (depth > 0.97f ? 1f : 0.55f + 0.3f * vis));
            c.setTranslationZ(depth * px(20));
            c.setRotationY(-x * 18 * Motion.scale);
            c.setCameraDistance(8000 * dp);
        }
    }

    /** The orbit itself: a fine dotted ellipse, bright where it passes in front, gone behind. */
    private final class OrbitPath extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        OrbitPath() { super(Nova.this); p.setColor(PORCELAIN); }
        @Override protected void onDraw(Canvas c) {
            float cx = px(ORBIT_CX), cy = px(ORBIT_CY), rx = px(ORBIT_RX + 70), ry = px(ORBIT_RY + 14);
            for (int i = 0; i < 220; i++) {
                double th = i / 220.0 * 2 * Math.PI;
                float z = (float) Math.cos(th);                 // 1 = nearest the viewer
                float a = Math.max(0f, (z + 0.15f) / 1.15f);
                if (a <= 0) continue;
                p.setAlpha((int) (10 + 70 * a * a));
                c.drawCircle(cx + (float) Math.sin(th) * rx, cy + z * ry, dp * (0.6f + 0.5f * a), p);
            }
        }
    }

    private void turnTo(int target) {
        int n = sats.size();
        if (n == 0) return;
        float from = angle, to = from + shortest(target - from, n);
        focus = ((target % n) + n) % n;
        if (turn != null) turn.cancel();
        turn = ValueAnimator.ofFloat(from, to);
        turn.setDuration(Motion.ms(620));
        turn.setInterpolator(Motion.GLIDE);
        turn.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            public void onAnimationUpdate(ValueAnimator va) { angle = (Float) va.getAnimatedValue(); layoutOrbit(); }
        });
        turn.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) { angle = ((angle % n) + n) % n; }
        });
        turn.start();
        showFocus(true);
        prefs.edit().putInt("focus", focus).apply();
    }

    private static float shortest(float d, int n) {
        while (d > n / 2f) d -= n;
        while (d < -n / 2f) d += n;
        return d;
    }

    private void showFocus(boolean animate) {
        if (sats.isEmpty()) return;
        final Apps.App a = sats.get(focus).app;
        if (!animate) { focusName.setText(a.label); focusMeta.setText(context(a)); return; }
        focusName.animate().cancel();
        focusName.animate().alpha(0f).setStartDelay(0).setDuration(90).withEndAction(new Runnable() { public void run() {
            focusName.setText(a.label);
            focusMeta.setText(context(a));
            focusName.animate().alpha(1f).setStartDelay(0).setDuration(Motion.ms(360)).start();
        } }).start();
    }

    private String context(Apps.App a) {
        long last = apps.lastOpened(a);
        if (last <= 0) return "Press OK to open";
        long ago = System.currentTimeMillis() - last;
        if (ago < 60_000) return "Opened just now";
        return "Opened " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
    }

    // ================================================================== now playing

    /** Whatever is playing in the background (music apps keep playing behind NOVA). Up pauses or resumes. */
    private void buildPlaying() {
        playing = new LinearLayout(this);
        playing.setGravity(Gravity.CENTER_VERTICAL);
        playing.setPadding(px(8), px(8), px(18), px(8));
        Glass.apply(playing, px(20), Glass.ORNAMENT, dp, 18);
        playArt = new ImageView(this);
        playArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        playArt.setOutlineProvider(new android.view.ViewOutlineProvider() {
            public void getOutline(View v, android.graphics.Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), px(12)); }
        });
        playArt.setClipToOutline(true);
        playing.addView(playArt, new LinearLayout.LayoutParams(px(52), px(52)));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        playTitle = text(interM, 13, PORCELAIN, 1f);
        playTitle.setSingleLine(true);
        playTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        playTitle.setMaxWidth(px(200));
        playArtist = text(inter, 11, PORCELAIN, 0.6f);
        playArtist.setSingleLine(true);
        playArtist.setMaxWidth(px(200));
        playHint = text(inter, 10, PORCELAIN, 0.45f);
        t.addView(playTitle);
        t.addView(playArtist);
        t.addView(playHint);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(-2, -2);
        tl.leftMargin = px(12);
        playing.addView(t, tl);
        FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        l.setMargins(px(46), 0, 0, px(52));
        playing.setVisibility(View.GONE);
        sky.addView(playing, l);
    }

    private void refreshPlaying() {
        now = Media.current(this);
        boolean show = now != null && mode != Mode.AMBIENT;
        if (show) {
            playTitle.setText(now.title);
            playArtist.setText(now.artist == null ? "" : now.artist);
            playHint.setText(now.playing ? "\u2191  Pause" : "\u2191  Play");
            if (now.art != null) playArt.setImageBitmap(now.art);
            else playArt.setImageDrawable(null);
            playArt.setVisibility(now.art != null ? View.VISIBLE : View.GONE);
        }
        if (show && playing.getVisibility() != View.VISIBLE) {
            playing.setVisibility(View.VISIBLE);
            playing.setAlpha(0f);
            playing.setTranslationY(px(12));
            playing.animate().alpha(1f).translationY(0).setStartDelay(0).setDuration(Motion.ms(600)).setInterpolator(Motion.GLIDE).start();
        } else if (!show && playing.getVisibility() == View.VISIBLE) {
            playing.animate().alpha(0f).setDuration(Motion.ms(300)).withEndAction(new Runnable() { public void run() { playing.setVisibility(View.GONE); } }).start();
        }
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
        img.setImageBitmap(a.banner);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        launchLayer.addView(img, new FrameLayout.LayoutParams((int) w, (int) h));
        img.setX(loc[0]);
        img.setY(loc[1]);
        img.setPivotX(0);
        img.setPivotY(0);
        float sw = root.getWidth(), sh = root.getHeight(), s = Math.max(sw / w, sh / h);
        long d = Motion.ms(520);
        sky.animate().alpha(0f).scaleX(1.06f).scaleY(1.06f).setDuration(d).setInterpolator(Motion.EASE).start();
        img.animate().x((sw - w * s) / 2).y((sh - h * s) / 2).scaleX(s).scaleY(s).alpha(0f)
                .setDuration(d).setInterpolator(Motion.GLIDE).setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator an) {
                        try { startActivity(a.launch); overridePendingTransition(0, 0); }
                        catch (Exception ex) { comeBack(); }
                    }
                }).start();
    }

    private void comeBack() {
        launchLayer.removeAllViews();
        launching = false;
        sky.setAlpha(0f);
        sky.setScaleX(0.96f);
        sky.setScaleY(0.96f);
        sky.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(800)).setInterpolator(Motion.GLIDE).start();
        showFocus(false);
    }

    // ================================================================== lifecycle

    @Override protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE);
        lastInput = SystemClock.uptimeMillis();
        if (mode == Mode.AMBIENT) exitAmbient();
        if (!entered) { entered = true; enter(); } else comeBack();
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        if (mode == Mode.DRAWER) drawer.close();
        if (mode == Mode.CONTROLS) controls.close();
    }

    private void enter() {
        View[] seq = {time, date, moonView, moonName, moonNext, wCity, wTemp.getParent() instanceof View ? (View) wTemp.getParent() : wTemp, wLine, wAir};
        for (int i = 0; i < seq.length; i++) rise(seq[i], 500 + i * 60);
        orbit.setAlpha(0f);
        orbit.setScaleX(0.9f);
        orbit.setScaleY(0.9f);
        orbit.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(900).setDuration(1400).setInterpolator(Motion.GLIDE).start();
        rise(focusName, 1300);
        rise(focusMeta, 1380);
        rise(hint, 1700);
    }

    private void rise(View v, long delay) {
        float a = v.getTag() instanceof Float ? (Float) v.getTag() : 1f;
        v.setAlpha(0f);
        v.setTranslationY(px(10));
        final View fv = v;
        v.animate().alpha(a).translationY(0).setStartDelay(Motion.ms(delay)).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE)
                .withEndAction(new Runnable() { public void run() { fv.animate().setStartDelay(0); } }).start();
    }

    // ================================================================== time & ambient

    private final Runnable tick = new Runnable() {
        public void run() {
            Calendar c = Calendar.getInstance();
            time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(c.getTime()));
            date.setText(new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(c.getTime()));
            long now = System.currentTimeMillis();
            if (c.get(Calendar.SECOND) == 0 || moonView.getDrawable() == null) updateMoon(now);
            if (c.get(Calendar.SECOND) % 2 == 0) refreshPlaying();
            long idle = SystemClock.uptimeMillis() - lastInput;
            if (mode == Mode.ORBIT && !launching && hasWindowFocus() && idle > prefs.getInt("ambientMin", 3) * 60_000L) enterAmbient();
            ui.postDelayed(this, 1000 - now % 1000 + 5);
        }
    };

    private void updateMoon(long now) {
        double age = Earth.moonAge(now);
        if (moonView.getDrawable() == null || ((int) (age * 4)) != (moonView.getTag() instanceof Integer ? (Integer) moonView.getTag() : -1)) {
            moonView.setImageBitmap(Earth.moon(px(42), age));
            moonView.setTag((int) (age * 4));
        }
        moonName.setText(Earth.moonName(age) + " moon");
        double toFull = ((14.765 - age) % 29.530588853 + 29.530588853) % 29.530588853;
        String full = new SimpleDateFormat("d MMMM", Locale.getDefault()).format(now + (long) (toFull * 86400000L));
        moonNext.setText(toFull < 0.6 ? "Full tonight" : "Full on " + full);
    }

    /** Idle: the orbit and the words fall away; the planet rises and fills the screen. */
    private void enterAmbient() {
        mode = Mode.AMBIENT;
        for (View v : new View[]{orbit, focusName, focusMeta, hint, wLine, wAir, moonName, moonNext, leader, pin})
            v.animate().alpha(0f).setDuration(1800).start();
        planet.animate().translationY(-px(150)).scaleX(1.12f).scaleY(1.12f).setDuration(6000).setInterpolator(Motion.EASE).start();
    }

    private void exitAmbient() {
        mode = Mode.ORBIT;
        for (View v : new View[]{orbit, focusName, focusMeta, hint, wLine, wAir, moonName, moonNext, leader, pin}) {
            float a = v.getTag() instanceof Float ? (Float) v.getTag() : 1f;
            v.animate().alpha(v == wAir && weather.aqi > 100 ? 1f : a).setDuration(Motion.ms(600)).start();
        }
        planet.animate().translationY(0).scaleX(1f).scaleY(1f).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE).start();
    }

    // ================================================================== input

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        lastInput = SystemClock.uptimeMillis();
        if (mode == Mode.AMBIENT) {
            if (e.getAction() == KeyEvent.ACTION_UP) exitAmbient();
            return true;
        }
        if (launching) return true;
        if (e.getAction() != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        boolean ok = k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER || k == KeyEvent.KEYCODE_BUTTON_A;
        if (mode == Mode.DRAWER) return drawer.key(k, ok);
        if (mode == Mode.CONTROLS) return controls.key(k, ok);
        if (ok) { if (!sats.isEmpty()) launch(sats.get(focus), sats.get(focus).app); return true; }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_RIGHT: turnTo(focus + 1); return true;
            case KeyEvent.KEYCODE_DPAD_LEFT: turnTo(focus - 1); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN: drawer.open(); return true;
            case KeyEvent.KEYCODE_MENU: controls.open(); return true;
            case KeyEvent.KEYCODE_BACK: if (focus != 0) turnTo(0); return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (now != null && now.controller != null) {
                    if (now.playing) now.controller.getTransportControls().pause(); else now.controller.getTransportControls().play();
                    ui.postDelayed(new Runnable() { public void run() { refreshPlaying(); } }, 400);
                }
                return true;
        }
        return super.dispatchKeyEvent(e);
    }

    // ================================================================== all apps: a glass sheet

    private final class Drawer {
        static final int COLS = 4;
        final FrameLayout sheet = new FrameLayout(Nova.this);
        final FrameLayout grid = new FrameLayout(Nova.this);
        final List<Card> cards = new ArrayList<>();
        final List<TextView> names = new ArrayList<>();
        final TextView title = text(manropeL, 26, PORCELAIN, 1f), sub = text(inter, 12, PORCELAIN, 0.55f);
        final float W = 178, H = 100, GX = 22, ROW = 142, TOP = 88, LEFT = 40;
        int sel;

        Drawer() {
            Glass.apply(sheet, px(34), 0xf2161a21, dp, 30);
            sheet.setClipToOutline(true);
            title.setText("All apps");
            place(sheet, title, LEFT, 30);
            place(sheet, sub, LEFT + 2, 64);
            FrameLayout.LayoutParams gl = new FrameLayout.LayoutParams(-1, -1);
            gl.topMargin = px(TOP);
            sheet.addView(grid, gl);
            grid.setClipChildren(false);
            TextView h = text(inter, 11, PORCELAIN, 0.45f);
            h.setText("OK  Open        ≡  Pin to orbit        Back  Close");
            FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            hl.setMargins(0, px(46), px(LEFT), 0);
            sheet.addView(h, hl);
            FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(px(LEFT * 2 + COLS * W + (COLS - 1) * GX), px(470), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
            sl.bottomMargin = px(24);
            sheet.setVisibility(View.GONE);
            root.addView(sheet, root.indexOfChild(launchLayer), sl);
        }

        void open() {
            mode = Mode.DRAWER;
            grid.removeAllViews();
            cards.clear();
            names.clear();
            for (int i = 0; i < apps.all.size(); i++) {
                Apps.App a = apps.all.get(i);
                Card c = new Card(Nova.this, a, px(12));
                c.marked = apps.isFavourite(a);
                FrameLayout.LayoutParams l = new FrameLayout.LayoutParams(px(W), px(H));
                l.leftMargin = px(LEFT + (i % COLS) * (W + GX));
                l.topMargin = px((i / COLS) * ROW);
                grid.addView(c, l);
                TextView n = text(inter, 12.5f, PORCELAIN, 0.7f);
                n.setText(a.label);
                n.setSingleLine(true);
                n.setEllipsize(android.text.TextUtils.TruncateAt.END);
                n.setShadowLayer(0, 0, 0, 0);
                FrameLayout.LayoutParams nl = new FrameLayout.LayoutParams(px(W), -2);
                nl.leftMargin = l.leftMargin;
                nl.topMargin = l.topMargin + px(H + 9);
                grid.addView(n, nl);
                cards.add(c);
                names.add(n);
            }
            sub.setText(apps.all.size() + " apps on this TV");
            sel = Math.min(sel, cards.size() - 1);
            sheet.setVisibility(View.VISIBLE);
            sheet.setAlpha(0f);
            sheet.setTranslationY(px(80));
            sheet.animate().alpha(1f).translationY(0).setDuration(Motion.ms(620)).setInterpolator(Motion.GLIDE).start();
            sky.animate().alpha(0.12f).scaleX(0.97f).scaleY(0.97f).setDuration(Motion.ms(620)).setInterpolator(Motion.GLIDE).start();
            select(sel, false);
        }

        void close() {
            mode = Mode.ORBIT;
            sheet.animate().alpha(0f).translationY(px(80)).setDuration(Motion.ms(360)).setInterpolator(Motion.EASE).withEndAction(new Runnable() {
                public void run() { sheet.setVisibility(View.GONE); }
            }).start();
            sky.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(Motion.ms(620)).setInterpolator(Motion.GLIDE).start();
            syncOrbit();
        }

        void select(int i, boolean animate) {
            if (cards.isEmpty()) return;
            sel = Math.max(0, Math.min(i, cards.size() - 1));
            for (int j = 0; j < cards.size(); j++) {
                boolean on = j == sel;
                cards.get(j).animate().scaleX(on ? 1.08f : 1f).scaleY(on ? 1.08f : 1f).alpha(on ? 1f : 0.62f)
                        .setDuration(animate ? Motion.ms(300) : 0).setInterpolator(Motion.FOCUS).start();
                names.get(j).animate().alpha(on ? 1f : 0.55f).translationY(on ? px(5) : 0).setDuration(animate ? Motion.ms(300) : 0).start();
                cards.get(j).ring(on);
            }
            int row = sel / COLS;
            float shift = -Math.max(0, row - 1) * px(ROW);
            grid.animate().translationY(shift).setDuration(animate ? Motion.ms(420) : 0).setInterpolator(Motion.GLIDE).start();
        }

        boolean key(int k, boolean ok) {
            if (ok) { Card c = cards.get(sel); mode = Mode.ORBIT; sheet.setVisibility(View.GONE); sky.setAlpha(1f); sky.setScaleX(1f); sky.setScaleY(1f); launch(c, c.app); return true; }
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_RIGHT: if (sel % COLS < COLS - 1 && sel + 1 < cards.size()) select(sel + 1, true); return true;
                case KeyEvent.KEYCODE_DPAD_LEFT: if (sel % COLS > 0) select(sel - 1, true); return true;
                case KeyEvent.KEYCODE_DPAD_DOWN: if (sel + COLS < cards.size()) select(sel + COLS, true); return true;
                case KeyEvent.KEYCODE_DPAD_UP: if (sel >= COLS) select(sel - COLS, true); else close(); return true;
                case KeyEvent.KEYCODE_BACK: close(); return true;
                case KeyEvent.KEYCODE_MENU: {
                    Card c = cards.get(sel);
                    apps.toggleFavourite(c.app);
                    c.marked = apps.isFavourite(c.app);
                    c.invalidate();
                    return true;
                }
            }
            return true;
        }
    }

    /** Rebuild the orbit if pins changed in the drawer. */
    private void syncOrbit() {
        List<Apps.App> f = apps.favourites();
        boolean same = f.size() == sats.size();
        for (int i = 0; same && i < f.size(); i++) same = f.get(i) == sats.get(i).app;
        if (same) return;
        for (Card c : sats) orbit.removeView(c);
        sats.clear();
        for (Apps.App a : f) {
            Card c = new Card(this, a, px(10));
            orbit.addView(c, new FrameLayout.LayoutParams(px(SAT_W), px(SAT_H)));
            sats.add(c);
        }
        focus = Math.min(focus, sats.size() - 1);
        angle = focus;
        layoutOrbit();
        showFocus(false);
    }

    // ================================================================== settings

    private final class Controls {
        final LinearLayout panel = new LinearLayout(Nova.this);
        final List<Row> rows = new ArrayList<>();
        int rsel;

        final class Row {
            final LinearLayout v = new LinearLayout(Nova.this);
            final TextView label = text(inter, 15, PORCELAIN, 1f), value = text(inter, 13, GLACIER, 0.9f);
            final Value val;
            final Runnable action;
            final Glass g;
            Row(String l, Value val, Runnable action) {
                this.val = val;
                this.action = action;
                v.setGravity(Gravity.CENTER_VERTICAL);
                v.setPadding(px(18), 0, px(18), 0);
                g = Glass.apply(v, px(21), 0, dp, 0);
                label.setText(l);
                label.setShadowLayer(0, 0, 0, 0);
                value.setShadowLayer(0, 0, 0, 0);
                v.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
                v.addView(value);
            }
            void refresh(boolean on) {
                value.setText(val == null ? "" : val.get());
                g.tintTo(on ? Glass.SELECTED : 0);
                label.setTextColor(on ? OBSIDIAN : PORCELAIN);
                value.setTextColor(on ? 0xff20242c : GLACIER);
            }
        }

        Controls() {
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(px(14), px(26), px(14), px(18));
            Glass.apply(panel, px(30), 0xc01a1e26, dp, 36);
            TextView head = text(manropeL, 26, PORCELAIN, 1f);
            head.setText("Settings");
            head.setPadding(px(16), 0, 0, px(16));
            panel.addView(head);
            add("Show Earth now", null, new Runnable() { public void run() { close(); ui.postDelayed(new Runnable() { public void run() { enterAmbient(); } }, 350); } });
            add("Show Earth after", new Value() { public String get() { int m = prefs.getInt("ambientMin", 3); return m + (m == 1 ? " minute" : " minutes"); } },
                    new Runnable() { public void run() { int[] o = {1, 3, 5, 10, 20}; int cur = prefs.getInt("ambientMin", 3), n = o[0];
                        for (int i = 0; i < o.length; i++) if (o[i] == cur) n = o[(i + 1) % o.length]; prefs.edit().putInt("ambientMin", n).apply(); } });
            add("Motion", new Value() { public String get() { return Motion.scale < 1 ? "Reduced" : "Full"; } },
                    new Runnable() { public void run() { boolean r = Motion.scale == 1f; Motion.scale = r ? .55f : 1f; prefs.edit().putBoolean("reducedMotion", r).apply(); } });
            add("Wi-Fi", null, settings("android.settings.PICK_WIFI_NETWORK"));
            add("Manage apps", null, settings("android.settings.APPLICATION_SETTINGS"));
            add("About this TV", null, settings("android.settings.DEVICE_INFO_SETTINGS"));
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(px(330), -2, Gravity.END | Gravity.CENTER_VERTICAL);
            lp.rightMargin = px(46);
            panel.setVisibility(View.GONE);
            root.addView(panel, root.indexOfChild(launchLayer), lp);
        }

        void add(String l, Value v, Runnable r) {
            Row row = new Row(l, v, r);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, px(44));
            lp.topMargin = px(4);
            panel.addView(row.v, lp);
            rows.add(row);
        }

        Runnable settings(final String action) {
            return new Runnable() { public void run() {
                try { startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); close(); } catch (Exception ignored) { }
            } };
        }

        void open() {
            mode = Mode.CONTROLS;
            refresh();
            panel.setVisibility(View.VISIBLE);
            panel.setAlpha(0f);
            panel.setTranslationX(px(50));
            panel.animate().alpha(1f).translationX(0).setDuration(Motion.ms(600)).setInterpolator(Motion.GLIDE).start();
            sky.animate().alpha(0.4f).setDuration(Motion.ms(600)).start();
        }

        void close() {
            mode = Mode.ORBIT;
            panel.animate().alpha(0f).translationX(px(50)).setDuration(Motion.ms(320)).withEndAction(new Runnable() { public void run() { panel.setVisibility(View.GONE); } }).start();
            sky.animate().alpha(1f).setDuration(Motion.ms(500)).start();
        }

        void refresh() { for (int i = 0; i < rows.size(); i++) rows.get(i).refresh(i == rsel); }

        boolean key(int k, boolean ok) {
            if (ok || k == KeyEvent.KEYCODE_DPAD_RIGHT) { rows.get(rsel).action.run(); if (mode == Mode.CONTROLS) refresh(); return true; }
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_DOWN: rsel = Math.min(rows.size() - 1, rsel + 1); refresh(); return true;
                case KeyEvent.KEYCODE_DPAD_UP: rsel = Math.max(0, rsel - 1); refresh(); return true;
                case KeyEvent.KEYCODE_BACK: case KeyEvent.KEYCODE_MENU: case KeyEvent.KEYCODE_DPAD_LEFT: close(); return true;
            }
            return true;
        }
    }

    interface Value { String get(); }

    // ================================================================== helpers

    private TextView text(Typeface tf, float sp, int color, float alpha) {
        TextView t = new TextView(this);
        t.setTypeface(tf);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setAlpha(alpha);
        t.setTag(alpha);
        t.setShadowLayer(px(10), 0, 0, 0xcc090b10); // keeps type legible where it crosses the planet
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
