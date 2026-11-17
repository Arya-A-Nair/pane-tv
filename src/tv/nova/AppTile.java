package tv.nova;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A round app icon with its name. Under focus it comes forward, gains a Porcelain ring, and
 * its name brightens. With no app it is the "All apps" tile.
 */
final class AppTile {
    /** Room round the disc for the focus ring (dp). */
    static final float RING_ROOM = 10;

    final Apps.App app;
    final LinearLayout view;
    final View disc;
    final TextView label;
    private final Ui ui;
    private boolean focused;
    private float shine = 1f;   // 0..1 as a highlight crosses the icon; 1 = gone

    AppTile(final Ui ui, Apps.App a, float discDp, float cellDp) {
        this.ui = ui;
        app = a;
        view = new LinearLayout(ui.context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setClipChildren(false);
        disc = new View(ui.context) {
            final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            final RectF r = new RectF();
            final Paint glint = new Paint(Paint.ANTI_ALIAS_FLAG);
            final Path circle = new Path();
            @Override protected void onDraw(Canvas c) {
                float dp = ui.dp, pad = RING_ROOM * dp, s = getWidth() - 2 * pad, mid = getWidth() / 2f, rad = s / 2f;
                p.setStyle(Paint.Style.FILL);
                if (app != null) {
                    r.set(pad, pad, pad + s, pad + s);
                    p.setColor(0xffffffff);
                    c.drawBitmap(app.round, null, r, p);
                } else {
                    p.setColor(0x24e9ecf2);
                    c.drawCircle(mid, mid, rad, p);
                    p.setColor(Ui.PORCELAIN);
                    float q = s * 0.105f, g = s * 0.07f;   // a 2 x 2 grid
                    for (int i = 0; i < 4; i++) {
                        float x = mid - g / 2 - q + (i % 2) * (q + g), y = mid - g / 2 - q + (i / 2) * (q + g);
                        c.drawRoundRect(x, y, x + q, y + q, q * 0.3f, q * 0.3f, p);
                    }
                }
                if (shine < 1f && app != null) {   // a soft diagonal band of light, clipped to the icon
                    float at = pad + s * (-0.5f + 2f * shine), half = s * 0.42f;
                    glint.setShader(new LinearGradient(at - half, at - half, at + half, at + half, new int[]{0x00ffffff, 0x3dffffff, 0x00ffffff}, null, Shader.TileMode.CLAMP));
                    circle.rewind();
                    circle.addCircle(mid, mid, rad, Path.Direction.CW);
                    c.save();
                    c.clipPath(circle);
                    c.drawRect(pad, pad, pad + s, pad + s, glint);
                    c.restore();
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(1.5f, dp * 0.75f));   // hairline that seats the icon on the dark
                p.setColor(0x33ffffff);
                c.drawCircle(mid, mid, rad - p.getStrokeWidth() / 2, p);
                if (focused) {
                    p.setStrokeWidth(2.5f * dp);
                    p.setColor(Ui.PORCELAIN);
                    c.drawCircle(mid, mid, rad + 4f * dp, p);
                }
            }
        };
        disc.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { int pad = ui.px(RING_ROOM); o.setOval(pad, pad, v.getWidth() - pad, v.getHeight() - pad); }
        });
        disc.setElevation(a == null ? 0 : ui.px(6));   // a translucent disc would show its own shadow through itself
        int box = ui.px(discDp + 2 * RING_ROOM);
        view.addView(disc, new LinearLayout.LayoutParams(box, box));
        label = ui.text(ui.interMedium, 14, Ui.MIST);
        ui.shadow(label);
        label.setMaxLines(2);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setText(a == null ? "All apps" : a.label);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(ui.px(cellDp - 6), -2);
        ll.topMargin = ui.px(4);
        view.addView(label, ll);
        view.setLayoutParams(new LinearLayout.LayoutParams(ui.px(cellDp), -2));
    }

    /** travel: -1 or 1 when focus arrived by moving left or right, 0 otherwise. */
    void focus(boolean on, boolean animate, int travel) {
        boolean gained = on && !focused && animate && !Motion.reduced;
        focused = on;
        if (gained) {
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f).setDuration(450);
            a.addUpdateListener(x -> { shine = (Float) x.getAnimatedValue(); disc.invalidate(); });
            a.start();
            disc.setRotationY(3f * travel);   // leans the way it was approached, then settles flat
            disc.animate().rotationY(0f);
        }
        disc.invalidate();
        label.setTextColor(on ? Ui.PORCELAIN : Ui.MIST);
        long ms = animate ? Motion.ms(300) : 0;
        disc.animate().scaleX(on ? 1.1f : 1f).scaleY(on ? 1.1f : 1f).translationZ(on && app != null ? ui.px(14) : 0)
                .setDuration(ms).setInterpolator(Motion.FOCUS).start();
        label.animate().translationY(on ? ui.px(7) : 0).setDuration(ms).setInterpolator(Motion.FOCUS).start();
    }

    /** A small Glacier dot before the name says "this one is on Home". */
    void markOnHome(boolean on) {
        if (!on) { label.setText(app.label); return; }
        SpannableString s = new SpannableString("●  " + app.label);
        s.setSpan(new ForegroundColorSpan(Ui.GLACIER), 0, 1, 0);
        s.setSpan(new RelativeSizeSpan(0.6f), 0, 1, 0);
        label.setText(s);
    }
}
