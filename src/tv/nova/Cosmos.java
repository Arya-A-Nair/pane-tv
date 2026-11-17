package tv.nova;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.SystemClock;
import android.view.View;
import java.util.Random;

/**
 * The living sky, in the visual language of a deep-space film: cold blue-black, a warm haze
 * where the sun sits beyond the planet, a faint infrared filament arcing out of it, and slow
 * drifting motes of pink and amber light. Always moving, never busy. Everything heavy is
 * pre-rendered once; each frame only moves sprites (cheap on the Fire TV GPU), at ~30 fps.
 */
final class Cosmos extends View {
    private static final int MOTES = 90;
    private final float dp;
    private Bitmap haze, ribbon, sprite;
    private final Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final float[] mx = new float[MOTES], my = new float[MOTES], vx = new float[MOTES], vy = new float[MOTES],
            ph = new float[MOTES], sz = new float[MOTES];
    private final int[] tint = new int[MOTES];
    private final Paint mote = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private long last;
    private boolean running;
    private float sunX = 0.08f, sunY = 0.30f; // where the sun glows from, in 0..1 screen space

    Cosmos(Context c, float dp) { super(c); this.dp = dp; }

    /** Put the sun's glow on the side of the planet the sun is actually on. */
    void setSun(float x, float y) { sunX = x; sunY = y; haze = null; ribbon = null; invalidate(); }

    void start() { if (!running) { running = true; last = 0; invalidate(); } }
    void stop() { running = false; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) { haze = null; ribbon = null; seed(w, h); }

    private void seed(int w, int h) {
        Random r = new Random(42);
        for (int i = 0; i < MOTES; i++) {
            mx[i] = r.nextFloat() * w;
            my[i] = r.nextFloat() * h * 0.75f;
            float speed = dp * (3 + r.nextFloat() * 9);
            double ang = Math.toRadians(-8 + r.nextFloat() * 16); // drifting with the filament, gently
            vx[i] = (float) Math.cos(ang) * speed;
            vy[i] = (float) Math.sin(ang) * speed * 0.6f;
            ph[i] = r.nextFloat() * 6.28f;
            sz[i] = dp * (r.nextFloat() < 0.1f ? 7 + r.nextFloat() * 6 : 2.5f + r.nextFloat() * 3.5f);
            float k = r.nextFloat();
            tint[i] = k < 0.55f ? 0xff6b8a : k < 0.9f ? 0xffb066 : 0xa8c4ff; // infrared pink, amber, a few cold blue
        }
    }

    private void build(int w, int h) {
        // haze: small and soft, scaled up by the GPU
        int hw = Math.max(64, w / 6), hh = Math.max(36, h / 6);
        haze = Bitmap.createBitmap(hw, hh, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(haze);
        Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
        q.setShader(new LinearGradient(0, 0, 0, hh, 0xff070912, 0xff090b10, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, hw, hh, q);
        float sx = sunX * hw, sy = sunY * hh;
        q.setShader(new RadialGradient(sx, sy, hw * 0.55f, new int[]{0x55f0a060, 0x22c8606a, 0x00090b10}, new float[]{0f, 0.35f, 1f}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, hw, hh, q);
        q.setShader(new RadialGradient(hw * 0.78f, hh * 0.18f, hw * 0.45f, new int[]{0x221c2c55, 0x00090b10}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, hw, hh, q);

        // the filament: a blurred arc from the sun out across the sky (software layer, done once)
        int rw = w / 2, rh = h / 2;
        ribbon = Bitmap.createBitmap(rw, rh, Bitmap.Config.ARGB_8888);
        Canvas rc = new Canvas(ribbon);
        Path path = new Path();
        float x0 = sunX * rw, y0 = sunY * rh;
        path.moveTo(x0, y0);
        path.cubicTo(rw * 0.35f, rh * 0.05f, rw * 0.7f, rh * 0.02f, rw * 1.05f, rh * 0.22f);
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setShader(new LinearGradient(x0, y0, rw, rh * 0.2f, new int[]{0xccff9a6a, 0x99ff5f86, 0x00ff5f86}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
        s.setMaskFilter(new BlurMaskFilter(rh * 0.05f, BlurMaskFilter.Blur.NORMAL));
        s.setStrokeWidth(rh * 0.05f);
        rc.drawPath(path, s);
        s.setMaskFilter(new BlurMaskFilter(rh * 0.012f, BlurMaskFilter.Blur.NORMAL));
        s.setStrokeWidth(rh * 0.006f);
        rc.drawPath(path, s);

        // one soft mote sprite, tinted per mote at draw time
        int sp = 32;
        sprite = Bitmap.createBitmap(sp, sp, Bitmap.Config.ARGB_8888);
        Canvas mc = new Canvas(sprite);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setShader(new RadialGradient(sp / 2f, sp / 2f, sp / 2f, new int[]{0xffffffff, 0x55ffffff, 0x00ffffff}, new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
        mc.drawCircle(sp / 2f, sp / 2f, sp / 2f, g);
    }

    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0) return;
        if (haze == null) build(w, h);
        long now = SystemClock.uptimeMillis();
        float dt = last == 0 ? 0.033f : Math.min(0.1f, (now - last) / 1000f);
        last = now;
        float t = now / 1000f;

        dst.set(0, 0, w, h);
        p.setAlpha(255);
        c.drawBitmap(haze, null, dst, p);

        // the filament breathes and drifts a little
        float breathe = 0.55f + 0.25f * (float) Math.sin(t * 0.35f);
        p.setAlpha((int) (255 * breathe));
        float drift = (float) Math.sin(t * 0.05f) * 10 * dp;
        dst.set(drift, drift * 0.4f, w + drift, h + drift * 0.4f);
        c.drawBitmap(ribbon, null, dst, p);

        // motes
        src.set(0, 0, sprite.getWidth(), sprite.getHeight());
        for (int i = 0; i < MOTES; i++) {
            mx[i] += vx[i] * dt;
            my[i] += vy[i] * dt + (float) Math.sin(t * 0.3f + ph[i]) * dp * 1.5f * dt;
            if (mx[i] > w + 20 * dp) { mx[i] = -20 * dp; }
            if (my[i] < -20 * dp) my[i] = h * 0.75f;
            if (my[i] > h * 0.8f) my[i] = 0;
            float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 0.9f + ph[i] * 3));
            mote.setColorFilter(new android.graphics.PorterDuffColorFilter(0xff000000 | tint[i], android.graphics.PorterDuff.Mode.MULTIPLY));
            mote.setAlpha((int) (200 * tw));
            float r = sz[i];
            dst.set(mx[i] - r, my[i] - r, mx[i] + r, my[i] + r);
            c.drawBitmap(sprite, src, dst, mote);
        }
        if (running) postInvalidateDelayed(33);
    }
}
