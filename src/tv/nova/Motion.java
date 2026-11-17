package tv.nova;

import android.animation.TimeInterpolator;
import android.view.View;

/**
 * How things move (DESIGN.md, "Motion"): the spring curves, and the few small motions that
 * are shared across the interface. Every one answers something the viewer did or a real
 * change, plays once, and is skipped when Motion is set to Reduced.
 */
final class Motion {
    private Motion() {}

    /** Normalised damped spring; t in [0,1] maps to ~6 time constants so it lands at 1. */
    static TimeInterpolator spring(final float damping) {
        return new TimeInterpolator() {
            final double w = 12.0, z = damping, wd = w * Math.sqrt(Math.max(1e-4, 1 - z * z));
            public float getInterpolation(float t) {
                if (t >= 1f) return 1f;
                double e = Math.exp(-z * w * t);
                return (float) (1 - e * (Math.cos(wd * t) + (z * w / wd) * Math.sin(wd * t)));
            }
        };
    }

    static final TimeInterpolator FOCUS = spring(0.86f);   // focus: scale, lift
    static final TimeInterpolator GLIDE = spring(0.98f);   // large moves: pages, the hero, launch
    static final TimeInterpolator EASE = new TimeInterpolator() { // ease-out quint for fades
        public float getInterpolation(float t) { float u = 1 - t; return 1 - u * u * u * u * u; }
    };

    /** Set from Settings: every duration is shortened to 60 %. */
    static boolean reduced;
    static long ms(long base) { return reduced ? base * 6 / 10 : base; }

    /** OK was pressed: the view gives a little, the action fires, and the view springs back. */
    static void press(final View v, final Runnable action) {
        if (reduced) { action.run(); return; }
        // Posted, so it starts after any focus animation begun by the same key press and is not cancelled by it;
        // and the action is on a timer rather than an end-action, so it fires even if something else animates the view.
        v.post(() -> {
            final float sx = v.getScaleX(), sy = v.getScaleY();
            v.animate().scaleX(sx * 0.96f).scaleY(sy * 0.96f).setDuration(PRESS_MS).setInterpolator(EASE).start();
            v.postDelayed(() -> {
                action.run();
                v.animate().scaleX(sx).scaleY(sy).setDuration(260).setInterpolator(spring(0.6f)).start();   // the one place a little bounce is right
            }, PRESS_MS);
        });
    }
    private static final long PRESS_MS = 90;

    /** There is nothing further that way: the view leans a few pixels toward the edge and comes back. */
    static void nudge(final View v, float dx, float dy) {
        if (reduced) return;
        v.animate().translationX(dx).translationY(dy).setDuration(90).setInterpolator(EASE)
                .withEndAction(() -> v.animate().translationX(0).translationY(0).setDuration(300).setInterpolator(FOCUS).start()).start();
    }

    /** A page opens: this part of it rises into place, a beat after the row above it. */
    static void rise(final View v, int row, float distance) {
        v.animate().cancel();
        if (reduced) { v.setAlpha(1f); v.setTranslationY(0); return; }
        v.setAlpha(0f);
        v.setTranslationY(distance);
        v.animate().alpha(1f).translationY(0).setStartDelay(row * 40L).setDuration(420).setInterpolator(GLIDE)
                .setUpdateListener(a -> Room.redrawGlass(v))
                .withEndAction(() -> { v.animate().setStartDelay(0).setUpdateListener(null); Room.redrawGlass(v); }).start();
    }
}
