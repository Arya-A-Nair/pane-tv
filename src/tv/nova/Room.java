package tv.nova;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

/**
 * The room behind the interface: the environment, sharp on Home; its blurred, dimmed copy
 * for every other page; and the light — on Home the room is re-lit in the colour of the app
 * under focus (in the living smoke the colour spreads in from the side focus moved toward),
 * and glass is re-lit with it.
 *
 * An artwork environment is drawn alive (LivingSmoke). A photograph, the Reduced motion
 * setting, or a GPU that cannot keep up gets the still picture instead.
 */
final class Room implements LivingSmoke.Listener {
    private static final int FIRST_PHOTOGRAPH = 6;   // index in Env.IDS where the NASA photographs start
    private static final int DIM = 0xff8a8a8a;       // what the blurred room is multiplied by on pages other than Home

    private final ImageView sharp, dimmed;
    private final LivingSmoke smoke;
    private boolean smokeTooSlow, smokeAlive, watching;
    private int spreadFrom;   // 1 or -1 while the next relight should spread in from the right or left
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
        dimmed.setColorFilter(DIM, PorterDuff.Mode.MULTIPLY);
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

    /** Focus has moved along Home's row: the next change of light spreads in from that side rather than arriving everywhere. */
    void spreadNextFrom(boolean right) { spreadFrom = right ? 1 : -1; }

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
        int side = smokeAlive && watching && animate && target[3] > 0 ? spreadFrom : 0;
        spreadFrom = 0;
        smoke.light(target[0], target[1], target[2], target[3], side, !animate);
        if (relighting != null) relighting.cancel();
        final float[] from = light.clone();
        if (from[3] == 0) System.arraycopy(target, 0, from, 0, 3);   // fade the new colour in; do not pass through the old one
        if (from[3] == target[3] && from[0] == target[0] && from[1] == target[1] && from[2] == target[2]) return;
        relighting = ValueAnimator.ofFloat(0, 1).setDuration(!animate ? 0 : side != 0 ? 1400 : Motion.ms(700));   // glass follows the spread
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
        GlassPanel.grade = f;
        redrawGlass(glassRoot);
    }

    /**
     * Change the environment. It is decoded and blurred off the main thread; then, if the dimmed room is
     * what is on screen, the new one spreads in a soft circle from 'origin' (the card that was chosen),
     * and glass re-frosts once it has. 'then' runs when the new environment is in place.
     */
    void use(final int index, final View origin, final Runnable then) {
        final int w = sharp.getWidth(), h = sharp.getHeight();
        new Thread(() -> {
            final Bitmap[] prepared = Env.prepare(sharp.getContext(), index, w, h);
            if (prepared == null) return;
            ui.post(() -> {
                final Runnable settle = () -> {
                    sharp.setImageBitmap(prepared[0]);
                    sharp.setVisibility(View.VISIBLE);   // until the smoke has drawn the new picture, if it will
                    smokeAlive = false;
                    smoke.setVisibility(View.GONE);
                    dimmed.setImageBitmap(prepared[1]);
                    Env.adopt(index, prepared);
                    watch(watching);
                    redrawGlass(glassRoot);
                    then.run();
                };
                if (origin == null || dimmed.getAlpha() < 1f || Motion.reduced) { settle.run(); return; }
                final FrameLayout root = (FrameLayout) dimmed.getParent();
                final ImageView incoming = new ImageView(root.getContext());
                incoming.setScaleType(ImageView.ScaleType.FIT_XY);
                incoming.setImageBitmap(prepared[1]);
                incoming.setColorFilter(DIM, PorterDuff.Mode.MULTIPLY);
                root.addView(incoming, root.indexOfChild(dimmed) + 1, Ui.fill());
                int[] at = new int[2];
                origin.getLocationInWindow(at);
                int cx = at[0] + origin.getWidth() / 2, cy = at[1] + origin.getHeight() / 2;
                Animator spread = ViewAnimationUtils.createCircularReveal(incoming, cx, cy, origin.getHeight() / 2f, (float) Math.hypot(w, h));
                spread.setDuration(700);
                spread.setInterpolator(Motion.EASE);
                spread.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        settle.run();
                        root.removeView(incoming);
                    }
                });
                spread.start();
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
