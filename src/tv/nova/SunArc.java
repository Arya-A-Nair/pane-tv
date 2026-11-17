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
import java.util.Locale;

/**
 * Today's daylight. One warm arc from sunrise to where the sun is now, the rest of the day
 * dotted ahead of it, a glowing sun on the line, and the two times big enough to read from
 * the sofa. A plain sentence on top says how much daylight is left (or when it returns).
 */
final class SunArc extends View {
    static final int AMBER = 0xffd9874f, GOLD = 0xfff2d3a0, PORCELAIN = 0xffe9ecf2;
    private final Paint past = new Paint(Paint.ANTI_ALIAS_FLAG), ahead = new Paint(Paint.ANTI_ALIAS_FLAG),
            fill = new Paint(Paint.ANTI_ALIAS_FLAG), horizon = new Paint(Paint.ANTI_ALIAS_FLAG),
            halo = new Paint(Paint.ANTI_ALIAS_FLAG), core = new Paint(Paint.ANTI_ALIAS_FLAG),
            big = new Paint(Paint.ANTI_ALIAS_FLAG), small = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arc = new Path(), done = new Path(), area = new Path();
    private final float dp;
    private String rise = "", set = "";

    SunArc(Context c, float dp, Typeface display, Typeface text) {
        super(c);
        this.dp = dp;
        past.setStyle(Paint.Style.STROKE);
        past.setStrokeWidth(2.2f * dp);
        past.setStrokeCap(Paint.Cap.ROUND);
        ahead.setStyle(Paint.Style.STROKE);
        ahead.setStrokeWidth(1.6f * dp);
        ahead.setStrokeCap(Paint.Cap.ROUND);
        ahead.setColor(0x40e9ecf2);
        ahead.setPathEffect(new DashPathEffect(new float[]{0.1f, 5 * dp}, 0)); // round dots
        horizon.setStrokeWidth(Math.max(1f, 0.8f * dp));
        core.setColor(0xfffff4e2);
        big.setTypeface(display);
        big.setTextSize(19 * dp);
        big.setColor(PORCELAIN);
        small.setTypeface(text);
        small.setTextSize(10 * dp);
        small.setColor(0x8ce9ecf2);
        line.setTypeface(text);
        line.setTextSize(11.5f * dp);
        line.setColor(0xd9e9ecf2);
    }

    void set(String sunrise, String sunset) { rise = sunrise; set = sunset; invalidate(); }

    private static int minutes(String hm) {
        try { return Integer.parseInt(hm.substring(0, 2)) * 60 + Integer.parseInt(hm.substring(3, 5)); }
        catch (Exception e) { return -1; }
    }

    private static String span(int m) {
        int h = m / 60, mm = m % 60;
        return h > 0 ? (mm > 0 ? h + " h " + mm + " min" : h + " h") : mm + " min";
    }

    @Override protected void onDraw(Canvas c) {
        int a = minutes(rise), b = minutes(set);
        if (a < 0 || b <= a) return;
        float w = getWidth(), h = getHeight();
        Calendar cal = Calendar.getInstance();
        int now = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE);
        boolean day = now >= a && now <= b;
        float f = day ? (now - a) / (float) (b - a) : now > b ? 1f : 0f;

        // sentence
        String say = day ? span(b - now) + " of daylight left"
                : now > b ? "Sunrise in " + span(24 * 60 - now + a) : "Sunrise in " + span(a - now);
        c.drawText(say, 0, 12 * dp, line);

        // geometry: a flattened arc, like the sun's real path seen from the ground
        float top = 26 * dp, base = h - 34 * dp, x0 = 6 * dp, x1 = w - 6 * dp;
        arc.reset();
        arc.moveTo(x0, base);
        arc.cubicTo(x0 + (x1 - x0) * 0.18f, top - 6 * dp, x0 + (x1 - x0) * 0.82f, top - 6 * dp, x1, base);
        PathMeasure pm = new PathMeasure(arc, false);
        float len = pm.getLength();

        // horizon, fading at both ends
        horizon.setShader(new LinearGradient(0, 0, w, 0, new int[]{0x00e9ecf2, 0x40e9ecf2, 0x40e9ecf2, 0x00e9ecf2},
                new float[]{0f, 0.15f, 0.85f, 1f}, Shader.TileMode.CLAMP));
        c.drawLine(0, base, w, base, horizon);

        // the day still ahead: dotted
        c.drawPath(arc, ahead);

        if (f > 0) {
            done.reset();
            pm.getSegment(0, len * f, done, true);
            float[] pos = new float[2];
            pm.getPosTan(len * f, pos, null);
            // warm light under the travelled part
            area.set(done);
            area.lineTo(pos[0], base);
            area.lineTo(x0, base);
            area.close();
            fill.setShader(new LinearGradient(0, top, 0, base, 0x33d9874f, 0x00d9874f, Shader.TileMode.CLAMP));
            c.drawPath(area, fill);
            past.setShader(new LinearGradient(x0, 0, Math.max(x0 + 1, pos[0]), 0, AMBER, GOLD, Shader.TileMode.CLAMP));
            c.drawPath(done, past);
            if (day) {
                halo.setShader(new RadialGradient(pos[0], pos[1], 16 * dp, new int[]{0x66f2d3a0, 0x22d9874f, 0x00d9874f}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                c.drawCircle(pos[0], pos[1], 16 * dp, halo);
                c.drawCircle(pos[0], pos[1], 4.6f * dp, core);
            }
        }

        // the two times, readable from across the room
        float ty = h - 10 * dp;
        big.setTextAlign(Paint.Align.LEFT);
        c.drawText(rise, 0, ty, big);
        small.setTextAlign(Paint.Align.LEFT);
        c.drawText("Sunrise", big.measureText(rise) + 6 * dp, ty, small);
        big.setTextAlign(Paint.Align.RIGHT);
        c.drawText(set, w, ty, big);
        small.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Sunset", w - big.measureText(set) - 6 * dp, ty, small);
    }
}
