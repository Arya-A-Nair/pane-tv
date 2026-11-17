package tv.nova;

import android.animation.TimeInterpolator;

/** Spring curves. Damping just under critical: settles with no visible bounce. */
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

    static final TimeInterpolator FOCUS = spring(0.86f);   // dock/library focus
    static final TimeInterpolator GLIDE = spring(0.98f);   // large surfaces: panels, sheets
    static final TimeInterpolator EASE = new TimeInterpolator() { // ease-out quint for fades
        public float getInterpolation(float t) { float u = 1 - t; return 1 - u * u * u * u * u; }
    };

    /** Global motion scale: 1 = full, 0.55 = reduced (shorter, no parallax). */
    static float scale = 1f;
    static long ms(long base) { return (long) (base * (scale < 1f ? 0.6f : 1f)); }

    static int lerpColor(int a, int b, float t) {
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xff000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
