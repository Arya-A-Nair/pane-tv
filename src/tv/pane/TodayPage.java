package tv.pane;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Today: four readings to take in at a glance — weather, air, the sky (the sun by day, the
 * moon's path by night), and the moon's phase. Each has the
 * same anatomy (a quiet label, one large figure, two lines beside it) and one small drawing
 * of its own. Nothing here is selectable.
 */
final class TodayPage extends Page {
    private final RollingText temperature, airIndex;
    private final TextView context, city, condition, range, airName, airNote, skyCaption, skyValue, skyNote, moonName, moonNote;
    private final HourlyCurve hourly;
    private final AirScale airScale;
    private final SkyArc skyArc;
    private final ImageView moon;

    TodayPage(Pane host) {
        super(host);
        context = header("Today");
        float gap = 16, width = Ui.W - 2 * Ui.MARGIN, top = Ui.CONTENT_TOP, h1 = 152, h2 = 140;
        float airW = 320, weatherW = width - airW - gap, half = (width - gap) / 2;

        GlassPanel weather = ui.panel(view, Ui.MARGIN, top, weatherW, h1);
        temperature = new RollingText(ui, ui.manrope, 58, Ui.PORCELAIN);
        TextView[] r = reading(weather, "", temperature);
        city = r[0]; condition = r[1]; range = r[2];
        hourly = new HourlyCurve(ui);
        FrameLayout.LayoutParams hl = new FrameLayout.LayoutParams(ui.px(240), ui.px(h1 - 40), Gravity.END);
        hl.setMargins(0, ui.px(20), ui.px(24), 0);
        weather.addView(hourly, hl);

        GlassPanel air = ui.panel(view, Ui.MARGIN + weatherW + gap, top, airW, h1);
        airIndex = new RollingText(ui, ui.manrope, 58, Ui.PORCELAIN);
        r = reading(air, "Air quality", airIndex);
        airName = r[1]; airNote = r[2];
        airScale = new AirScale(host, ui.dp);
        FrameLayout.LayoutParams al = new FrameLayout.LayoutParams(-1, ui.px(5), Gravity.BOTTOM);
        al.setMargins(ui.px(24), 0, ui.px(24), ui.px(22));
        air.addView(airScale, al);

        float second = top + h1 + gap;
        GlassPanel day = ui.panel(view, Ui.MARGIN, second, half, h2);
        TextView[] d = stacked(day, "Daylight", 24);
        skyCaption = d[0]; skyValue = d[1]; skyNote = d[2];
        skyArc = new SkyArc(host, ui.dp, ui.interMedium);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(ui.px(204), ui.px(h2 - 26), Gravity.END);
        sl.setMargins(0, ui.px(8), ui.px(20), 0);
        day.addView(skyArc, sl);

        GlassPanel sky = ui.panel(view, Ui.MARGIN + half + gap, second, half, h2);
        moon = new ImageView(host);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ui.px(96), ui.px(96), Gravity.CENTER_VERTICAL);
        ml.leftMargin = ui.px(24);
        sky.addView(moon, ml);
        TextView[] m = stacked(sky, "Moon", 144);
        moonName = m[1]; moonNote = m[2];
        row(weather, air);
        row(day, sky);
        refresh();
    }

    @Override boolean selectable() { return false; }
    @Override void focus(boolean holdsFocus, boolean animate) { }

    /** A label, a large figure, and two lines beside the figure. Returns {label, first line, second line}. */
    private TextView[] reading(GlassPanel g, String label, RollingText figure) {
        TextView caption = ui.text(ui.inter, 14, Ui.MIST);
        caption.setText(label);
        ui.place(g, caption, 24, 20);
        LinearLayout row = new LinearLayout(host);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(figure);
        LinearLayout lines = new LinearLayout(host);
        lines.setOrientation(LinearLayout.VERTICAL);
        TextView first = ui.text(ui.interMedium, 16, Ui.PORCELAIN), second = ui.text(ui.inter, 14, Ui.MIST);
        second.setPadding(0, ui.px(3), 0, 0);
        lines.addView(first);
        lines.addView(second);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
        ll.leftMargin = ui.px(16);
        row.addView(lines, ll);
        ui.place(g, row, 24, 60);
        return new TextView[]{caption, first, second};
    }

    /** A label, a value under it, a note under that. Returns {label, value, note}. */
    private TextView[] stacked(GlassPanel g, String label, float left) {
        TextView caption = ui.text(ui.inter, 14, Ui.MIST);
        caption.setText(label);
        ui.place(g, caption, left, 20);
        TextView value = ui.text(ui.manrope, 30, Ui.PORCELAIN);
        ui.place(g, value, left, 50);
        TextView note = ui.text(ui.inter, 14, Ui.MIST);
        ui.place(g, note, left, 96);
        return new TextView[]{caption, value, note};
    }

    @Override void refresh() {
        context.setText(Ui.date() + "  ·  " + Ui.time());
        Weather w = host.weather;
        boolean ready = w != null && w.ready();
        int aqi = ready ? w.air() : -1;
        city.setText(ready ? w.city : "Weather");
        temperature.set(ready ? Math.round(w.temp) + "°" : "–");
        condition.setText(ready ? w.describe() : "No reading yet");
        range.setText(ready ? Math.round(w.hi) + "° / " + Math.round(w.lo) + "°" : "Waiting for the network");   // short: the curve starts at 248 dp
        hourly.set(ready ? w.hourly : null, ready ? w.startHour : 0);
        airIndex.set(aqi < 0 ? "–" : String.valueOf(aqi));
        airName.setText(aqi < 0 ? "No reading yet" : Weather.AIR_NAME[Weather.airLevel(aqi)]);
        airNote.setText(aqi < 0 ? "" : Weather.AIR_NOTE[Weather.airLevel(aqi)]);
        airScale.set(aqi < 0 ? -1 : Weather.airLevel(aqi));

        SkyArc.Reading sky = skyArc.update(ready ? w.sunrise : "", ready ? w.sunset : "", ready ? w.lat : Double.NaN, ready ? w.lon : Double.NaN);
        skyCaption.setText(sky == null ? "Daylight" : sky.caption);
        skyValue.setText(sky == null ? "–" : sky.value);
        skyNote.setText(sky == null ? "No reading yet" : sky.note);

        long now = System.currentTimeMillis();
        double age = Moon.age(now), daysToFull = ((Moon.SYNODIC / 2 - age) % Moon.SYNODIC + Moon.SYNODIC) % Moon.SYNODIC;
        moon.setImageBitmap(Moon.render(host, ui.px(96), age));
        moonName.setText(Moon.name(age));
        moonNote.setText(Math.round(Moon.lit(age) * 100) + "% lit  ·  Full on "
                + new SimpleDateFormat("d MMMM", Locale.getDefault()).format(now + (long) (daysToFull * 86_400_000L)));
    }

    /** The next ten hours as a line: five readings, each with its temperature above and its hour below. */
    private static final class HourlyCurve extends View {
        private static final int POINTS = 5;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG), dot = new Paint(Paint.ANTI_ALIAS_FLAG),
                value = new Paint(Paint.ANTI_ALIAS_FLAG), hour = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final float dp;
        private float[] temps;
        private int startHour;

        HourlyCurve(Ui ui) {
            super(ui.context);
            dp = ui.dp;
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2 * dp);
            line.setColor(0x8ce9ecf2);
            line.setStrokeCap(Paint.Cap.ROUND);
            dot.setColor(Ui.PORCELAIN);
            value.setTypeface(ui.interMedium);
            value.setTextSize(15 * dp);
            value.setColor(Ui.PORCELAIN);
            value.setTextAlign(Paint.Align.CENTER);
            hour.setTypeface(ui.inter);
            hour.setTextSize(13 * dp);
            hour.setColor(Ui.MIST);
            hour.setTextAlign(Paint.Align.CENTER);
        }

        /** temps[i] is the temperature i hours after startHour. */
        void set(float[] temps, int startHour) { this.temps = temps; this.startHour = startHour; invalidate(); }

        private float at(int point) { return temps[Math.min(1 + 2 * point, temps.length - 1)]; }

        @Override protected void onDraw(Canvas c) {
            if (temps == null) return;
            float w = getWidth(), h = getHeight(), low = Float.MAX_VALUE, high = -Float.MAX_VALUE, column = w / POINTS, top = 30 * dp, bottom = h - 34 * dp;
            for (int i = 0; i < POINTS; i++) { low = Math.min(low, at(i)); high = Math.max(high, at(i)); }
            float[] xs = new float[POINTS], ys = new float[POINTS];
            for (int i = 0; i < POINTS; i++) {
                xs[i] = column * (i + 0.5f);
                ys[i] = high - low < 0.5f ? (top + bottom) / 2 : bottom - (at(i) - low) / (high - low) * (bottom - top);
            }
            path.reset();
            path.moveTo(xs[0], ys[0]);
            for (int i = 1; i < POINTS; i++) { float mid = (xs[i - 1] + xs[i]) / 2; path.cubicTo(mid, ys[i - 1], mid, ys[i], xs[i], ys[i]); }
            c.drawPath(path, line);
            for (int i = 0; i < POINTS; i++) {
                c.drawCircle(xs[i], ys[i], 3.5f * dp, dot);
                c.drawText(Math.round(at(i)) + "°", xs[i] + 2 * dp, ys[i] - 11 * dp, value);
                c.drawText(String.format(Locale.US, "%02d:00", (startHour + 1 + 2 * i) % 24), xs[i], h - 3 * dp, hour);
            }
        }
    }

    /** Six steps from good to hazardous; the one the air is in now is lit. Colour is a mark here, never text. */
    private static final class AirScale extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;
        private int level = -1;

        AirScale(Context c, float dp) { super(c); this.dp = dp; }

        void set(int level) { this.level = level; invalidate(); }

        @Override protected void onDraw(Canvas c) {
            float gap = 4 * dp, w = (getWidth() - 5 * gap) / 6f, h = getHeight();
            for (int i = 0; i < 6; i++) {
                p.setColor(i == level ? Ui.AIR[i] : 0x33e9ecf2);
                c.drawRoundRect(i * (w + gap), 0, i * (w + gap) + w, h, h / 2, h / 2, p);
            }
        }
    }
}
