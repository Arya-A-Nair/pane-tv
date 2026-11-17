package tv.nova;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * What is crossing the sky. By day, the sun's path from sunrise to sunset in Champagne, the
 * sun where it is now. By night, the moon's real path from moonrise to moonset in Glacier,
 * the moon itself — at tonight's phase — where it is now. The part already travelled is a
 * solid line, the rest is dotted, and the two times stand under the two ends.
 */
final class SkyArc extends View {
    /** The words that go beside the arc: a caption, one large value, a note. */
    static final class Reading {
        final String caption, value, note;
        Reading(String caption, String value, String note) { this.caption = caption; this.value = value; this.note = note; }
    }

    /** Minutes since midnight; set only from adb to show other times of day. */
    static int testNow = -1;

    private static final int UNKNOWN = 0, DAY = 1, NIGHT = 2;
    private static final float GLOW = 17;   // radius of the halo round the sun or moon (dp)
    // a few fixed stars, as fractions of the view: sparing, and only at night
    private static final float[][] STARS = {{0.07f, 0.16f}, {0.30f, 0.05f}, {0.47f, 0.42f}, {0.71f, 0.10f}, {0.93f, 0.26f}, {0.60f, 0.55f}};

    private final Paint travelled = new Paint(Paint.ANTI_ALIAS_FLAG), ahead = new Paint(Paint.ANTI_ALIAS_FLAG),
            horizon = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG),
            body = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arc = new Path(), done = new Path();
    private final float dp;

    private int mode = UNKNOWN;
    private String leftTime = "", rightTime = "";
    private float progress;      // 0..1 along the arc
    private boolean bodyUp;      // the sun or moon is above the horizon now
    private Bitmap moon;

    SkyArc(Context c, float dp, Typeface text) {
        super(c);
        this.dp = dp;
        travelled.setStyle(Paint.Style.STROKE);
        travelled.setStrokeWidth(2.5f * dp);
        travelled.setStrokeCap(Paint.Cap.ROUND);
        ahead.setStyle(Paint.Style.STROKE);
        ahead.setStrokeWidth(2f * dp);
        ahead.setStrokeCap(Paint.Cap.ROUND);
        ahead.setColor(0x73e9ecf2);
        ahead.setPathEffect(new DashPathEffect(new float[]{0.1f, 6 * dp}, 0));   // round dots
        horizon.setStrokeWidth(Math.max(1.5f, 0.75f * dp));
        horizon.setColor(0x40e9ecf2);
        time.setTypeface(text);
        time.setTextSize(15 * dp);
        time.setColor(Ui.PORCELAIN);
    }

    /**
     * Work out the sky for now and return what to say about it. Sunrise and sunset are today's, "HH:mm";
     * the place is in degrees and may be NaN, in which case the night shows no moon path. Null before any reading.
     */
    Reading update(String sunrise, String sunset, double latitude, double longitude) {
        int rise = minutes(sunrise), set = minutes(sunset), now = minuteNow();
        int was = mode;
        float from = progress;
        Reading reading;
        if (rise < 0 || set <= rise) {
            mode = UNKNOWN;
            reading = null;
        } else if (now >= rise && now <= set) {
            mode = DAY;
            leftTime = sunrise;
            rightTime = sunset;
            progress = (now - rise) / (float) (set - rise);
            bodyUp = true;
            reading = new Reading("Daylight", span(set - now), "of daylight left");
        } else {
            mode = NIGHT;
            reading = night(sunrise, sunset, rise, set, now, latitude, longitude);
        }
        if (mode != was && was != UNKNOWN) {   // sunset and sunrise: one sky gives way to the other
            setAlpha(0f);
            animate().alpha(1f).setDuration(Motion.ms(900)).start();
        }
        if (mode == was && mode != UNKNOWN && from != progress && isShown() && !Motion.reduced) {   // glide along the path rather than jump
            final float to = progress;
            progress = from;
            ValueAnimator glide = ValueAnimator.ofFloat(from, to).setDuration(450);
            glide.setInterpolator(Motion.GLIDE);
            glide.addUpdateListener(a -> { progress = (Float) a.getAnimatedValue(); invalidate(); });
            glide.start();
        }
        invalidate();
        return reading;
    }

    private Reading night(String sunrise, String sunset, int rise, int set, int now, double latitude, double longitude) {
        long clock = millisNow(), minute = 60_000L;
        // tonight runs from the last sunset to the next sunrise
        long dusk = clock - (now > set ? now - set : now + 24 * 60 - set) * minute;
        long dawn = clock + (now > set ? 24 * 60 - now + rise : rise - now) * minute;
        String next = "Sunrise " + sunrise;
        long[] pass = Double.isNaN(latitude) ? null : MoonPath.pass(dusk, dawn, clock, latitude, longitude);
        if (pass == null) {
            leftTime = sunset;
            rightTime = sunrise;
            progress = 0;
            bodyUp = false;
            return new Reading("Tonight", "No moon", next);
        }
        SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
        leftTime = hm.format(pass[0]);
        rightTime = hm.format(pass[1]);
        progress = Math.max(0f, Math.min(1f, (clock - pass[0]) / (float) (pass[1] - pass[0])));
        bodyUp = clock >= pass[0] && clock <= pass[1];
        if (bodyUp && moon == null) moon = Moon.render(getContext(), Math.round(22 * dp), Moon.age(clock));
        if (clock < pass[0]) return new Reading("Moonrise at", leftTime, next);
        return new Reading(bodyUp ? "Moon sets at" : "Moon set at", rightTime, next);
    }

    /** True outside the hours between the two "HH:mm" times. */
    static boolean isNight(String sunrise, String sunset) { int n = minuteNow(); return n < minutes(sunrise) || n > minutes(sunset); }

    private static int minutes(String hm) {
        try { return Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5)); }
        catch (RuntimeException notATime) { return -1; }
    }

    private static int minuteNow() {
        if (testNow >= 0) return testNow;
        Calendar c = Calendar.getInstance();
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE);
    }

    private static long millisNow() {
        if (testNow < 0) return System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, testNow / 60);
        c.set(Calendar.MINUTE, testNow % 60);
        return c.getTimeInMillis();
    }

    private static String span(int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? (m > 0 ? h + " h " + m + " min" : h + " h") : m + " min";
    }

    @Override protected void onDraw(Canvas c) {
        if (mode == UNKNOWN) return;
        boolean night = mode == NIGHT;
        float w = getWidth(), h = getHeight();
        // the path keeps a glow's width clear of every edge, so neither sun nor moon is ever cut off
        float room = GLOW * dp + 3 * dp, top = room, base = h - 28 * dp, x0 = room, x1 = w - room;
        arc.reset();
        arc.moveTo(x0, base);
        arc.cubicTo(x0 + (x1 - x0) * 0.2f, top - (base - top) * 0.33f, x0 + (x1 - x0) * 0.8f, top - (base - top) * 0.33f, x1, base);
        PathMeasure measure = new PathMeasure(arc, false);
        float length = measure.getLength();

        if (night) {
            body.setColor(0x59e9ecf2);
            for (float[] star : STARS) c.drawCircle(star[0] * w, star[1] * base, dp, body);
            body.setColor(0xffffffff);
        }
        c.drawLine(0, base, w, base, horizon);
        c.drawPath(arc, ahead);
        if (progress > 0) {
            float[] at = new float[2];
            measure.getPosTan(length * progress, at, null);
            done.reset();
            measure.getSegment(0, length * progress, done, true);
            travelled.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, at[0]), 0,
                    night ? Ui.GLACIER : Ui.CHAMPAGNE, night ? Ui.PORCELAIN : Ui.CHAMPAGNE_LIGHT, Shader.TileMode.CLAMP));
            c.drawPath(done, travelled);
            if (bodyUp) {
                int glow = night ? 0xa4b8ee : 0xf2dcb8;
                halo.setShader(new RadialGradient(at[0], at[1], GLOW * dp, new int[]{0x66000000 | glow, 0x1f000000 | glow, glow}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(at[0], at[1], GLOW * dp, halo);
                if (night && moon != null) c.drawBitmap(moon, at[0] - moon.getWidth() / 2f, at[1] - moon.getHeight() / 2f, body);
                else { body.setColor(0xfffff4e2); c.drawCircle(at[0], at[1], 5 * dp, body); body.setColor(0xffffffff); }
            }
        }
        float baseline = h - 3 * dp;
        time.setTextAlign(Paint.Align.LEFT);
        c.drawText(leftTime, 0, baseline, time);
        time.setTextAlign(Paint.Align.RIGHT);
        c.drawText(rightTime, w, baseline, time);
    }
}
