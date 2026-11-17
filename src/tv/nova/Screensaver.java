package tv.nova;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * What is left when nobody has touched the remote for a while: the environment as the
 * picture, the time set large at the bottom left, and one line with the date and weather.
 */
final class Screensaver {
    final FrameLayout view;
    private final Clock clock;
    private final TextView line;

    Screensaver(Ui ui) {
        view = new FrameLayout(ui.context);
        view.setAlpha(0f);
        clock = new Clock(ui, 132);
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        cl.setMargins(ui.px(Ui.MARGIN - 6), 0, 0, ui.px(Ui.BOTTOM + 34));
        view.addView(clock, cl);
        line = ui.text(ui.inter, 16, Ui.PORCELAIN);
        ui.shadow(line);
        FrameLayout.LayoutParams ll = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM);
        ll.setMargins(ui.px(Ui.MARGIN), 0, 0, ui.px(Ui.BOTTOM));
        view.addView(line, ll);
    }

    void show(String weatherSummary) {
        clock.set(Ui.time());
        line.setText(Ui.date() + (weatherSummary.isEmpty() ? "" : "   ·   " + weatherSummary));
    }

    /** The time in Manrope Light, drawn by hand so the view's top edge is the top of the digits and the colon can be Champagne. */
    private static final class Clock extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Rect digit = new Rect();
        private final float dp;
        private String time = "";

        Clock(Ui ui, float sp) {
            super(ui.context);
            dp = ui.dp;
            p.setTypeface(ui.manrope);
            p.setTextSize(sp * dp);
            p.setLetterSpacing(-0.02f);
            p.setShadowLayer(14 * dp, 0, 2 * dp, 0x55000000);
            p.getTextBounds("0", 0, 1, digit);
        }

        void set(String s) { if (!s.equals(time)) { time = s; requestLayout(); invalidate(); } }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension((int) (p.measureText(time) + 12 * dp), digit.height() + (int) (10 * dp)); }

        @Override protected void onDraw(Canvas c) {
            int colon = time.indexOf(':');
            if (colon < 0) return;
            String hours = time.substring(0, colon), minutes = time.substring(colon + 1);
            float x = 4 * dp, y = digit.height() + 2 * dp;
            p.setColor(Ui.PORCELAIN);
            c.drawText(hours, x, y, p);
            x += p.measureText(hours);
            p.setColor(Ui.CHAMPAGNE_LIGHT);                        // the one warm mark in the composition
            c.drawText(":", x, y - digit.height() * 0.07f, p);     // lifted to sit centred on the digits
            x += p.measureText(":");
            p.setColor(Ui.PORCELAIN);
            c.drawText(minutes, x, y, p);
        }
    }
}
