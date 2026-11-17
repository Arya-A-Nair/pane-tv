package tv.nova;

import android.content.Context;
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
import java.util.Calendar;

/**
 * Today's daylight: the sun's path from sunrise to sunset over a horizon. The part of the day
 * already gone is a solid Champagne line, the rest is dotted, the sun sits where it is now,
 * and the two times stand under the two ends.
 */
final class SunArc extends View {
    private final Paint past = new Paint(Paint.ANTI_ALIAS_FLAG), ahead = new Paint(Paint.ANTI_ALIAS_FLAG),
            horizon = new Paint(Paint.ANTI_ALIAS_FLAG), halo = new Paint(Paint.ANTI_ALIAS_FLAG),
            core = new Paint(Paint.ANTI_ALIAS_FLAG), time = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arc = new Path(), done = new Path();
    private final float dp;
    private String rise = "", set = "";
    static int nowOverride = -1; // minutes since midnight, for testing states

    SunArc(Context c, float dp, Typeface text) {
        super(c);
        this.dp = dp;
        past.setStyle(Paint.Style.STROKE);
        past.setStrokeWidth(2.5f * dp);
        past.setStrokeCap(Paint.Cap.ROUND);
        ahead.setStyle(Paint.Style.STROKE);
        ahead.setStrokeWidth(2f * dp);
        ahead.setStrokeCap(Paint.Cap.ROUND);
        ahead.setColor(0x73e9ecf2);
        ahead.setPathEffect(new DashPathEffect(new float[]{0.1f, 6 * dp}, 0)); // round dots
        horizon.setStrokeWidth(Math.max(1.5f, 0.75f * dp));
        horizon.setColor(0x40e9ecf2);
        core.setColor(0xfffff4e2);
        time.setTypeface(text);
        time.setTextSize(15 * dp);
        time.setColor(Nova.PORCELAIN);
    }

    void set(String sunrise, String sunset) { rise = sunrise; set = sunset; invalidate(); }

    private static int minutes(String hm) {
        try { return Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5)); }
        catch (Exception e) { return -1; }
    }

    private static int now() {
        if (nowOverride >= 0) return nowOverride;
        Calendar cal = Calendar.getInstance();
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
    }

    private static String span(int m) {
        int h = m / 60, mm = m % 60;
        return h > 0 ? (mm > 0 ? h + " h " + mm + " min" : h + " h") : mm + " min";
    }

    /** {amount, what it is}: "5 h 12 min", "of daylight left" — or the wait until sunrise. Null before data. */
    String[] summary() {
        int a = minutes(rise), b = minutes(set), n = now();
        if (a < 0 || b <= a) return null;
        if (n >= a && n <= b) return new String[]{span(b - n), "of daylight left"};
        return new String[]{span(n > b ? 24 * 60 - n + a : a - n), "until sunrise"};
    }

    @Override protected void onDraw(Canvas c) {
        int a = minutes(rise), b = minutes(set);
        if (a < 0 || b <= a) return;
        float w = getWidth(), h = getHeight();
        int now = now();
        boolean day = now >= a && now <= b;
        float f = day ? (now - a) / (float) (b - a) : 0f;   // at night the whole path waits, dotted

        float top = 8 * dp, base = h - 28 * dp, x0 = 14 * dp, x1 = w - 14 * dp;
        arc.reset();
        arc.moveTo(x0, base);
        arc.cubicTo(x0 + (x1 - x0) * 0.2f, top - (base - top) * 0.33f, x0 + (x1 - x0) * 0.8f, top - (base - top) * 0.33f, x1, base);
        PathMeasure pm = new PathMeasure(arc, false);
        float len = pm.getLength();

        c.drawLine(0, base, w, base, horizon);
        c.drawPath(arc, ahead);
        if (f > 0) {
            done.reset();
            pm.getSegment(0, len * f, done, true);
            float[] pos = new float[2];
            pm.getPosTan(len * f, pos, null);
            past.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, pos[0]), 0, Nova.CHAMPAGNE, 0xffe8d5bc, Shader.TileMode.CLAMP));
            c.drawPath(done, past);
            if (day) {
                halo.setShader(new RadialGradient(pos[0], pos[1], 15 * dp, new int[]{0x70f2dcb8, 0x24b4a28c, 0x00b4a28c}, new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(pos[0], pos[1], 15 * dp, halo);
                c.drawCircle(pos[0], pos[1], 5 * dp, core);
            }
        }
        float ty = h - 3 * dp;
        time.setTextAlign(Paint.Align.LEFT);
        c.drawText(rise, 0, ty, time);
        time.setTextAlign(Paint.Align.RIGHT);
        c.drawText(set, w, ty, time);
    }
}
