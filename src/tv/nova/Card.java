package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;

/** An app banner. Barely rounded, no border; focus is expressed by scale, lift and light. */
final class Card extends View {
    final Apps.App app;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final float radius;
    boolean marked; // favourite marker in the library
    private boolean ringed;
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** A clear porcelain ring around the selected card: unmistakable from across the room. */
    void ring(boolean on) {
        if (ringed == on) return;
        ringed = on;
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setColor(0xffe9ecf2);
        invalidate();
    }

    Card(Context ctx, Apps.App app, float radius) {
        super(ctx);
        this.app = app;
        this.radius = radius;
        paint.setShader(new BitmapShader(app.banner, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        dot.setColor(0xffb4a28c); // Champagne
        setOutlineProvider(new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Card.this.radius); }
        });
        setClipToOutline(true);
        setLayerType(LAYER_TYPE_NONE, null);
    }

    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float depthSet = -1;
    boolean glassRim;

    /** Further round the orbit: less colour, less light. 1 = right in front. */
    void depth(float d) {
        if (Math.abs(d - depthSet) < 0.02f) return;
        depthSet = d;
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        m.setSaturation(0.25f + 0.75f * d * d);
        float k = 0.45f + 0.55f * d;
        android.graphics.ColorMatrix dim = new android.graphics.ColorMatrix(new float[]{k,0,0,0,0, 0,k,0,0,0, 0,0,k,0,0, 0,0,0,1,0});
        m.postConcat(dim);
        paint.setColorFilter(new android.graphics.ColorMatrixColorFilter(m));
        invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, h * 0.008f));
        rim.setShader(new android.graphics.LinearGradient(0, 0, 0, h, new int[]{0x8cffffff, 0x1affffff, 0x0dffffff, 0x26ffffff},
                new float[]{0f, 0.3f, 0.75f, 1f}, Shader.TileMode.CLAMP));
        Matrix m = new Matrix();
        Bitmap b = app.banner;
        m.setScale((float) w / b.getWidth(), (float) h / b.getHeight());
        paint.getShader().setLocalMatrix(m);
    }

    @Override protected void onDraw(Canvas c) {
        r.set(0, 0, getWidth(), getHeight());
        c.drawRoundRect(r, radius, radius, paint);
        if (glassRim) {
            float w2 = rim.getStrokeWidth() / 2;
            r.set(w2, w2, getWidth() - w2, getHeight() - w2);
            c.drawRoundRect(r, radius, radius, rim);
            r.set(0, 0, getWidth(), getHeight());
        }
        if (ringed) {
            float w = Math.max(2f, getHeight() * 0.03f);
            ringPaint.setStrokeWidth(w);
            r.inset(w / 2, w / 2);
            c.drawRoundRect(r, radius, radius, ringPaint);
        }
        if (marked) {
            float s = getHeight() * 0.045f;
            c.drawCircle(getWidth() - s * 3, s * 3, s, dot);
        }
    }
}
