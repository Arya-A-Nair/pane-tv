package tv.nova;

import android.graphics.drawable.GradientDrawable;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home: a row of the family's apps, and above it a hero for whichever one is focused — its
 * mark set large with one line of real context — beside two glass widgets with the weather,
 * the air and tonight's sky. Focus starts on the app last watched, so OK continues it.
 */
final class HomePage extends Page {
    private static final float ROW_TOP = 300, DISC = 92, PITCH = 121;

    private final TextView time, date, heroName, heroLine, temperature, condition, city, air, moonName, sun;
    private final ImageView heroMark, moon;
    private final View airDot;
    private final LinearLayout hero, row;
    private final GlassPanel weatherWidget, skyWidget;
    private final List<AppTile> tiles = new ArrayList<>();
    private Apps.App lastWatched, heroApp;
    private boolean heroFilled;
    private int selected;

    HomePage(Nova host) {
        super(host);
        LinearLayout clock = new LinearLayout(host);
        clock.setGravity(Gravity.BOTTOM);
        time = ui.text(ui.manrope, 34, Ui.PORCELAIN);
        date = ui.text(ui.interMedium, 15, Ui.MIST);
        date.setPadding(ui.px(14), 0, 0, ui.px(4));
        ui.shadow(time);
        ui.shadow(date);
        clock.addView(time);
        clock.addView(date);
        ui.place(view, clock, Ui.MARGIN - 1, Ui.TOP - 7);

        hero = new LinearLayout(host);
        hero.setOrientation(LinearLayout.VERTICAL);
        FrameLayout mark = new FrameLayout(host);
        heroMark = new ImageView(host);
        heroMark.setScaleType(ImageView.ScaleType.FIT_START);
        mark.addView(heroMark, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM));
        heroName = ui.text(ui.manrope, 72, Ui.PORCELAIN);
        heroName.setLetterSpacing(-0.02f);
        heroName.setShadowLayer(ui.px(14), 0, ui.px(2), 0x55000000);
        mark.addView(heroName, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM));
        hero.addView(mark, new LinearLayout.LayoutParams(-2, ui.px(88)));
        heroLine = ui.text(ui.inter, 16, Ui.MIST);
        ui.shadow(heroLine);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(-2, -2);
        hl.topMargin = ui.px(14);
        hero.addView(heroLine, hl);
        ui.place(view, hero, Ui.MARGIN - 3, 128);

        float w = 280, x = Ui.W - Ui.MARGIN - w;
        weatherWidget = ui.panel(view, x, Ui.TOP, w, 116);
        temperature = ui.text(ui.manrope, 46, Ui.PORCELAIN);
        ui.place(weatherWidget, temperature, 22, 14);
        condition = ui.text(ui.interMedium, 16, Ui.PORCELAIN);
        city = ui.text(ui.inter, 14, Ui.MIST);
        ui.place(weatherWidget, twoLines(condition, city), 104, 22);
        LinearLayout airLine = new LinearLayout(host);
        airLine.setGravity(Gravity.CENTER_VERTICAL);
        airDot = ui.dot(Ui.GLACIER);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ui.px(9), ui.px(9));
        dl.rightMargin = ui.px(9);
        airLine.addView(airDot, dl);
        air = ui.text(ui.inter, 14, Ui.PORCELAIN);
        airLine.addView(air);
        ui.place(weatherWidget, airLine, 24, 80);

        skyWidget = ui.panel(view, x, Ui.TOP + 116 + 12, w, 76);
        moon = new ImageView(host);
        FrameLayout.LayoutParams ml = new FrameLayout.LayoutParams(ui.px(44), ui.px(44), Gravity.CENTER_VERTICAL);
        ml.leftMargin = ui.px(20);
        skyWidget.addView(moon, ml);
        moonName = ui.text(ui.interMedium, 16, Ui.PORCELAIN);
        sun = ui.text(ui.inter, 14, Ui.MIST);
        FrameLayout.LayoutParams sl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL);
        sl.leftMargin = ui.px(80);
        skyWidget.addView(twoLines(moonName, sun), sl);

        row = new LinearLayout(host);
        row.setClipChildren(false);
        // +3: the focus ring of the first tile then stays inside the safe area
        ui.place(view, row, Ui.MARGIN + 3 - AppTile.RING_ROOM, ROW_TOP - AppTile.RING_ROOM);
        fillRow();
        focusLastWatched();
        refresh();
    }

    private LinearLayout twoLines(TextView first, TextView second) {
        LinearLayout l = new LinearLayout(host);
        l.setOrientation(LinearLayout.VERTICAL);
        second.setPadding(0, ui.px(3), 0, 0);
        l.addView(first);
        l.addView(second);
        return l;
    }

    /** The favourites, with the app last watched put first if it is not one of them, then "All apps". */
    void fillRow() {
        row.removeAllViews();
        tiles.clear();
        List<Apps.App> favourites = host.apps.favourites();
        List<Apps.App> shown = new ArrayList<>(favourites.subList(0, Math.min(Apps.HOME_SLOTS, favourites.size())));
        List<Apps.App> recent = host.apps.recents(1);
        lastWatched = recent.isEmpty() ? null : recent.get(0);
        if (lastWatched != null && !shown.contains(lastWatched)) {
            if (shown.size() == Apps.HOME_SLOTS) shown.remove(shown.size() - 1);
            shown.add(0, lastWatched);
        }
        for (int i = 0; i <= shown.size(); i++) {
            AppTile t = new AppTile(ui, i < shown.size() ? shown.get(i) : null, DISC, PITCH);
            row.addView(t.view);
            tiles.add(t);
        }
        selected = Math.min(selected, tiles.size() - 1);
        heroFilled = false;
        host.apps.warmMarks(shown);
    }

    void focusLastWatched() {
        selected = 0;
        for (int i = 0; i < tiles.size(); i++) if (lastWatched != null && tiles.get(i).app == lastWatched) selected = i;
    }

    void select(int index) { selected = Math.max(0, Math.min(tiles.size() - 1, index)); }

    /** The colour to light the room with: the focused app's, or none for "All apps". */
    Integer accent() { Apps.App a = tiles.get(selected).app; return a == null ? null : a.accent; }

    /** The views that rise into place, in order, when Home first appears. */
    View[] entrance() { return new View[]{(View) time.getParent(), hero, weatherWidget, skyWidget, row}; }

    @Override void focus(boolean holdsFocus, boolean animate) {
        for (int i = 0; i < tiles.size(); i++) tiles.get(i).focus(holdsFocus && i == selected, animate);
        showHero(animate);
    }

    @Override boolean key(int k) {
        if (k == KeyEvent.KEYCODE_DPAD_LEFT) selected = Math.max(0, selected - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_RIGHT) selected = Math.min(tiles.size() - 1, selected + 1);
        else if (k == KeyEvent.KEYCODE_DPAD_CENTER) {
            AppTile t = tiles.get(selected);
            if (t.app == null) host.open(Nova.APPS);
            else host.launch(t);
        } else return false;
        return true;
    }

    private void showHero(boolean animate) {
        final Apps.App a = tiles.get(selected).app;
        if (heroFilled && heroApp == a) return;
        heroFilled = true;
        heroApp = a;
        hero.animate().cancel();
        if (!animate) {
            fillHero(a);
            hero.setAlpha(1f);
            hero.setTranslationX(0);
            return;
        }
        hero.animate().alpha(0f).setDuration(Motion.ms(110)).withEndAction(() -> {
            fillHero(a);
            hero.setTranslationX(ui.px(10));
            hero.animate().alpha(1f).translationX(0).setDuration(Motion.ms(380)).setInterpolator(Motion.GLIDE).start();
        }).start();
    }

    private void fillHero(Apps.App a) {
        // The app's own mark, as large as the box allows but never enlarged past its own pixels. Most TV
        // banners are too small for that to be a hero, and then the name is set in type instead.
        if (a != null) host.apps.loadMark(a);   // already warm unless focus got here within a second of starting
        float scale = a == null || a.logo == null ? 0 : Math.min(a.logoSharp / a.logo.getWidth(),
                Math.min(ui.px(340) / (float) a.logo.getWidth(), ui.px(88) / (float) a.logo.getHeight()));
        boolean mark = scale > 0 && (a.logo.getWidth() * scale >= ui.px(220) || a.logo.getHeight() * scale >= ui.px(64));
        heroMark.setVisibility(mark ? View.VISIBLE : View.GONE);
        heroName.setVisibility(mark ? View.GONE : View.VISIBLE);
        if (mark) {
            heroMark.setImageBitmap(a.logo);
            heroMark.setLayoutParams(new FrameLayout.LayoutParams(Math.round(a.logo.getWidth() * scale), Math.round(a.logo.getHeight() * scale), Gravity.BOTTOM));
        } else heroName.setText(a == null ? "All apps" : a.label);

        if (a == null) { heroLine.setText(host.apps.all.size() + " apps on this TV"); return; }
        long last = host.apps.lastOpened(a), now = System.currentTimeMillis();
        if (last <= 0) { heroLine.setText("Press OK to open"); return; }
        String opened = now - last < 60_000 ? "Opened just now"
                : "Opened " + DateUtils.getRelativeTimeSpanString(last, now, DateUtils.MINUTE_IN_MILLIS).toString().toLowerCase(Locale.getDefault());
        heroLine.setText((mark ? a.label + "  ·  " : "") + (a == lastWatched ? "Continue  ·  " : "") + opened);
    }

    @Override void refresh() {
        time.setText(Ui.time());
        date.setText(Ui.date());
        Weather w = host.weather;
        boolean ready = w != null && w.ready();
        int aqi = ready ? w.air() : -1;
        temperature.setText(ready ? Math.round(w.temp) + "°" : "–");
        condition.setText(ready ? w.describe() : "No reading yet");
        city.setText(ready ? w.city : "Waiting for the network");
        airDot.setVisibility(aqi < 0 ? View.GONE : View.VISIBLE);
        if (aqi >= 0) ((GradientDrawable) airDot.getBackground()).setColor(Ui.AIR[Weather.airLevel(aqi)]);
        air.setText(aqi < 0 ? "" : "Air " + aqi + "  ·  " + Weather.AIR_NAME[Weather.airLevel(aqi)]);
        double age = Moon.age(System.currentTimeMillis());
        moon.setImageBitmap(Moon.render(host, ui.px(44), age));
        moonName.setText(Moon.name(age));
        sun.setText(!ready ? "" : SunArc.isNight(w.sunrise, w.sunset) ? "Sunrise " + w.sunrise : "Sunset " + w.sunset);
    }
}
