package tv.nova;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.View;

/**
 * A figure that changes — the time, a temperature, an index. When it does, only the
 * characters that changed roll: the old one slides up and fades, the new one rises in.
 * The view is exactly as tall as its digits (plus a little room), so it can be placed by
 * the top of the digits rather than by a line box.
 */
final class RollingText extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect digit = new Rect();
    private final float padTop, padBottom, padSide;
    private String text = "", before = "";
    private float roll = 1f;         // 0 = showing 'before', 1 = showing 'text'
    private char accent;
    private int color, accentColor;
    private ValueAnimator rolling;

    RollingText(Ui ui, Typeface face, float sp, int color) {
        super(ui.context);
        this.color = color;
        paint.setTypeface(face);
        paint.setTextSize(sp * ui.dp);
        paint.getTextBounds("0", 0, 1, digit);
        padTop = 2 * ui.dp;
        padBottom = 4 * ui.dp;
        padSide = sp * ui.dp * 0.04f;
    }

    /** For text standing directly on the environment. */
    RollingText shadowed(float radius) { paint.setShadowLayer(radius, 0, radius / 7, 0x55000000); return this; }

    /** Draw one character (the colon of a clock) in another colour, lifted to sit centred on the digits. */
    RollingText accent(char c, int color) { accent = c; accentColor = color; return this; }

    void set(String next) {
        if (next.equals(text)) return;
        boolean sameShape = next.length() == text.length() && isShown() && !Motion.reduced;
        before = sameShape ? text : next;
        text = next;
        if (rolling != null) rolling.cancel();
        roll = 1f;
        if (sameShape) {
            rolling = ValueAnimator.ofFloat(0f, 1f).setDuration(380);
            rolling.setInterpolator(Motion.GLIDE);
            rolling.addUpdateListener(a -> { roll = (Float) a.getAnimatedValue(); invalidate(); });
            rolling.start();
        }
        requestLayout();
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension((int) Math.ceil(paint.measureText(text) + 2 * padSide), (int) Math.ceil(digit.height() + padTop + padBottom));
    }

    @Override protected void onDraw(Canvas c) {
        float x = padSide, baseline = padTop + digit.height(), travel = digit.height() * 0.45f;
        for (int i = 0; i < text.length(); i++) {
            char now = text.charAt(i), was = i < before.length() ? before.charAt(i) : now;
            if (now == was || roll >= 1f) draw(c, now, x, baseline, 1f);
            else {
                draw(c, was, x, baseline - travel * roll, 1f - roll);
                draw(c, now, x, baseline + travel * (1f - roll), roll);
            }
            x += paint.measureText(text, i, i + 1);
        }
    }

    private void draw(Canvas c, char ch, float x, float y, float alpha) {
        boolean special = ch == accent;
        paint.setColor(special ? accentColor : color);
        paint.setAlpha((int) (255 * alpha));
        c.drawText(String.valueOf(ch), x, special ? y - digit.height() * 0.07f : y, paint);
    }
}
