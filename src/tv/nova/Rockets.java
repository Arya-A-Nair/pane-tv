package tv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import android.widget.ImageView;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Now and then a launch lifts off from a real spaceport on the visible face of the globe:
 * a flash at the pad, a bright point that accelerates and bends east as real launches do,
 * a fading amber trail, then gone into space. Draws nothing between launches.
 */
final class Rockets extends View {
    // real launch sites: name, lat, lon
    static final double[][] SITES = {
            {13.72, 80.23},   // Satish Dhawan, Sriharikota (ISRO)
            {45.96, 63.31},   // Baikonur
            {40.96, 100.29},  // Jiuquan
            {30.40, 130.97},  // Tanegashima
            {19.61, 110.95},  // Wenchang
    };
    static final long FLIGHT_MS = 11000;

    private final ImageView planet;
    private final float dp;
    private final int planetSize;
    private final Random rnd = new Random();
    private final Paint trail = new Paint(Paint.ANTI_ALIAS_FLAG), head = new Paint(Paint.ANTI_ALIAS_FLAG),
            flash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Flight> flights = new ArrayList<>();
    private long nextAt;
    boolean enabled = true;

    private static final class Flight {
        float x0, y0, cx, cy, x1, y1; // quadratic path in view coords
        long start;
    }

    Rockets(Context c, ImageView planet, int planetSize, float dp) {
        super(c);
        this.planet = planet;
        this.planetSize = planetSize;
        this.dp = dp;
        trail.setStyle(Paint.Style.STROKE);
        trail.setStrokeCap(Paint.Cap.ROUND);
        head.setColor(0xfffff1dc);
        nextAt = SystemClock.uptimeMillis() + 8000; // the first one comes early
        post(clock);
    }

    /** Checks once a second whether it's time for a launch; animation only runs while one is in the air. */
    private final Runnable clock = new Runnable() {
        public void run() {
            long now = SystemClock.uptimeMillis();
            if (enabled && now >= nextAt && planet.getDrawable() != null && planet.getAlpha() > 0.9f) {
                launch();
                nextAt = now + 60000 + rnd.nextInt(60000);
            }
            postDelayed(this, 1000);
        }
    };

    private void launch() {
        List<float[]> visible = new ArrayList<>();
        for (double[] s : SITES) {
            float[] at = Earth.locate(s[0], s[1], planetSize);
            if (at != null) visible.add(at);
        }
        if (visible.isEmpty()) return;
        float[] at = visible.get(rnd.nextInt(visible.size())).clone();
        // planet bitmap coords -> this view's coords (follows the planet's own drift and scale)
        planet.getMatrix().mapPoints(at);
        float px = at[0] + planet.getLeft(), py = at[1] + planet.getTop();
        float pcx = planet.getLeft() + planet.getWidth() / 2f, pcy = planet.getTop() + planet.getHeight() / 2f;
        // straight up from the surface, then bending east (screen right) toward orbit
        float ux = px - pcx, uy = py - pcy, ul = (float) Math.hypot(ux, uy);
        ux /= ul; uy /= ul;
        float rise = (170 + rnd.nextInt(80)) * dp, east = (150 + rnd.nextInt(90)) * dp;
        Flight f = new Flight();
        f.x0 = px; f.y0 = py;
        f.cx = px + ux * rise * 0.7f; f.cy = py + uy * rise * 0.7f;
        f.x1 = px + ux * rise + east; f.y1 = py + uy * rise - 40 * dp;
        f.start = SystemClock.uptimeMillis();
        flights.add(f);
        postInvalidateOnAnimation();
    }

    private static float bez(float a, float b, float c, float t) { float u = 1 - t; return u * u * a + 2 * u * t * b + t * t * c; }

    @Override protected void onDraw(Canvas c) {
        long now = SystemClock.uptimeMillis();
        for (int i = flights.size() - 1; i >= 0; i--) {
            Flight f = flights.get(i);
            float age = (now - f.start) / (float) FLIGHT_MS;
            if (age >= 1f) { flights.remove(i); continue; }
            float t = age * age;                      // accelerating, like a real ascent
            float fade = age < 0.75f ? 1f : 1 - (age - 0.75f) / 0.25f;

            // pad flash in the first second
            if (age < 0.12f) {
                float k = 1 - age / 0.12f;
                flash.setShader(new RadialGradient(f.x0, f.y0, 14 * dp, new int[]{(int) (180 * k) << 24 | 0xffd9a0, 0x00ffd9a0}, null, Shader.TileMode.CLAMP));
                c.drawCircle(f.x0, f.y0, 14 * dp, flash);
            }
            // exhaust trail: segments behind the head, thinning and fading
            int segs = 36;
            float tail = Math.min(t, 0.35f);
            float px = bez(f.x0, f.cx, f.x1, Math.max(0, t - tail)), py = bez(f.y0, f.cy, f.y1, Math.max(0, t - tail));
            for (int s = 1; s <= segs; s++) {
                float tt = Math.max(0, t - tail) + tail * s / segs;
                float x = bez(f.x0, f.cx, f.x1, tt), y = bez(f.y0, f.cy, f.y1, tt);
                float k = s / (float) segs;
                trail.setStrokeWidth(dp * (0.5f + 1.4f * k));
                trail.setColor(((int) (200 * k * k * fade) << 24) | 0xe8a060);
                c.drawLine(px, py, x, y, trail);
                px = x; py = y;
            }
            // the rocket itself
            float hx = bez(f.x0, f.cx, f.x1, t), hy = bez(f.y0, f.cy, f.y1, t);
            flash.setShader(new RadialGradient(hx, hy, 7 * dp, new int[]{(int) (150 * fade) << 24 | 0xfff1dc, 0x00fff1dc}, null, Shader.TileMode.CLAMP));
            c.drawCircle(hx, hy, 7 * dp, flash);
            head.setAlpha((int) (255 * fade));
            c.drawCircle(hx, hy, 1.8f * dp, head);
        }
        if (!flights.isEmpty()) postInvalidateOnAnimation();
    }
}
