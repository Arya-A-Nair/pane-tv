package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import java.util.Random;

/**
 * Glass (DESIGN.md, "Materials"): the frosted environment behind the panel, sampled at the
 * panel's own place on screen; a faint light from above; fine grain; a hairline rim that is
 * brightest along the top edge; a soft shadow.
 */
class GlassPanel extends FrameLayout {
    final float dp;
    float radius;
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG), sheen = new Paint(Paint.ANTI_ALIAS_FLAG),
            grain = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Matrix m = new Matrix();
    private final int[] loc = new int[2];
    private Bitmap shaded;      // the frost bitmap our shader was built for
    private static Bitmap grainTile;
    /** Colour grade shared by all glass, so it follows the environment when Home re-lights it for an app. */
    static android.graphics.ColorFilter grade;

    GlassPanel(Context c, float dp, float radiusDp) {
        super(c);
        this.dp = dp;
        radius = radiusDp * dp;
        setWillNotDraw(false);
        setClipToPadding(false);
        setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(radius, v.getHeight() / 2f)); o.setAlpha(0.55f); }
        });
        setElevation(10 * dp);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1.5f, dp * 0.75f));
        if (grainTile == null) {
            int n = 96;
            int[] px = new int[n * n];
            Random rnd = new Random(7);
            for (int i = 0; i < px.length; i++) px[i] = (rnd.nextInt(18) << 24) | (rnd.nextBoolean() ? 0xffffff : 0x000000);
            grainTile = Bitmap.createBitmap(px, n, n, Bitmap.Config.ARGB_8888);
        }
        grain.setShader(new BitmapShader(grainTile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheen.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x0cffffff, 0x00ffffff}, new float[]{0f, 0.55f}, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, new int[]{0x70ffffff, 0x1cffffff, 0x10ffffff, 0x26ffffff}, new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        if (Env.frost == null) return;
        float w = getWidth(), h = getHeight(), rad = Math.min(radius, h / 2f);
        if (shaded != Env.frost) { shaded = Env.frost; frost.setShader(new BitmapShader(shaded, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)); }
        // line the frost up with the real environment behind this panel
        getLocationOnScreen(loc);
        float sx = getScaleX() == 0 ? 1 : getScaleX(), sy = getScaleY() == 0 ? 1 : getScaleY();
        m.reset();
        m.setScale(Env.FROST_SCALE / sx, Env.FROST_SCALE / sy);
        m.postTranslate(-loc[0] / sx, -loc[1] / sy);
        frost.getShader().setLocalMatrix(m);
        frost.setColorFilter(grade);
        r.set(0, 0, w, h);
        c.drawRoundRect(r, rad, rad, frost);
        c.drawRoundRect(r, rad, rad, sheen);
        c.drawRoundRect(r, rad, rad, grain);
        float i = rim.getStrokeWidth() / 2;
        r.set(i, i, w - i, h - i);
        c.drawRoundRect(r, rad - i, rad - i, rim);
    }
}
