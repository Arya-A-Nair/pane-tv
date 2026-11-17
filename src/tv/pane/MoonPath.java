package tv.pane;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Where the moon is in the sky, worked out on the device with the standard low-precision
 * lunar series (the largest terms of Meeus, "Astronomical Algorithms", ch. 47). Good to a
 * fraction of a degree, which is a few minutes at the horizon. No network, no tables.
 */
final class MoonPath {
    private MoonPath() {}

    private static final double RAD = Math.PI / 180, DAY_MS = 86_400_000.0;
    private static final double OBLIQUITY = RAD * 23.4397;
    /** The moon counts as risen above this altitude: refraction and parallax lift it by about 0.133 degrees. */
    private static final double HORIZON = RAD * 0.133;
    private static final long STEP_MS = 10 * 60_000L, REACH_MS = 16 * 3_600_000L;
    /** How far the self-check lets this series stray from the ephemeris; the truncated series is a few minutes late at moonset. */
    private static final long TOLERANCE_MIN = 8;

    /** The moon's altitude above the horizon, in radians, for a place given in degrees (east and north positive). */
    static double altitude(long utcMillis, double latitude, double longitude) {
        double d = utcMillis / DAY_MS - 0.5 + 2440588 - 2451545;                 // days since J2000
        double meanLongitude = RAD * (218.316 + 13.176396 * d);
        double anomaly = RAD * (134.963 + 13.064993 * d);          // the moon's mean anomaly
        double node = RAD * (93.272 + 13.229350 * d);              // mean distance from its ascending node
        double elongation = RAD * (297.850 + 12.190749 * d);       // mean angle from the sun
        double sunAnomaly = RAD * (357.529 + 0.98560028 * d);
        // ecliptic longitude and latitude: the equation of the centre, then evection, variation and the annual equation
        double lon = meanLongitude + RAD * (6.289 * Math.sin(anomaly) + 1.274 * Math.sin(2 * elongation - anomaly)
                + 0.658 * Math.sin(2 * elongation) + 0.214 * Math.sin(2 * anomaly) - 0.186 * Math.sin(sunAnomaly));
        double lat = RAD * (5.128 * Math.sin(node) + 0.280 * Math.sin(anomaly + node) + 0.277 * Math.sin(anomaly - node)
                + 0.173 * Math.sin(2 * elongation - node));
        double rightAscension = Math.atan2(Math.sin(lon) * Math.cos(OBLIQUITY) - Math.tan(lat) * Math.sin(OBLIQUITY), Math.cos(lon));
        double declination = Math.asin(Math.sin(lat) * Math.cos(OBLIQUITY) + Math.cos(lat) * Math.sin(OBLIQUITY) * Math.sin(lon));
        double siderealTime = RAD * (280.16 + 360.9856235 * d) + RAD * longitude;
        double hourAngle = siderealTime - rightAscension, phi = RAD * latitude;
        return Math.asin(Math.sin(phi) * Math.sin(declination) + Math.cos(phi) * Math.cos(declination) * Math.cos(hourAngle));
    }

    /**
     * The moon's time above the horizon that matters between two instants (a night): {moonrise, moonset}
     * in UTC milliseconds. That is the pass still ahead or under way at 'now'; failing that, one already
     * over that night; or null if the moon is not up at any point in it.
     */
    static long[] pass(long from, long to, long now, double latitude, double longitude) {
        long[] over = null;
        long rise = Long.MIN_VALUE;
        double before = altitude(from - REACH_MS, latitude, longitude) - HORIZON;
        for (long t = from - REACH_MS + STEP_MS; t <= to + REACH_MS; t += STEP_MS) {
            double after = altitude(t, latitude, longitude) - HORIZON;
            if (before < 0 != after < 0) {
                long crossing = t - STEP_MS + Math.round(STEP_MS * (before / (before - after)));   // straight line between the two samples
                if (after >= 0) rise = crossing;
                else if (rise != Long.MIN_VALUE && rise < to && crossing > from) {
                    if (crossing > now) return new long[]{rise, crossing};
                    over = new long[]{rise, crossing};
                }
            }
            before = after;
        }
        return over;
    }

    /**
     * Self-check: java -cp out/classes tv.pane.MoonPath
     * Compares passes for a site at 20 N, 0 E (times in UTC) with moonrise and
     * moonset from PyEphem, a full ephemeris, using the same horizon altitude as above.
     */
    public static void main(String[] args) throws Exception {
        double latitude = 20, longitude = 0;
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));
        String[][] expected = {   // night from, night to, moonrise, moonset
                {"2026-11-17 17:20", "2026-11-18 06:11", "2026-11-17 12:29", "2026-11-18 00:05"},   // first quarter
                {"2026-11-24 17:19", "2026-11-25 06:15", "2026-11-24 17:13", "2026-11-25 07:01"},   // full
                {"2026-06-10 18:39", "2026-06-11 05:20", "2026-06-11 01:50", "2026-06-11 14:50"},   // waning crescent
                {"2025-01-13 17:40", "2025-01-14 06:38", "2025-01-13 17:25", "2025-01-14 07:05"}};  // full
        long worst = 0;
        for (String[] e : expected) {
            long from = utc.parse(e[0]).getTime();
            long[] pass = pass(from, utc.parse(e[1]).getTime(), from, latitude, longitude);
            if (pass == null) throw new AssertionError("no moon found for the night of " + e[0]);
            long riseOff = Math.abs(pass[0] - utc.parse(e[2]).getTime()) / 60_000, setOff = Math.abs(pass[1] - utc.parse(e[3]).getTime()) / 60_000;
            System.out.println(e[0] + "  rise " + utc.format(pass[0]) + " (" + riseOff + " min off)  set " + utc.format(pass[1]) + " (" + setOff + " min off)");
            worst = Math.max(worst, Math.max(riseOff, setOff));
        }
        if (worst > TOLERANCE_MIN) throw new AssertionError("moonrise or moonset is " + worst + " minutes from the ephemeris; expected within " + TOLERANCE_MIN);
        System.out.println("ok: within " + worst + " minutes");
    }
}
