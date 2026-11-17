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

    static final TimeInterpolator FOCUS = spring(0.86f);   // focus: scale, lift
    static final TimeInterpolator GLIDE = spring(0.98f);   // large moves: pages, the hero, launch
    static final TimeInterpolator EASE = new TimeInterpolator() { // ease-out quint for fades
        public float getInterpolation(float t) { float u = 1 - t; return 1 - u * u * u * u * u; }
    };

    /** Set from Settings: every duration is shortened to 60 %. */
    static boolean reduced;
    static long ms(long base) { return reduced ? base * 6 / 10 : base; }
}
