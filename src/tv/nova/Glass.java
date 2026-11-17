package tv.nova;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Spatial material: a smoked translucent body with a specular rim that catches light
 * along the top edge and fades down the sides. No blur API on Fire OS 6, so the
 * environment behind is kept low-frequency and the glass reads as frosted.
 */
final class Glass extends Drawable {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    final float radius;
    private final float stroke;
    private int tint;

    /** Body tints */
    static final int WINDOW = 0x6b1d2129, PANE = 0x2ee9ecf2, ORNAMENT = 0x80232831, CHIP = 0x1fe9ecf2, SELECTED = 0xf2e9ecf2;

    Glass(float radius, int tint, float dp) {
        this.radius = radius;
        this.stroke = Math.max(1f, dp * 0.75f);
        this.tint = tint;
        fill.setColor(tint);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(stroke);
    }

    void tintTo(int c) { tint = c; fill.setColor(c); invalidateSelf(); }
    int tint() { return tint; }

    @Override protected void onBoundsChange(Rect b) {
        r.set(b.left + stroke / 2, b.top + stroke / 2, b.right - stroke / 2, b.bottom - stroke / 2);
        rim.setShader(new LinearGradient(0, b.top, 0, b.bottom,
                new int[]{0x55ffffff, 0x14ffffff, 0x08ffffff, 0x1cffffff}, new float[]{0f, 0.28f, 0.75f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        c.drawRoundRect(r, radius, radius, fill);
        c.drawRoundRect(r, radius, radius, rim);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }

    /** Give a view this material plus a soft, real shadow. */
    static Glass apply(View v, float radius, int tint, float dp, float elevationDp) {
        final Glass g = new Glass(radius, tint, dp);
        v.setBackground(g);
        v.setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View view, Outline o) { o.setRoundRect(0, 0, view.getWidth(), view.getHeight(), g.radius); o.setAlpha(0.55f); }
        });
        v.setElevation(elevationDp * dp);
        return g;
    }
}
