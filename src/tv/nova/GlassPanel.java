package tv.nova;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;

/**
 * visionOS-style glass: the environment behind the panel, frosted (sampled from Env.frost at
 * the panel's own place on screen), a light veil, a sheen that is brighter at the top, a fine
 * specular rim, and a soft real shadow. Optionally lifts and brightens when it holds focus.
 */
class GlassPanel extends FrameLayout {
    final float dp;
    float radius;
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG),
            veil = new Paint(Paint.ANTI_ALIAS_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG),
            ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final Matrix m = new Matrix();
    private final int[] loc = new int[2];
    private float lit; // 0..1 focus highlight
    int veilColor = 0x14ffffff;

    GlassPanel(Context c, float dp, float radiusDp) {
        super(c);
        this.dp = dp;
        radius = radiusDp * dp;
        setWillNotDraw(false);
        setClipToPadding(false);
        setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius); o.setAlpha(0.6f); }
        });
        setElevation(16 * dp);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, dp * 0.7f));
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(2f, dp * 1.4f));
        ring.setColor(0xffffffff);
    }

    void setLit(float l) { lit = l; invalidate(); }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheen.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x2effffff, 0x0affffff, 0x05ffffff}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x80ffffff, 0x1fffffff, 0x0dffffff, 0x2effffff}, new float[]{0f, 0.25f, 0.7f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        r.set(0, 0, w, h);
        clip.reset();
        clip.addRoundRect(r, radius, radius, Path.Direction.CW);
        c.save();
        c.clipPath(clip);
        if (Env.frost != null) {
            // place the frosted environment so it lines up with the real one behind this panel
            getLocationOnScreen(loc);
            float sx = getScaleX() == 0 ? 1 : getScaleX(), sy = getScaleY() == 0 ? 1 : getScaleY();
            m.reset();
            m.setScale(Env.FROST_SCALE / sx, Env.FROST_SCALE / sy);
            m.postTranslate(-loc[0] / sx, -loc[1] / sy);
            c.drawBitmap(Env.frost, m, frost);
        }
        veil.setColor(veilColor);
        c.drawRect(r, veil);
        if (lit > 0) { veil.setColor(((int) (34 * lit) << 24) | 0xffffff); c.drawRect(r, veil); }
        c.drawRect(r, sheen);
        c.restore();
        float i = rim.getStrokeWidth() / 2;
        r.set(i, i, w - i, h - i);
        c.drawRoundRect(r, radius, radius, rim);
        if (lit > 0.01f) {
            ring.setAlpha((int) (255 * lit));
            float j = ring.getStrokeWidth() / 2;
            r.set(j, j, w - j, h - j);
            c.drawRoundRect(r, radius, radius, ring);
        }
    }
}
