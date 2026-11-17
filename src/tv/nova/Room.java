package tv.nova;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The room behind the interface: the environment, sharp on Home; its blurred, dimmed copy
 * for every other page; and the light — on Home the room is re-lit in the colour of the app
 * under focus, and glass is re-lit with it.
 *
 * An artwork environment is drawn alive (LivingSmoke). A photograph, the Reduced motion
 * setting, or a GPU that cannot keep up gets the still picture instead.
 */
final class Room implements LivingSmoke.Listener {
    private static final int FIRST_PHOTOGRAPH = 6;   // index in Env.IDS where the NASA photographs start

    private final ImageView sharp, dimmed;
    private final LivingSmoke smoke;
    private boolean smokeTooSlow, smokeAlive, watching;
    private final View glassRoot;
    private final Handler ui = new Handler();
    private final float[] light = {1, 1, 1, 0}, target = {1, 1, 1, 0};   // tint r, g, b and how much of it
    private ValueAnimator relighting;

    /** Adds its two layers to root. glassRoot is the tree whose glass is redrawn when the light changes. */
    Room(FrameLayout root, View glassRoot) {
        this.glassRoot = glassRoot;
        smoke = new LivingSmoke(root.getContext(), this);
        smoke.setVisibility(View.GONE);
        root.addView(smoke, Ui.fill());
        sharp = new ImageView(root.getContext());
        sharp.setScaleType(ImageView.ScaleType.CENTER_CROP);
        sharp.setImageBitmap(Env.photo);
        root.addView(sharp, Ui.fill());
        dimmed = new ImageView(root.getContext());
        dimmed.setScaleType(ImageView.ScaleType.FIT_XY);
        dimmed.setImageBitmap(Env.frost);
        dimmed.setColorFilter(0xff8a8a8a, PorterDuff.Mode.MULTIPLY);
        dimmed.setAlpha(0f);
        root.addView(dimmed, Ui.fill());
    }

    /**
     * Tell the room whether the sharp environment can be seen (Home or the screensaver, in the foreground).
     * The smoke only moves while it can, and this is also where a changed setting or picture takes effect.
     */
    void watch(boolean visible) {
        watching = visible;
        boolean living = Env.index < FIRST_PHOTOGRAPH && !Motion.reduced && !smokeTooSlow;
        if (living && smoke.getVisibility() != View.VISIBLE) {
            smoke.setPicture(Env.photo);
            smoke.setVisibility(View.VISIBLE);   // the still stays on top until onAlive
        } else if (!living && smoke.getVisibility() == View.VISIBLE) {
            sharp.setVisibility(View.VISIBLE);
            smoke.setVisibility(View.GONE);
            smokeAlive = false;
        }
        if (living && visible) smoke.start(); else smoke.stop();
    }

    void pause() { smoke.stop(); smoke.onPause(); }
    void resume() { smoke.onResume(); watch(watching); }

    @Override public void onAlive() {
        smokeAlive = true;
        if (smoke.getVisibility() == View.VISIBLE) sharp.setVisibility(View.INVISIBLE);
    }

    @Override public void onTooSlow() {
        smokeTooSlow = true;
        watch(watching);
    }

    /** Light enters from the side focus moved toward and passes through the smoke, in the app's colour. */
    void sweep(boolean fromRight, int accent) {
        if (!smokeAlive || !watching) return;
        float[] c = lightOf(accent);
        smoke.sweep(fromRight, c[0], c[1], c[2]);
    }

    /** Away from Home the environment falls out of focus and dims; glass keeps its light. */
    void dim(boolean on, long ms) { dimmed.animate().alpha(on ? 1f : 0f).setDuration(ms).setInterpolator(Motion.EASE).start(); }

    /** Light the room in an app's colour, or (null) let it return to its own. */
    void relight(Integer accent, boolean animate) {
        // A photograph keeps its own colour: a pink Earth is wrong.
        if (accent == null || Env.index >= FIRST_PHOTOGRAPH) target[3] = 0;
        else {
            System.arraycopy(lightOf(accent), 0, target, 0, 3);
            target[3] = 0.66f;
        }
        if (relighting != null) relighting.cancel();
        final float[] from = light.clone();
        if (from[3] == 0) System.arraycopy(target, 0, from, 0, 3);   // fade the new colour in; do not pass through the old one
        if (from[3] == target[3] && from[0] == target[0] && from[1] == target[1] && from[2] == target[2]) return;
        relighting = ValueAnimator.ofFloat(0, 1).setDuration(animate ? Motion.ms(700) : 0);
        relighting.setInterpolator(Motion.EASE);
        relighting.addUpdateListener(a -> {
            float t = (Float) a.getAnimatedValue();
            for (int i = 0; i < 4; i++) light[i] = from[i] + (target[i] - from[i]) * t;
            apply();
        });
        relighting.start();
    }

    /** An app's accent as a light: halfway between "same brightness" (which turns red to pink) and the pure colour (which is deep). */
    private static float[] lightOf(int accent) {
        float[] hsv = new float[3];
        Color.colorToHSV(accent, hsv);
        hsv[1] = Math.min(hsv[1], 0.75f);
        hsv[2] = 1f;
        int c = Color.HSVToColor(hsv);
        float r = Color.red(c) / 255f, g = Color.green(c) / 255f, b = Color.blue(c) / 255f, luma = 0.299f * r + 0.587f * g + 0.114f * b;
        return new float[]{(r / luma + r) / 2, (g / luma + g) / 2, (b / luma + b) / 2};
    }

    /** Keep each pixel's brightness, replace part of its colour with the light's. */
    private void apply() {
        float amount = light[3];
        ColorFilter f = null;
        if (amount > 0.004f) {
            float[] m = new float[20];
            for (int c = 0; c < 3; c++) {
                m[c * 5] = amount * light[c] * 0.299f;
                m[c * 5 + 1] = amount * light[c] * 0.587f;
                m[c * 5 + 2] = amount * light[c] * 0.114f;
                m[c * 5 + c] += 1 - amount;
            }
            m[18] = 1;
            f = new ColorMatrixColorFilter(m);
        }
        sharp.setColorFilter(f);
        smoke.setTint(light[0], light[1], light[2], amount);
        GlassPanel.grade = f;
        redrawGlass(glassRoot);
    }

    /** Change the environment. Decoding and blurring happen off the main thread; then is run when it is on screen. */
    void use(final int index, final Runnable then) {
        final int w = sharp.getWidth(), h = sharp.getHeight();
        new Thread(() -> {
            Env.load(sharp.getContext(), index, w, h);
            ui.post(() -> {
                sharp.setImageBitmap(Env.photo);
                sharp.setVisibility(View.VISIBLE);   // until the smoke has drawn the new picture, if it will
                smokeAlive = false;
                smoke.setVisibility(View.GONE);
                dimmed.setImageBitmap(Env.frost);
                watch(watching);
                redrawGlass(glassRoot);
                then.run();
            });
        }, "environment").start();
    }

    /** The screensaver lets the picture drift slowly inward. */
    void drift(boolean on) {
        smoke.zoom(on);
        sharp.animate().cancel();
        if (on) sharp.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180_000).setInterpolator(new android.view.animation.LinearInterpolator()).start();
        else sharp.animate().scaleX(1f).scaleY(1f).setDuration(Motion.ms(900)).setInterpolator(Motion.GLIDE).start();
    }

    /** Glass samples the environment at its own place on screen, so it must be redrawn when either changes. */
    static void redrawGlass(View v) {
        if (v instanceof GlassPanel) v.invalidate();
        if (v instanceof ViewGroup) for (int i = 0; i < ((ViewGroup) v).getChildCount(); i++) redrawGlass(((ViewGroup) v).getChildAt(i));
    }
}
