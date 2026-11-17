package tv.pane;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Real local weather: approximate location from the TV's public IP (ip-api.com), forecast
 * from Open-Meteo (no key). Refreshed every 30 minutes; last result cached for instant start.
 */
final class Weather {
    interface Listener { void onWeather(Weather w); }

    private static final long REFRESH_MS = 30 * 60_000L;
    private static final int TIMEOUT_MS = 8000;

    volatile String city = "";   // written on the fetch thread, read on the UI thread
    /** Where the reading is for, in degrees; NaN until the first reading. Used to find the moon. */
    volatile double lat = Double.NaN, lon = Double.NaN;
    volatile float temp, hi, lo;
    volatile int code, aqi = -1;
    volatile String sunrise = "", sunset = "";
    volatile boolean day = true, ok;
    volatile float[] hourly = new float[12];
    volatile int startHour;

    private final SharedPreferences prefs;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());

    Weather(Context c, Listener l) {
        prefs = c.getSharedPreferences(Pane.PREFS, Context.MODE_PRIVATE);
        listener = l;
        String cached = prefs.getString("weather", null);
        if (cached != null) try { parse(new JSONObject(cached)); ok = true; } catch (Exception stale) { }   // unreadable cache: wait for a fresh reading
        refresh.run();
    }

    private final Runnable refresh = new Runnable() {
        public void run() {
            new Thread(Weather.this::fetch, "pane-weather").start();
            main.postDelayed(this, REFRESH_MS);
        }
    };

    /** Stop refreshing; the screen that owns this is going away. */
    void stop() { main.removeCallbacksAndMessages(null); }

    private void fetch() {
        try {
            // plain http: ip-api.com serves its free tier over http only
            JSONObject loc = new JSONObject(get("http://ip-api.com/json/?fields=status,city,lat,lon"));
            if (!"success".equals(loc.optString("status"))) return;
            String url = String.format(Locale.US, "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f"
                    + "&current=temperature_2m,weather_code,is_day&hourly=temperature_2m"
                    + "&daily=temperature_2m_max,temperature_2m_min,sunrise,sunset&forecast_days=2&timezone=auto" + (fahrenheit() ? "&temperature_unit=fahrenheit" : ""),
                    loc.getDouble("lat"), loc.getDouble("lon"));
            JSONObject f = new JSONObject(get(url));
            try {
                JSONObject aq = new JSONObject(get(String.format(Locale.US, "https://air-quality-api.open-meteo.com/v1/air-quality?latitude=%.3f&longitude=%.3f&current=us_aqi", loc.getDouble("lat"), loc.getDouble("lon"))));
                f.put("_aqi", aq.getJSONObject("current").optInt("us_aqi", -1));
            } catch (Exception noAir) { }   // air quality is optional; the forecast is still worth showing
            f.put("_city", loc.optString("city"));
            f.put("_lat", loc.getDouble("lat"));
            f.put("_lon", loc.getDouble("lon"));
            prefs.edit().putString("weather", f.toString()).apply();
            parse(f);
            ok = true;
            main.post(() -> listener.onWeather(this));
        } catch (Exception offline) { }   // keep the cached reading
    }

    private void parse(JSONObject f) throws Exception {
        city = f.optString("_city");
        lat = f.optDouble("_lat", Double.NaN);
        lon = f.optDouble("_lon", Double.NaN);
        JSONObject cur = f.getJSONObject("current");
        temp = (float) cur.getDouble("temperature_2m");
        code = cur.getInt("weather_code");
        day = cur.optInt("is_day", 1) == 1;
        JSONObject d = f.getJSONObject("daily");
        hi = (float) d.getJSONArray("temperature_2m_max").getDouble(0);
        lo = (float) d.getJSONArray("temperature_2m_min").getDouble(0);
        sunrise = d.optJSONArray("sunrise") == null ? "" : d.getJSONArray("sunrise").getString(0).substring(11);
        sunset = d.optJSONArray("sunset") == null ? "" : d.getJSONArray("sunset").getString(0).substring(11);
        aqi = f.optInt("_aqi", -1);
        JSONArray t = f.getJSONObject("hourly").getJSONArray("temperature_2m");
        String now = cur.getString("time"); // local ISO, e.g. 2026-11-17T16:00
        startHour = Integer.parseInt(now.substring(11, 13));
        int base = startHour; // hourly array starts at 00:00 today
        float[] h = new float[12];
        for (int i = 0; i < h.length; i++) h[i] = (float) t.optDouble(Math.min(base + i, t.length() - 1), temp);
        hourly = h;
    }

    /** US, Liberia and Myanmar read temperatures in °F; everywhere else °C. */
    private static boolean fahrenheit() {
        String c = Locale.getDefault().getCountry();
        return c.equals("US") || c.equals("LR") || c.equals("MM");
    }

    // ---- air quality (US AQI), in six steps from good to hazardous
    static final String[] AIR_NAME = {"Good", "Moderate", "Poor", "Unhealthy", "Very unhealthy", "Hazardous"};
    static final String[] AIR_NOTE = {"A good day for open windows", "Fine for most people", "Hard on sensitive lungs", "Keep the windows shut", "Stay indoors if you can", "Stay indoors"};
    static int airLevel(int aqi) { return aqi <= 50 ? 0 : aqi <= 100 ? 1 : aqi <= 150 ? 2 : aqi <= 200 ? 3 : aqi <= 300 ? 4 : 5; }

    // Test overrides, set only from adb (see Pane.handleTestExtras).
    static int testAqi = Integer.MIN_VALUE;
    static boolean testOffline;

    /** True once there is a reading to show. */
    boolean ready() { return ok && !testOffline; }

    /** The air index, or -1 when there is none. */
    int air() { return !ready() ? -1 : testAqi != Integer.MIN_VALUE ? testAqi : aqi; }

    /** One line for the screensaver: "29°  Clear night  ·  Unhealthy air". Empty before the first reading. */
    String summary() {
        if (!ready()) return "";
        return Math.round(temp) + "°  " + describe() + (air() > 150 ? "  ·  " + AIR_NAME[airLevel(air())] + " air" : "");
    }

    String describe() {
        switch (code) {
            case 0: return day ? "Clear sky" : "Clear night";
            case 1: return day ? "Mostly sunny" : "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: return "Showers";
            case 82: return "Violent showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Storm with hail";
            default: return "";
        }
    }

    private static String get(String u) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { c.disconnect(); }
    }
}
