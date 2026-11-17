package tv.nova;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Settings: one glass list on the left — each row says in words what OK will do — and a few
 * facts about this TV on the right, the only place the interface speaks in mono.
 */
final class SettingsPage extends Page {
    private static final float LIST_WIDTH = 440, ROW_HEIGHT = 50, INSET = 6;
    private static final int[] SCREENSAVER_MINUTES = {1, 3, 5, 10, 20};
    static final int DEFAULT_SCREENSAVER_MINUTES = 3;

    private interface Value { String now(); }

    private final List<Row> rows = new ArrayList<>();
    private int selected;

    SettingsPage(Nova host) {
        super(host);
        header("Settings");
        rows.add(new Row("Fire TV settings", null, () -> {
            for (String target : new String[]{"com.amazon.tv.launcher/.ui.MainSettingsActivity", "com.amazon.tv.launcher/.util.QuickSettingsProxyActivity"}) {
                try { host.startActivity(new Intent().setComponent(ComponentName.unflattenFromString(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                catch (RuntimeException tryNext) { }   // the activity differs between Fire OS versions
            }
        }));
        rows.add(new Row("Wi-Fi", null, open("android.settings.PICK_WIFI_NETWORK")));
        rows.add(new Row("Manage apps", null, open("android.settings.APPLICATION_SETTINGS")));
        rows.add(new Row("About this TV", null, open("android.settings.DEVICE_INFO_SETTINGS")));
        rows.add(new Row("Screensaver after", () -> {
            int m = host.prefs.getInt("ambientMin", DEFAULT_SCREENSAVER_MINUTES);
            return m + (m == 1 ? " minute" : " minutes");
        }, () -> {
            int current = host.prefs.getInt("ambientMin", DEFAULT_SCREENSAVER_MINUTES), next = SCREENSAVER_MINUTES[0];
            for (int i = 0; i < SCREENSAVER_MINUTES.length; i++) if (SCREENSAVER_MINUTES[i] == current) next = SCREENSAVER_MINUTES[(i + 1) % SCREENSAVER_MINUTES.length];
            host.prefs.edit().putInt("ambientMin", next).apply();
        }));
        rows.add(new Row("Motion", () -> Motion.reduced ? "Reduced" : "Full", () -> {
            Motion.reduced = !Motion.reduced;
            host.prefs.edit().putBoolean("reducedMotion", Motion.reduced).apply();
        }));

        GlassPanel list = ui.panel(view, Ui.MARGIN, Ui.CONTENT_TOP, LIST_WIDTH, rows.size() * ROW_HEIGHT + 2 * INSET);
        for (int i = 0; i < rows.size(); i++) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, ui.px(ROW_HEIGHT - 6));
            lp.setMargins(ui.px(INSET), ui.px(INSET + 3 + i * ROW_HEIGHT), ui.px(INSET), 0);
            list.addView(rows.get(i).view, lp);
        }
        ui.place(view, facts(), Ui.MARGIN + LIST_WIDTH + 56, Ui.CONTENT_TOP + INSET + 16);
    }

    private Runnable open(final String action) {
        return () -> {
            try { host.startActivity(new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); }
            catch (RuntimeException absent) { }   // Fire OS does not ship every stock settings screen
        };
    }

    private LinearLayout facts() {
        LinearLayout block = new LinearLayout(host);
        block.setOrientation(LinearLayout.VERTICAL);
        TextView title = ui.text(ui.inter, 14, Ui.MIST);
        title.setText("This TV");
        block.addView(title);
        String address = "–";
        int ip = ((WifiManager) host.getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getIpAddress();
        if (ip != 0) address = (ip & 255) + "." + ((ip >> 8) & 255) + "." + ((ip >> 16) & 255) + "." + ((ip >> 24) & 255);
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        ((ActivityManager) host.getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(memory);
        String[][] facts = {
                {"Model", Build.MANUFACTURER + " " + Build.MODEL},
                {"System", "Android " + Build.VERSION.RELEASE},
                {"Address", address},
                {"Storage", String.format(Locale.US, "%.1f GB free", new StatFs(Environment.getDataDirectory().getPath()).getAvailableBytes() / 1e9)},
                {"Memory", (memory.availMem >> 20) + " MB free"},
                {"Apps", String.valueOf(host.apps.all.size())}};
        for (int i = 0; i < facts.length; i++) {
            LinearLayout line = new LinearLayout(host);
            TextView key = ui.text(ui.mono, 13, Ui.MIST), value = ui.text(ui.mono, 13, Ui.PORCELAIN);
            key.setText(facts[i][0]);
            value.setText(facts[i][1]);
            line.addView(key, new LinearLayout.LayoutParams(ui.px(92), -2));
            line.addView(value);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-2, -2);
            ll.topMargin = ui.px(i == 0 ? 16 : 10);
            block.addView(line, ll);
        }
        return block;
    }

    @Override void focus(boolean holdsFocus, boolean animate) {
        for (int i = 0; i < rows.size(); i++) rows.get(i).show(holdsFocus && i == selected);
    }

    @Override boolean key(int k) {
        if (k == KeyEvent.KEYCODE_DPAD_UP) selected = Math.max(0, selected - 1);
        else if (k == KeyEvent.KEYCODE_DPAD_DOWN) { if (selected == rows.size() - 1) return false; selected++; }
        else if (k == KeyEvent.KEYCODE_DPAD_CENTER) {
            Row row = rows.get(selected);
            if (row.value != null || !host.opensNothing()) row.action.run();
        } else return false;
        return true;
    }

    /** One line of the list. Focus turns the whole line to Porcelain. */
    private final class Row {
        final LinearLayout view = new LinearLayout(host);
        final Value value;
        final Runnable action;
        private final TextView label = ui.text(ui.interMedium, 16, Ui.PORCELAIN), state = ui.text(ui.inter, 15, Ui.MIST);

        /** value is null for a row that opens another screen. */
        Row(String name, Value value, Runnable action) {
            this.value = value;
            this.action = action;
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setPadding(ui.px(20), 0, ui.px(20), 0);
            GradientDrawable capsule = new GradientDrawable();
            capsule.setCornerRadius(ui.px(22));
            view.setBackground(capsule);
            label.setText(name);
            view.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
            view.addView(state);
        }

        void show(boolean focused) {
            state.setText(value == null ? "Open" : value.now());
            ((GradientDrawable) view.getBackground()).setColor(focused ? Ui.PORCELAIN : 0);
            label.setTextColor(focused ? Ui.OBSIDIAN : Ui.PORCELAIN);
            state.setTextColor(focused ? Ui.GRAPHITE : Ui.MIST);
        }
    }
}
